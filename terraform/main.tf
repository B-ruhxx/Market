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

# 3. Subred Privada (Backend / Base de datos)
resource "google_compute_subnetwork" "private_subnet" {
  name          = "electronix-private-subnet"
  ip_cidr_range = "10.0.2.0/24"
  region        = var.gcp_region
  network       = google_compute_network.electronix_vpc.id
}

# 4. Reglas de Firewall (Seguridad Perimetral)
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

# 6. Máquina Virtual Compute Engine (Semana 8)
resource "google_compute_instance" "electronix_vm" {
  name         = "electronix-prod-vm"
  machine_type = var.machine_type
  zone         = var.gcp_zone

  tags = ["web-server"]

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
  versioning {
    enabled = true
  }
}
