#!/usr/bin/env bash
# ==============================================================================
# Respaldo automático en Linux (VM GCP/AWS) para PostgreSQL + uploads + GCS
# Unidad 1 (S3), Unidad 3 (S15 - RPO/RTO y recuperación ante desastres)
# ==============================================================================
set -euo pipefail
umask 077

BACKUP_ROOT="${BACKUP_ROOT:-$HOME/electronix-backups}"
KEEP_DAYS="${KEEP_DAYS:-30}"
GCS_BACKUP_BUCKET="${GCS_BACKUP_BUCKET:-}"

if ! [[ "$KEEP_DAYS" =~ ^[0-9]+$ ]]; then
    echo "[ERROR] KEEP_DAYS debe ser un entero no negativo: '$KEEP_DAYS'" >&2
    exit 1
fi

detectar_contenedor() {
    for candidato in "$@"; do
        if docker ps --format '{{.Names}}' | grep -qx "$candidato"; then
            echo "$candidato"
            return 0
        fi
    done
    return 1
}

DB_CONTAINER="${DB_CONTAINER:-$(detectar_contenedor electronix-postgres-prod electronix-postgres || true)}"
CLOUD_SQL_INSTANCE="${CLOUD_SQL_INSTANCE:-}"
mapfile -t APP_CONTAINERS < <(docker ps --filter "label=com.docker.compose.service=ecommerce-app" --format '{{.Names}}')
if [ "${#APP_CONTAINERS[@]}" -eq 0 ]; then
    FALLBACK_APP="$(detectar_contenedor electronix-app || true)"
    [ -n "$FALLBACK_APP" ] && APP_CONTAINERS=("$FALLBACK_APP")
fi

if [ -z "$DB_CONTAINER" ]; then
    if [ -n "$CLOUD_SQL_INSTANCE" ] && command -v gcloud >/dev/null 2>&1; then
        echo "[1/1] Ejecutando respaldo gestionado en Cloud SQL ($CLOUD_SQL_INSTANCE)..."
        gcloud sql backups create --instance="$CLOUD_SQL_INSTANCE" --description="Respaldo manual Electronix $(date +%Y%m%d-%H%M%S)"
        echo "[OK] Respaldo bajo demanda creado en Cloud SQL."
        exit 0
    fi
    echo "[ERROR] No se encontró contenedor PostgreSQL activo ni CLOUD_SQL_INSTANCE configurado." >&2
    exit 1
fi

TIMESTAMP="$(date +%Y%m%d-%H%M%S)"
BACKUP_DIR="$BACKUP_ROOT/$TIMESTAMP"
DUMP_FILE="$BACKUP_DIR/electronix-$TIMESTAMP.dump"
DUMP_TMP="$DUMP_FILE.tmp"
mkdir -p "$BACKUP_DIR"

limpiar_tmp() {
    rm -f "$DUMP_TMP"
}
trap limpiar_tmp EXIT

POSTGRES_DB="$(docker exec "$DB_CONTAINER" printenv POSTGRES_DB | tr -d '\r\n')"
POSTGRES_USER="$(docker exec "$DB_CONTAINER" printenv POSTGRES_USER | tr -d '\r\n')"

echo "[1/4] Generando dump binario por streaming desde $DB_CONTAINER ($POSTGRES_DB)..."
docker exec "$DB_CONTAINER" pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc > "$DUMP_TMP"

echo "[2/4] Verificando integridad del archivo dump..."
if ! docker exec -i "$DB_CONTAINER" pg_restore -l < "$DUMP_TMP" >/dev/null; then
    echo "[ERROR] El dump generado falló la validación pg_restore -l." >&2
    exit 1
fi
mv "$DUMP_TMP" "$DUMP_FILE"

HAS_UPLOADS=false
if [ "${#APP_CONTAINERS[@]}" -gt 0 ]; then
    FIRST_APP="${APP_CONTAINERS[0]}"
    if docker exec "$FIRST_APP" test -d /app/uploads 2>/dev/null; then
        echo "[3/4] Copiando /app/uploads desde $FIRST_APP..."
        mkdir -p "$BACKUP_DIR/uploads"
        docker cp "$FIRST_APP:/app/uploads/." "$BACKUP_DIR/uploads/"
        HAS_UPLOADS=true
    fi
fi
if [ "$HAS_UPLOADS" = false ]; then
    echo "[3/4] Sin carpeta local /app/uploads o almacenamiento en GCS activo."
fi

python3 -c '
import json, sys
print(json.dumps({
    "timestamp": sys.argv[1],
    "db_container": sys.argv[2],
    "database": sys.argv[3],
    "has_uploads": sys.argv[4] == "true",
}, indent=2))
' "$TIMESTAMP" "$DB_CONTAINER" "$POSTGRES_DB" "$HAS_UPLOADS" > "$BACKUP_DIR/metadata.json"

if [ -n "$GCS_BACKUP_BUCKET" ]; then
    echo "Sincronizando respaldo hacia Google Cloud Storage ($GCS_BACKUP_BUCKET)..."
    if command -v gcloud >/dev/null 2>&1; then
        gcloud storage cp -r "$BACKUP_DIR" "gs://${GCS_BACKUP_BUCKET#gs://}/backups/"
    elif command -v gsutil >/dev/null 2>&1; then
        gsutil -m cp -r "$BACKUP_DIR" "gs://${GCS_BACKUP_BUCKET#gs://}/backups/"
    else
        echo "[AVISO] Ni gcloud ni gsutil están instalados; respaldo guardado solo localmente." >&2
    fi
fi

echo "[4/4] Aplicando retención local (>${KEEP_DAYS} días)..."
find "$BACKUP_ROOT" -mindepth 1 -maxdepth 1 -type d -mtime "+$KEEP_DAYS" -exec rm -rf {} +

echo "[OK] Respaldo verificado y completado en: $BACKUP_DIR"
