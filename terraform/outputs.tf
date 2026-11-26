output "instance_public_ip" {
  description = "Dirección IPv4 pública asignada a la instancia de producción"
  value       = google_compute_address.static_ip.address
}

output "storage_bucket_url" {
  description = "URL del bucket público de Cloud Storage para multimedia (no usar para respaldos privados)"
  value       = google_storage_bucket.uploads_bucket.url
}

output "storage_bucket_name" {
  description = "Nombre del bucket para configurar GCP_STORAGE_BUCKET"
  value       = google_storage_bucket.uploads_bucket.name
}

output "backend_service_account_email" {
  description = "Service Account del backend con permisos de escritura en Cloud Storage"
  value       = google_service_account.backend_sa.email
}

output "vpc_network_name" {
  description = "Nombre de la Red Virtual Privada (VPC)"
  value       = google_compute_network.electronix_vpc.name
}

output "public_subnet_cidr" {
  description = "Rango CIDR de la subred pública"
  value       = google_compute_subnetwork.public_subnet.ip_cidr_range
}

output "private_subnet_cidr" {
  description = "Rango CIDR de la subred privada"
  value       = google_compute_subnetwork.private_subnet.ip_cidr_range
}

output "cloud_nat_name" {
  description = "Nombre del Cloud NAT que da salida controlada a la subred privada"
  value       = google_compute_router_nat.electronix_nat.name
}

output "cloud_sql_connection_name" {
  description = "Connection name de Cloud SQL (si enable_cloud_sql=true)"
  value       = try(google_sql_database_instance.postgres_cloudsql[0].connection_name, null)
}

output "cloud_sql_private_ip" {
  description = "IP privada de Cloud SQL dentro de la VPC (si enable_cloud_sql=true)"
  value       = try(google_sql_database_instance.postgres_cloudsql[0].private_ip_address, null)
}
