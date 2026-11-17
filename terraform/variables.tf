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
