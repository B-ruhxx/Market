variable "gcp_project_id" {
  description = "ID del proyecto en Google Cloud Platform"
  type        = string
  default     = "utp-cloud-electronix-2026"
}

variable "gcp_region" {
  description = "Región de GCP para el despliegue"
  type        = string
  default     = "us-central1"
}

variable "gcp_zone" {
  description = "Zona de GCP"
  type        = string
  default     = "us-central1-a"
}

variable "machine_type" {
  description = "Tipo de máquina virtual para Compute Engine"
  type        = string
  default     = "e2-medium"
}

variable "bucket_cors_origins" {
  description = "Orígenes explícitos permitidos por CORS para cargar imágenes públicas desde el navegador (incluye Fabric.js en Estudio). Añade aquí tu dominio o URL de túnel en lugar de usar '*'."
  type        = list(string)
  default     = ["https://electronix.tudominio.com"]
}

variable "enable_cloud_sql" {
  description = "Habilita Cloud SQL PostgreSQL 16 en red privada VPC (alineado al TCO). Pon false para usar PostgreSQL en contenedor dentro de la VM."
  type        = bool
  default     = true
}

variable "cloud_sql_tier" {
  description = "Tipo de máquina para Cloud SQL PostgreSQL"
  type        = string
  default     = "db-f1-micro"
}

variable "db_name" {
  description = "Nombre de la base de datos en Cloud SQL"
  type        = string
  default     = "ecommerce"
}

variable "db_user" {
  description = "Usuario de aplicación para Cloud SQL"
  type        = string
  default     = "ecommerce_user"
}

variable "db_password" {
  description = "Contraseña para el usuario de Cloud SQL (>= 12 caracteres, pásala mediante TF_VAR_db_password cuando enable_cloud_sql=true)"
  type        = string
  sensitive   = true
  default     = null
}

variable "cloud_sql_deletion_protection" {
  description = "Protege la instancia de Cloud SQL frente a borrados accidentales en producción"
  type        = bool
  default     = true
}
