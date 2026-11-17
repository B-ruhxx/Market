# Guía Rápida de Despliegue en la Nube (Google Cloud / AWS)
**Proyecto:** Electronix Perú S.A.C.  
**Criterio Evaluado:** C3 (Despliegue de la solución en la nube - 5 puntos)

Esta guía documenta los pasos reproducibles para desplegar Electronix en una máquina virtual de producción en menos de 10 minutos una vez que tengan acceso a la consola de GCP o AWS.

---

## Opción 1: Despliegue en Google Cloud Platform (Compute Engine)

### Paso 1: Crear la Instancia en GCP (Vía Cloud Shell)
Abre Google Cloud Shell y ejecuta:

```bash
gcloud compute instances create electronix-prod-vm \
    --project="TU_PROJECT_ID" \
    --zone="us-central1-a" \
    --machine-type="e2-medium" \
    --network-interface="network-tier=PREMIUM,subnet=default" \
    --tags="http-server,https-server,web-server" \
    --image-family="ubuntu-2204-lts" \
    --image-project="ubuntu-os-cloud" \
    --boot-disk-size="30GB"
```

Crea las reglas de firewall para permitir tráfico web:
```bash
gcloud compute firewall-rules create allow-http-https \
    --allow=tcp:80,tcp:443 \
    --target-tags=web-server
```

---

### Paso 2: Conectarse por SSH a la VM e Instalar Docker
```bash
gcloud compute ssh electronix-prod-vm --zone="us-central1-a"
```

Dentro de la máquina virtual Ubuntu:
```bash
# Actualizar e instalar Docker Engine y Docker Compose
sudo apt-get update -y
sudo apt-get install -y ca-certificates curl gnupg git
sudo install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg
sudo chmod a+r /etc/apt/keyrings/docker.gpg

echo \
  "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu \
  $(. /etc/os-release && echo "$VERSION_CODENAME") stable" | \
  sudo tee /etc/apt/sources.list.d/docker.list > /dev/null

sudo apt-get update -y
sudo apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
sudo usermod -aG docker $USER
newgrp docker
```

---

### Paso 3: Clonar el Repositorio y Configurar Variables
```bash
git clone https://github.com/TU_USUARIO/TU_REPOSITORIO.git /home/$USER/DesarrolloWeb
cd /home/$USER/DesarrolloWeb

# Crear archivo de entorno de producción
cp .env.example .env
nano .env
```
*En el editor `nano`, ajusta contraseñas seguras para `POSTGRES_PASSWORD`, `JWT_SECRET`, y `ADMIN_PASSWORD`.*

---

### Paso 4: Levantar el Entorno de Producción
```bash
# Levantar el stack completo (PostgreSQL + Spring Boot + Nginx Reverse Proxy)
docker compose -f docker-compose.prod.yml up -d --build

# Verificar que los 3 contenedores estén "healthy" (saludables)
docker compose -f docker-compose.prod.yml ps
```

---

### Paso 5: Verificación en Vivo
1. Obtén la IP pública de la VM desde la consola de GCP o con:
   ```bash
   curl -4 ifconfig.me
   ```
2. Abre en tu navegador:
   * `http://TU_IP_PUBLICA/` -> Catálogo de Electronix funcionando a través de Nginx.
   * `http://TU_IP_PUBLICA/actuator/health` -> Debe responder `{"status":"UP"}`.
   * `http://TU_IP_PUBLICA/actuator/prometheus` -> Métricas en vivo de series de tiempo.

---

## Opción 2: Ejecución de las Pruebas de Sustentación (Demo en Vivo)

Una vez desplegado en la VM, corre los scripts preparados en la carpeta `scripts/`:

```bash
# Prueba 1: Escalabilidad y concurrencia (100 peticiones a la API)
./scripts/test-estres.sh http://localhost/api/productos 20 100

# Prueba 2: Falla y autorecuperación (RTO)
./scripts/test-resiliencia-falla.sh
```
Ambos scripts emitirán las métricas en tiempo real en la pantalla para tu exposición.
