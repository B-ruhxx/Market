# Guía de Despliegue en Google Cloud Platform (Camino 1: VPC + VM + Cloud SQL Privado + GCS)
**Proyecto:** Electronix Perú S.A.C.  
**Criterio Evaluado:** C3 (Despliegue de la solución en la nube - 5 puntos) y C4 (Demostración en vivo - 4 puntos)

Esta guía documenta los pasos reproducibles para desplegar Electronix en **Google Cloud Platform** usando **Terraform (IaC)** + **Cloud SQL PostgreSQL 16 (IP privada + SSL)** + **Cloud Storage** + **Docker Compose** (App Spring Boot, Nginx, Redis, Prometheus y Grafana).

---

## Paso 0: Subir tus últimos cambios al repositorio Git
Antes de clonar desde la nube, asegúrate de que tu repositorio remoto en GitHub tenga los últimos archivos validados:

```bash
git status
git add .
git commit -m "chore: alinear infraestructura cloud, monitoreo y seguridad"
git push origin main
```

---

## Paso 1: Habilitar APIs en GCP y Aprovisionar con Terraform

En **Google Cloud Shell** (o en tu equipo con `gcloud` y `terraform` autenticados):

```bash
# 1. Seleccionar proyecto y habilitar las 4 APIs requeridas por Terraform
gcloud config set project TU_PROJECT_ID
gcloud services enable \
  compute.googleapis.com \
  sqladmin.googleapis.com \
  servicenetworking.googleapis.com \
  storage.googleapis.com

# 2. Clonar repo en Cloud Shell (o abrir carpeta terraform/)
git clone https://github.com/TU_USUARIO/TU_REPOSITORIO.git
cd TU_REPOSITORIO/terraform

# 3. Exportar contraseña segura (>= 12 caracteres) para Cloud SQL
export TF_VAR_db_password="CambiaEstaClaveCloudSQL2026!"

# 4. Inicializar y aprovisionar VPC, subredes, Cloud NAT, VM, GCS y Cloud SQL privado
terraform init
terraform apply \
  -var="gcp_project_id=TU_PROJECT_ID"
```

Al terminar, anota las salidas de Terraform:

```bash
terraform output instance_public_ip
terraform output cloud_sql_private_ip
terraform output storage_bucket_name
```

> **CORS del bucket para el Estudio/navegador:** una vez conocida la IP pública o tu dominio HTTPS, actualiza `bucket_cors_origins` ejecutando:
> `terraform apply -var="gcp_project_id=TU_PROJECT_ID" -var='bucket_cors_origins=["http://IP_PUBLICA","https://TU_DOMINIO"]'`

*(Opcional — Camino 2 sin Cloud SQL gestionado: si prefieres PostgreSQL dentro de Docker en la VM, pasa `-var="enable_cloud_sql=false"` y levanta Compose con `--profile local-db`.)*

---

## Paso 2: Conectarse por SSH (vía IAP) a la VM

Terraform ya instaló Docker y Docker Compose mediante `metadata_startup_script`. Conéctate usando Identity-Aware Proxy (no requiere abrir SSH a `0.0.0.0/0`):

```bash
gcloud compute ssh electronix-prod-vm --zone="us-central1-a" --tunnel-through-iap
```

Dentro de la VM, habilita Docker para tu usuario (si recién arrancó la VM, espera ~1 minuto a que termine el startup script o verifica `docker --version`):

```bash
sudo usermod -aG docker $USER
newgrp docker
sudo apt-get update -y && sudo apt-get install -y git
```

---

## Paso 3: Clonar el Repositorio y Configurar `.env`

```bash
git clone https://github.com/TU_USUARIO/TU_REPOSITORIO.git ~/DesarrolloWeb
cd ~/DesarrolloWeb
cp .env.example .env
nano .env
```

Configura estas variables en `.env` (reemplazando con valores reales):

```dotenv
# Base de datos Cloud SQL en red privada VPC (SSL obligatorio)
SPRING_DATASOURCE_URL=jdbc:postgresql://IP_PRIVADA_CLOUD_SQL:5432/ecommerce?sslmode=require
POSTGRES_DB=ecommerce
POSTGRES_USER=ecommerce_user
POSTGRES_PASSWORD=CambiaEstaClaveCloudSQL2026!

# Redis y seguridad JWT / Admin
REDIS_PASSWORD=GeneraClaveRedisFuerte2026!
JWT_SECRET=GeneraUnSecretoAleatorioDeAlMenos32CaracteresParaJWT2026
DEMO_SEED_ENABLED=true
ADMIN_EMAIL=Admin@gmail.com
ADMIN_PASSWORD=CambiaClaveAdmin2026!

# Almacenamiento de imágenes en Google Cloud Storage (usa Service Account de la VM)
APP_STORAGE_TYPE=gcs
GCP_STORAGE_BUCKET=electronix-uploads-TU_PROJECT_ID

# Orígenes permitidos (pon http://IP_PUBLICA o tu URL HTTPS si usas dominio/túnel)
CORS_ORIGINS=http://IP_PUBLICA

# Observabilidad Prometheus + Grafana
PROMETHEUS_SCRAPE_TOKEN=GeneraTokenPrometheusFuerte2026!
GRAFANA_ADMIN_USER=admin
GRAFANA_ADMIN_PASSWORD=CambiaClaveGrafana2026!
```

> **Tip para la demo:** en la primera carga puedes dejar `DEMO_SEED_ENABLED=true` si quieres sembrar los 200 productos y 50 usuarios demo en Cloud SQL automáticamente; luego cámbialo a `false` para producción real.

---

## Paso 4: Levantar el Stack en Producción

```bash
docker compose -f docker-compose.prod.yml up -d --build
docker compose -f docker-compose.prod.yml ps
```

En modo Cloud SQL deben quedar **5 contenedores activos y saludables (`healthy`)**:
`ecommerce-redis`, `ecommerce-app`, `ecommerce-nginx`, `ecommerce-prometheus` y `ecommerce-grafana`.
*(Si elegiste `enable_cloud_sql=false`, usa `docker compose -f docker-compose.prod.yml --profile local-db up -d --build` para levantar también `ecommerce-postgres`.)*

Para demostrar **escalabilidad horizontal** detrás de Nginx (los mensajes STOMP de chat se sincronizan entre réplicas vía Redis Pub/Sub en `ChatPubSubBridge`):

```bash
docker compose -f docker-compose.prod.yml up -d --scale ecommerce-app=2
```

---

## Paso 5: Verificación, HTTPS Opcional y Acceso a Grafana

1. **Verificación HTTP directa:**
   * `http://IP_PUBLICA/` → Tienda Electronix servida por Nginx.
   * `http://IP_PUBLICA/actuator/health` → Responde `{"status":"UP"}`.
   * `http://IP_PUBLICA/actuator/prometheus` → Responde `403` desde internet (solo Prometheus interno consulta `/actuator/prometheus` en la red privada Docker usando `PROMETHEUS_SCRAPE_TOKEN`).
2. **HTTPS rápido sin comprar dominio (opcional, recomendado para APK Android o demo con TLS):**
   Si deseas exponer la VM por `https://...` en segundos, ejecuta en la VM un túnel temporal apuntando a Nginx local (`http://127.0.0.1:80`) y añade esa URL exacta (sin comodines) a `CORS_ORIGINS` en `.env` + `bucket_cors_origins` en Terraform. En un dominio propio, coloca un Load Balancer HTTPS de GCP o Caddy delante del puerto `80`.
3. **Abrir Grafana (`:3000`) y Prometheus (`:9090`) de forma segura:**
   Desde tu laptop abre un túnel SSH por IAP:
   ```bash
   gcloud compute ssh electronix-prod-vm --zone="us-central1-a" --tunnel-through-iap -- -L 3000:127.0.0.1:3000 -L 9090:127.0.0.1:9090
   ```
   Entra a `http://localhost:3000` (panel pre-cargado *Electronix Cloud - Observabilidad y SLA*).

---

## Paso 6: Guion Rápido para la Demo en Vivo (Criterio C4)

1. **Flujo principal en Cloud:** muestra la consola de GCP (VM + Cloud SQL + Bucket GCS), entra como admin, sube una imagen en un producto y muestra que la URL devuelta apunta a `https://storage.googleapis.com/electronix-uploads-...`. Luego realiza una compra como cliente y verifica descuento de stock.
2. **Estrés + Observabilidad en vivo:** abre Grafana (`http://localhost:3000`) y ejecuta en la VM:
   ```bash
   ./scripts/test-estres.sh http://localhost/api/categorias 20 200
   ```
3. **Tolerancia a fallas y autorecuperación (RTO):**
   ```bash
   CATALOG_URL=http://localhost/api/categorias HEALTH_URL=http://localhost/actuator/health ./scripts/test-resiliencia-falla.sh
   ```
   *(Valida primero que la tienda sigue respondiendo `200` sin Redis y luego mide la autorecuperación automática de `ecommerce-app` tras `SIGKILL`).*
4. **Seguridad:** muestra `curl -i http://IP_PUBLICA/actuator/prometheus` (`403 Forbidden`) y el bloqueo tras 5 intentos fallidos de login (`429 Too Many Requests`).

---

## Paso 7: Ahorro de Créditos al Terminar la Exposición

Cloud SQL ya realiza respaldos automáticos y PITR, y puedes tomar un respaldo manual adicional con:
```bash
CLOUD_SQL_INSTANCE=electronix-postgres-cloudsql ./scripts/backup-electronix.sh
```
Cuando termines tus exposiciones, detén la VM e instancia Cloud SQL desde consola (o ejecuta `terraform destroy` dentro de `terraform/`) para conservar el resto de tus \$300 de crédito.

---

## Referencia Rápida: Kubernetes (`k8s/`) y AWS

- **Kubernetes (`k8s/`):** arquitectura de evolución multi-réplica con `HPA`, `PodDisruptionBudget`, `NetworkPolicy` y `kubernetes_sd_configs` en Prometheus (`kubectl apply -f k8s/` tras publicar la imagen en Artifact Registry).
- **AWS equivalente:** VPC con subred pública/privada, EC2 `t3.medium` (Ubuntu 22.04), Security Group (`80`/`443` y `22` restringido), RDS PostgreSQL privado y S3.
