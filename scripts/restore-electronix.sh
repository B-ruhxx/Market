#!/usr/bin/env bash
# ==============================================================================
# Restauración atómica en Linux (VM GCP/AWS) desde backup-electronix.sh
# ==============================================================================
set -euo pipefail
umask 077

if [ $# -lt 1 ]; then
    echo "Uso: $0 /ruta/al/directorio_respaldo" >&2
    exit 1
fi

BACKUP_DIR="$1"
if [ ! -d "$BACKUP_DIR" ]; then
    echo "[ERROR] El directorio de respaldo no existe: $BACKUP_DIR" >&2
    exit 1
fi

DUMP_FILE="$(find "$BACKUP_DIR" -maxdepth 1 -name '*.dump' ! -name 'pre-restore-*.dump' | sort | head -n 1)"
if [ -z "$DUMP_FILE" ]; then
    echo "[ERROR] No se encontró archivo .dump en $BACKUP_DIR" >&2
    exit 1
fi

detectar_contenedor() {
    for candidato in "$@"; do
        if docker ps -a --format '{{.Names}}' | grep -qx "$candidato"; then
            echo "$candidato"
            return 0
        fi
    done
    return 1
}

DB_CONTAINER="${DB_CONTAINER:-$(detectar_contenedor electronix-postgres-prod electronix-postgres || true)}"
mapfile -t APP_CONTAINERS < <(docker ps -a --filter "label=com.docker.compose.service=ecommerce-app" --format '{{.Names}}')
if [ "${#APP_CONTAINERS[@]}" -eq 0 ]; then
    FALLBACK_APP="$(detectar_contenedor electronix-app || true)"
    [ -n "$FALLBACK_APP" ] && APP_CONTAINERS=("$FALLBACK_APP")
fi

if [ -z "$DB_CONTAINER" ]; then
    echo "[ERROR] No se encontró contenedor PostgreSQL." >&2
    exit 1
fi

POSTGRES_DB="$(docker exec "$DB_CONTAINER" printenv POSTGRES_DB | tr -d '\r\n')"
POSTGRES_USER="$(docker exec "$DB_CONTAINER" printenv POSTGRES_USER | tr -d '\r\n')"

echo "[1/5] Verificando integridad del dump antes de tocar la base de datos..."
docker exec -i "$DB_CONTAINER" pg_restore -l < "$DUMP_FILE" >/dev/null

STOPPED_APPS=()
reanudar_apps() {
    if [ "${#STOPPED_APPS[@]}" -gt 0 ]; then
        echo "Reanudando contenedores de aplicación (${STOPPED_APPS[*]})..."
        docker start "${STOPPED_APPS[@]}" >/dev/null || true
    fi
}
trap reanudar_apps EXIT

if [ "${#APP_CONTAINERS[@]}" -gt 0 ]; then
    echo "[2/5] Deteniendo contenedores de aplicación (${APP_CONTAINERS[*]})..."
    for app_c in "${APP_CONTAINERS[@]}"; do
        STOPPED_APPS+=("$app_c")
        docker stop "$app_c" >/dev/null
    done
fi

PRE_RESTORE_DIR="$(dirname "$BACKUP_DIR")"
PRE_RESTORE_DUMP="$PRE_RESTORE_DIR/pre-restore-$(date +%Y%m%d-%H%M%S).dump"
echo "[3/5] Generando snapshot previo de seguridad en $PRE_RESTORE_DUMP..."
docker exec "$DB_CONTAINER" pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc > "$PRE_RESTORE_DUMP"

echo "[4/5] Restaurando base de datos en transacción única (atómica)..."
docker exec -i "$DB_CONTAINER" pg_restore -U "$POSTGRES_USER" -d "$POSTGRES_DB" \
    --single-transaction --clean --if-exists --no-owner --exit-on-error < "$DUMP_FILE"

REDIS_CONTAINER="${REDIS_CONTAINER:-$(detectar_contenedor electronix-redis-prod electronix-redis || true)}"
if [ -n "$REDIS_CONTAINER" ]; then
    REDIS_PW="$(docker exec "$REDIS_CONTAINER" printenv REDIS_PASSWORD 2>/dev/null | tr -d '\r\n' || true)"
    if [ -n "$REDIS_PW" ]; then
        echo "   Limpiando caché Redis tras rollback de base de datos ($REDIS_CONTAINER)..."
        docker exec -e REDISCLI_AUTH="$REDIS_PW" "$REDIS_CONTAINER" redis-cli FLUSHDB >/dev/null || true
    fi
fi

if [ -d "$BACKUP_DIR/uploads" ] && [ "${#APP_CONTAINERS[@]}" -gt 0 ]; then
    FIRST_APP="${APP_CONTAINERS[0]}"
    echo "[5/5] Restaurando archivos en /app/uploads ($FIRST_APP)..."
    docker cp "$BACKUP_DIR/uploads/." "$FIRST_APP:/app/uploads/"
    docker exec -u 0 "$FIRST_APP" chown -R appuser:appgroup /app/uploads 2>/dev/null || true
else
    echo "[5/5] Sin carpeta uploads que restaurar."
fi

echo "[OK] Restauración atómica finalizada."
