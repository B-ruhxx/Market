output "instance_public_ip" {
  description = "Dirección IPv4 pública asignada a la instancia de producción"
  value       = google_compute_address.static_ip.address
}

output "storage_bucket_url" {
  description = "URL del bucket de Cloud Storage para multimedia y backups"
  value       = google_storage_bucket.uploads_bucket.url
}

output "vpc_network_name" {
  description = "Nombre de la Red Virtual Privada (VPC)"
  value       = google_compute_network.electronix_vpc.name
}
