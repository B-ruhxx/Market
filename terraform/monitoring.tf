# Monitoreo administrado en GCP (Cloud Monitoring):
# - Uptime Check cada 60s contra /actuator/health (equivalente administrado al check externo)
# - Alerta ante caída prolongada (> 120s) con notificación por correo opcional

variable "enable_uptime_check" {
  description = "Habilita el Uptime Check y la alerta en Cloud Monitoring"
  type        = bool
  default     = true
}

variable "uptime_check_host" {
  description = "Host o dominio público para el Uptime Check (vacío = usa la IP estática de la VM)"
  type        = string
  default     = ""
}

variable "uptime_check_use_https" {
  description = "Si true, verifica /actuator/health por HTTPS (puerto 443) con validación TLS"
  type        = bool
  default     = false
}

variable "alert_notification_emails" {
  description = "Lista opcional de correos para recibir alertas de caída desde Cloud Monitoring"
  type        = list(string)
  default     = []
}

resource "google_monitoring_notification_channel" "alert_email" {
  for_each     = toset(var.enable_uptime_check ? var.alert_notification_emails : [])
  display_name = "Electronix Alertas (${each.value})"
  type         = "email"

  labels = {
    email_address = each.value
  }
}

resource "google_monitoring_uptime_check_config" "electronix_health_uptime" {
  count        = var.enable_uptime_check ? 1 : 0
  display_name = "Electronix /actuator/health Uptime Check"
  timeout      = "10s"
  period       = "60s"

  http_check {
    path           = "/actuator/health"
    port           = var.uptime_check_use_https ? 443 : 80
    use_ssl        = var.uptime_check_use_https
    validate_ssl   = var.uptime_check_use_https
    request_method = "GET"

    accepted_response_status_codes {
      status_class = "STATUS_CLASS_2XX"
    }
  }

  monitored_resource {
    type = "uptime_url"
    labels = {
      project_id = var.gcp_project_id
      host       = var.uptime_check_host != "" ? var.uptime_check_host : google_compute_address.static_ip.address
    }
  }

  depends_on = [google_compute_instance.electronix_vm]
}

resource "google_monitoring_alert_policy" "electronix_uptime_alert" {
  count        = var.enable_uptime_check ? 1 : 0
  display_name = "Electronix - Alerta de indisponibilidad (/actuator/health)"
  combiner     = "OR"

  notification_channels = [
    for ch in google_monitoring_notification_channel.alert_email : ch.id
  ]

  conditions {
    display_name = "Fallo de Uptime Check desde múltiples ubicaciones"

    condition_threshold {
      filter          = "resource.type = \"uptime_url\" AND metric.type = \"monitoring.googleapis.com/uptime_check/check_passed\" AND metric.labels.check_id = \"${google_monitoring_uptime_check_config.electronix_health_uptime[0].uptime_check_id}\""
      duration        = "120s"
      comparison      = "COMPARISON_GT"
      threshold_value = 1

      aggregations {
        alignment_period     = "120s"
        per_series_aligner   = "ALIGN_NEXT_OLDER"
        cross_series_reducer = "REDUCE_COUNT_FALSE"
        group_by_fields      = ["resource.label.*"]
      }

      trigger {
        count = 1
      }
    }
  }

  documentation {
    content   = "El endpoint /actuator/health de Electronix falló desde más de una ubicación de Cloud Monitoring durante 2 minutos. Verifique contenedores Docker, Nginx y Cloud SQL en la VM."
    mime_type = "text/markdown"
  }
}

output "uptime_check_id" {
  description = "ID del Uptime Check en Cloud Monitoring"
  value       = var.enable_uptime_check ? google_monitoring_uptime_check_config.electronix_health_uptime[0].uptime_check_id : null
}

output "uptime_alert_policy_name" {
  description = "Recurso de la política de alerta en Cloud Monitoring"
  value       = var.enable_uptime_check ? google_monitoring_alert_policy.electronix_uptime_alert[0].name : null
}
