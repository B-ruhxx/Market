terraform {
  required_version = ">= 1.5.0"
  required_providers {
    google = {
      source  = "hashicorp/google"
      version = "~> 5.0"
    }
  }
}

provider "google" {
  project = var.gcp_project_id
  region  = var.gcp_region
  zone    = var.gcp_zone
}

# 1. Red Virtual Personalizada (VPC) - Semana 7
resource "google_compute_network" "electronix_vpc" {
  name                    = "electronix-vpc"
  auto_create_subnetworks = false
}

# 2. Subred Pública (Load Balancer / Ingress)
resource "google_compute_subnetwork" "public_subnet" {
  name          = "electronix-public-subnet"
  ip_cidr_range = "10.0.1.0/24"
  region        = var.gcp_region
  network       = google_compute_network.electronix_vpc.id
}

# 3. Subred Privada (Capa de datos / servicios internos sin IP pública)
resource "google_compute_subnetwork" "private_subnet" {
  name                     = "electronix-private-subnet"
  ip_cidr_range            = "10.0.2.0/24"
  region                   = var.gcp_region
  network                  = google_compute_network.electronix_vpc.id
  private_ip_google_access = true
}

# 3b. Cloud Router y Cloud NAT para salida segura de la subred privada (Semana 7)
resource "google_compute_router" "electronix_router" {
  name    = "electronix-router"
  region  = var.gcp_region
  network = google_compute_network.electronix_vpc.id
}

resource "google_compute_router_nat" "electronix_nat" {
  name                               = "electronix-nat"
  router                             = google_compute_router.electronix_router.name
  region                             = var.gcp_region
  nat_ip_allocate_option             = "AUTO_ONLY"
  source_subnetwork_ip_ranges_to_nat = "LIST_OF_SUBNETWORKS"

  subnetwork {
    name                    = google_compute_subnetwork.private_subnet.id
    source_ip_ranges_to_nat = ["ALL_IP_RANGES"]
  }
}

# 4. Reglas de Firewall (Seguridad Perimetral e Interna VPC)
resource "google_compute_firewall" "allow_internal_vpc" {
  name    = "allow-internal-vpc"
  network = google_compute_network.electronix_vpc.name

  allow {
    protocol = "tcp"
    ports    = ["80", "443", "5432", "6379", "8080", "9090", "3000"]
  }

  source_ranges = ["10.0.1.0/24", "10.0.2.0/24"]
  target_tags   = ["web-server", "internal-data"]
}
resource "google_compute_firewall" "allow_web" {
  name    = "allow-web-traffic"
  network = google_compute_network.electronix_vpc.name

  allow {
    protocol = "tcp"
    ports    = ["80", "443"] # Solo HTTP y HTTPS públicos; 8090 aislado internamente
  }

  source_ranges = ["0.0.0.0/0"]
  target_tags   = ["web-server"]
}

resource "google_compute_firewall" "allow_ssh" {
  name    = "allow-ssh-iap"
  network = google_compute_network.electronix_vpc.name

  allow {
    protocol = "tcp"
    ports    = ["22"]
  }

  # Restringido al rango oficial de Google Cloud Identity-Aware Proxy (IAP) para evitar fuerza bruta pública
  source_ranges = ["35.235.240.0/20"]
  target_tags   = ["web-server"]
}

# 5. IP Externa Estática
resource "google_compute_address" "static_ip" {
  name   = "electronix-ipv4"
  region = var.gcp_region
}

# 6. Service Account y Máquina Virtual Compute Engine (Semana 8)
resource "google_service_account" "backend_sa" {
  account_id   = "electronix-backend-sa"
  display_name = "Electronix Backend Service Account"
}

resource "google_compute_instance" "electronix_vm" {
  name         = "electronix-prod-vm"
  machine_type = var.machine_type
  zone         = var.gcp_zone

  tags = ["web-server"]

  shielded_instance_config {
    enable_secure_boot          = true
    enable_vtpm                 = true
    enable_integrity_monitoring = true
  }

  metadata = {
    enable-oslogin         = "TRUE"
    block-project-ssh-keys = "true"
  }

  boot_disk {
    initialize_params {
      image = "ubuntu-os-cloud/ubuntu-2204-lts"
      size  = 30
      type  = "pd-balanced"
    }
  }

  network_interface {
    network    = google_compute_network.electronix_vpc.name
    subnetwork = google_compute_subnetwork.public_subnet.name

    access_config {
      nat_ip = google_compute_address.static_ip.address
    }
  }

  service_account {
    email = google_service_account.backend_sa.email
    scopes = [
      "https://www.googleapis.com/auth/devstorage.read_write",
      "https://www.googleapis.com/auth/logging.write",
      "https://www.googleapis.com/auth/monitoring.write"
    ]
  }

  metadata_startup_script = <<-EOF
    #!/bin/bash
    apt-get update -y
    apt-get install -y ca-certificates curl gnupg lsb-release
    mkdir -p /etc/apt/keyrings
    curl -fsSL https://download.docker.com/linux/ubuntu/gpg | gpg --dearmor -o /etc/apt/keyrings/docker.gpg
    echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu $(lsb_release -cs) stable" | tee /etc/apt/sources.list.d/docker.list > /dev/null
    apt-get update -y
    apt-get install -y docker-ce docker-ce-cli containerd.io docker-compose-plugin
    systemctl enable docker
    systemctl start docker
  EOF
}

# 7. Bucket de Almacenamiento de Objetos (Cloud Storage) - Semana 3
resource "google_storage_bucket" "uploads_bucket" {
  name          = "electronix-uploads-${var.gcp_project_id}"
  location      = var.gcp_region
  storage_class = "STANDARD"

  uniform_bucket_level_access = true
  public_access_prevention    = "inherited"

  versioning {
    enabled = true
  }

  lifecycle_rule {
    condition {
      num_newer_versions = 3
      with_state         = "ARCHIVED"
    }
    action {
      type = "Delete"
    }
  }

  cors {
    origin          = var.bucket_cors_origins
    method          = ["GET", "HEAD"]
    response_header = ["Content-Type", "Cache-Control"]
    max_age_seconds = 3600
  }
}

# Lectura pública de objetos únicamente (legacyObjectReader otorga storage.objects.get sin storage.objects.list)
resource "google_storage_bucket_iam_member" "uploads_public_read" {
  bucket = google_storage_bucket.uploads_bucket.name
  role   = "roles/storage.legacyObjectReader"
  member = "allUsers"
}

# Permisos mínimos de creación (sin borrado ni sobrescritura) sobre el bucket de uploads para la cuenta del backend
resource "google_storage_bucket_iam_member" "backend_storage_writer" {
  bucket = google_storage_bucket.uploads_bucket.name
  role   = "roles/storage.objectCreator"
  member = "serviceAccount:${google_service_account.backend_sa.email}"
}

# Permisos para telemetría en Cloud Monitoring y Cloud Logging (Semana 10)
resource "google_project_iam_member" "backend_monitoring_writer" {
  project = var.gcp_project_id
  role    = "roles/monitoring.metricWriter"
  member  = "serviceAccount:${google_service_account.backend_sa.email}"
}

resource "google_project_iam_member" "backend_logging_writer" {
  project = var.gcp_project_id
  role    = "roles/logging.logWriter"
  member  = "serviceAccount:${google_service_account.backend_sa.email}"
}

# 8. Private Service Access y Cloud SQL PostgreSQL 16 en red privada (Semana 3 y Semana 7)
resource "google_compute_global_address" "private_ip_alloc" {
  count         = var.enable_cloud_sql ? 1 : 0
  name          = "electronix-private-ip-range"
  purpose       = "VPC_PEERING"
  address_type  = "INTERNAL"
  prefix_length = 16
  network       = google_compute_network.electronix_vpc.id
}

resource "google_service_networking_connection" "private_vpc_connection" {
  count                   = var.enable_cloud_sql ? 1 : 0
  network                 = google_compute_network.electronix_vpc.id
  service                 = "servicenetworking.googleapis.com"
  reserved_peering_ranges = [google_compute_global_address.private_ip_alloc[0].name]
}

resource "google_sql_database_instance" "postgres_cloudsql" {
  count               = var.enable_cloud_sql ? 1 : 0
  name                = "electronix-postgres-cloudsql"
  region              = var.gcp_region
  database_version    = "POSTGRES_16"
  deletion_protection = var.cloud_sql_deletion_protection

  depends_on = [google_service_networking_connection.private_vpc_connection]

  settings {
    tier              = var.cloud_sql_tier
    disk_size         = 10
    disk_type         = "PD_SSD"
    availability_type = "ZONAL"

    ip_configuration {
      ipv4_enabled                                  = false
      private_network                               = google_compute_network.electronix_vpc.id
      enable_private_path_for_google_cloud_services = true
      ssl_mode                                      = "ENCRYPTED_ONLY"
    }

    backup_configuration {
      enabled                        = true
      point_in_time_recovery_enabled = true
      start_time                     = "03:00"
    }
  }
}

resource "google_sql_database" "ecommerce_db" {
  count    = var.enable_cloud_sql ? 1 : 0
  name     = var.db_name
  instance = google_sql_database_instance.postgres_cloudsql[0].name
}

resource "google_sql_user" "ecommerce_user" {
  count    = var.enable_cloud_sql ? 1 : 0
  name     = var.db_user
  instance = google_sql_database_instance.postgres_cloudsql[0].name
  password = var.db_password

  lifecycle {
    precondition {
      condition     = var.db_password != null && length(trimspace(var.db_password)) >= 12
      error_message = "Define TF_VAR_db_password con al menos 12 caracteres cuando enable_cloud_sql=true."
    }
  }
}
