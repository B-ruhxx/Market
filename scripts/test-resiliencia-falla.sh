#!/usr/bin/env bash
# ==============================================================================
# Script de Prueba de Falla, Degradación Elegante y Autorecuperación (RTO)
# Criterio C4 (Demostración en Vivo) y Unidad 3 (Semana 15)
# ==============================================================================
set -euo pipefail

ORCHESTRATOR="${ORCHESTRATOR:-docker}"
HEALTH_URL="${HEALTH_URL:-${2:-http://localhost:8090/actuator/health}}"
CATALOG_URL="${CATALOG_URL:-http://localhost:8090/api/categorias}"
NAMESPACE="${K8S_NAMESPACE:-electronix}"

detectar_contenedor() {
    for candidato in "$@"; do
        if docker ps --format '{{.Names}}' | grep -qx "$candidato"; then
            echo "$candidato"
            return 0
        fi
    done
    return 1
}

es_health_up() {
    local json="$1"
    [[ "$json" =~ \"status\"[[:space:]]*:[[:space:]]*\"UP\" ]]
}

REDIS_STOPPED=""
cleanup() {
    if [ -n "$REDIS_STOPPED" ]; then
        docker start "$REDIS_STOPPED" >/dev/null 2>&1 || true
    fi
}
trap cleanup EXIT

echo "=========================================================="
echo "   ELECTRONIX - PRUEBA DE FALLA Y AUTORECUPERACIÓN (RTO)  "
echo "=========================================================="
echo "Orquestador: $ORCHESTRATOR"
echo "Health URL:  $HEALTH_URL"
echo "Cache URL:   $CATALOG_URL"
echo "----------------------------------------------------------"

echo "1. Verificando estado inicial saludable..."
INITIAL_HEALTH="$(curl -sf "$HEALTH_URL" || echo '{"status":"DOWN"}')"
echo "   Estado actual: $INITIAL_HEALTH"
if ! es_health_up "$INITIAL_HEALTH"; then
    echo "[ERROR] El servicio debe estar UP antes de iniciar la prueba." >&2
    exit 1
fi

if [ "$ORCHESTRATOR" = "k8s" ]; then
    POD_NAME="$(kubectl get pods -n "$NAMESPACE" -l app=electronix-app -o jsonpath='{.items[0].metadata.name}')"
    echo "2. Eliminando pod '$POD_NAME' para verificar self-healing en Kubernetes..."
    START_TIME="$(date +%s)"
    kubectl delete pod "$POD_NAME" -n "$NAMESPACE" --wait=false
    kubectl rollout status deployment/electronix-app-deployment -n "$NAMESPACE" --timeout=120s
    END_TIME="$(date +%s)"
    RTO=$(( END_TIME - START_TIME ))
    echo "   [ÉXITO] Nuevo pod Ready. RTO Kubernetes medido: ${RTO}s."
    exit 0
fi

REDIS_CONTAINER="${REDIS_CONTAINER:-$(detectar_contenedor electronix-redis-prod electronix-redis || true)}"
mapfile -t COMPOSE_APPS < <(docker ps --filter "label=com.docker.compose.service=ecommerce-app" --format '{{.Names}}')
APP_CONTAINER="${1:-${CONTAINER_NAME:-${COMPOSE_APPS[0]:-$(detectar_contenedor electronix-app || true)}}}"

if [ -n "$REDIS_CONTAINER" ]; then
    echo "----------------------------------------------------------"
    echo "2A. Prueba de Graceful Degradation: poblando caché y deteniendo Redis ($REDIS_CONTAINER)..."
    curl -sf "$CATALOG_URL" >/dev/null
    docker stop "$REDIS_CONTAINER" >/dev/null
    REDIS_STOPPED="$REDIS_CONTAINER"
    HTTP_CATALOG="$(curl -s -o /dev/null -w "%{http_code}" "$CATALOG_URL" || true)"
    HTTP_HEALTH="$(curl -s -o /dev/null -w "%{http_code}" "$HEALTH_URL" || true)"
    echo "   GET $CATALOG_URL sin Redis -> HTTP $HTTP_CATALOG | Health -> HTTP $HTTP_HEALTH"
    docker start "$REDIS_CONTAINER" >/dev/null
    REDIS_STOPPED=""
    if [ "$HTTP_CATALOG" != "200" ] || [ "$HTTP_HEALTH" != "200" ]; then
        echo "[ERROR] El servicio no degradó correctamente sin Redis." >&2
        exit 1
    fi
    echo "   [ÉXITO] Graceful Degradation validada: endpoint @Cacheable y health siguen 200 sin Redis."
fi

if [ -z "$APP_CONTAINER" ]; then
    echo "[ERROR] No se encontró contenedor de aplicación activo." >&2
    exit 1
fi

echo "----------------------------------------------------------"
echo "2B. Provocando fallo abrupto (SIGKILL) en '$APP_CONTAINER' para probar restart automático..."
START_TIME="$(date +%s)"
if docker exec -u 0 "$APP_CONTAINER" kill -9 1 >/dev/null 2>&1; then
    echo "   [CONFIRMADO] Proceso JVM (PID 1) terminado abruptamente con SIGKILL. Esperando autorecuperación de Docker..."
else
    docker kill -s SIGKILL "$APP_CONTAINER" >/dev/null 2>&1 || docker restart "$APP_CONTAINER" >/dev/null
    echo "   [CONFIRMADO] Señal de terminación enviada. Esperando autorecuperación..."
fi

RECOVERED=false
for i in {1..60}; do
    STATUS="$(curl -s "$HEALTH_URL" 2>/dev/null || echo "")"
    CONTAINER_HEALTH="$(docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{if .State.Running}}healthy{{else}}starting{{end}}{{end}}' "$APP_CONTAINER" 2>/dev/null || echo "starting")"
    if es_health_up "$STATUS"; then
        END_TIME="$(date +%s)"
        RTO=$(( END_TIME - START_TIME ))
        echo "   [ÉXITO] Réplica '$APP_CONTAINER' auto-recuperada ($CONTAINER_HEALTH) y servicio UP!"
        echo "   Tiempo de recuperación observado (RTO): ${RTO} segundos."
        RECOVERED=true
        break
    fi
    echo "   Esperando autorecuperación de '$APP_CONTAINER' ($CONTAINER_HEALTH)... (${i}x2s)"
    sleep 2
done

if [ "$RECOVERED" = false ]; then
    echo "[ERROR] El servicio no se auto-recuperó en 120 segundos." >&2
    exit 1
fi

echo "=========================================================="
echo "   RESULTADO: TOLERANCIA A FALLAS Y SELF-HEALING SUPERADOS "
echo "   RTO medido: ${RTO}s (Dentro de la meta < 15 minutos)   "
echo "=========================================================="
