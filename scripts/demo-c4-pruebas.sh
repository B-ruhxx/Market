#!/usr/bin/env bash
# Suite unificada de demostración C4 / Rúbrica Cloud (P1..P4) para Electronix.
# Uso:
#   ./scripts/demo-c4-pruebas.sh [BASE_URL]
# Ejemplos:
#   ./scripts/demo-c4-pruebas.sh http://localhost:8090
#   RUN_CHAOS=true ./scripts/demo-c4-pruebas.sh https://34.123.45.67.sslip.io
#
# Variables opcionales:
#   CLIENT_EMAIL       (default: Usuario1@gmail.com)
#   CLIENT_PASSWORD    (default: User1)
#   STRESS_CONCURRENT  (default: 50)
#   STRESS_REQUESTS    (default: 500)
#   MAX_LATENCY_SEC    (default: 1.0)
#   RUN_CHAOS          (auto | true | false, default: auto)

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BASE_URL="${1:-http://localhost:8090}"
BASE_URL="${BASE_URL%/}"

CLIENT_EMAIL="${CLIENT_EMAIL:-usuario1@gmail.com}"
CLIENT_PASSWORD="${CLIENT_PASSWORD:-UserPassword123!}"
STRESS_CONCURRENT="${STRESS_CONCURRENT:-50}"
STRESS_REQUESTS="${STRESS_REQUESTS:-500}"
MAX_LATENCY_SEC="${MAX_LATENCY_SEC:-1.0}"
RUN_CHAOS="${RUN_CHAOS:-auto}"

TMP_DIR="$(mktemp -d)"
cleanup() {
    rm -rf "$TMP_DIR"
}
trap cleanup EXIT

PASS_COUNT=0
FAIL_COUNT=0
SKIP_COUNT=0

mark_pass() {
    echo "   [PASS] $1"
    PASS_COUNT=$((PASS_COUNT + 1))
}

mark_fail() {
    echo "   [FAIL] $1" >&2
    FAIL_COUNT=$((FAIL_COUNT + 1))
}

mark_skip() {
    echo "   [SKIP] $1"
    SKIP_COUNT=$((SKIP_COUNT + 1))
}

check_latency_under_limit() {
    local measured="$1"
    local limit="$2"
    python3 -c "import sys; sys.exit(0 if float(sys.argv[1]) <= float(sys.argv[2]) else 1)" "$measured" "$limit"
}

measure_get() {
    local url="$1"
    local out_file="$2"
    local auth_header="${3:-}"
    if [ -n "$auth_header" ]; then
        printf 'header = "Authorization: Bearer %s"\n' "$auth_header" | curl -sS -K - -o "$out_file" -w "%{http_code} %{time_total}\n" "$url"
    else
        curl -sS -o "$out_file" -w "%{http_code} %{time_total}\n" "$url"
    fi
}

echo "================================================================="
echo "  ELECTRONIX - VALIDACIÓN TÉCNICA DE CRITERIOS C4 (P1 - P4)"
echo "  Target: $BASE_URL"
echo "================================================================="
echo ""

# -----------------------------------------------------------------
# P1: Flujo funcional E2E, almacenamiento de imágenes y latencia < 1s
# -----------------------------------------------------------------
echo ">>> P1. Prueba funcional E2E, Storage y latencia (< ${MAX_LATENCY_SEC}s)"

read -r HEALTH_CODE HEALTH_TIME < <(measure_get "$BASE_URL/actuator/health" "$TMP_DIR/health.json")
if [ "$HEALTH_CODE" = "200" ] && grep -q '"UP"' "$TMP_DIR/health.json"; then
    mark_pass "/actuator/health -> HTTP $HEALTH_CODE UP en ${HEALTH_TIME}s"
else
    mark_fail "/actuator/health -> HTTP $HEALTH_CODE (${HEALTH_TIME}s)"
fi

read -r CAT_CODE CAT_TIME < <(measure_get "$BASE_URL/api/productos?page=0&size=8" "$TMP_DIR/productos.json")
PROD_INFO="$(python3 -c '
import json, sys
try:
    data = json.load(open(sys.argv[1], encoding="utf-8"))
    datos = data.get("datos") or {}
    contenido = datos.get("contenido") or []
    total = datos.get("totalElementos") or 0
    img = ""
    for p in contenido:
        if p.get("imagenUrl"):
            img = p["imagenUrl"]
            break
    print(f"{total}\t{len(contenido)}\t{img}")
except Exception:
    print("0\t0\t")
' "$TMP_DIR/productos.json")"
IFS=$'\t' read -r TOTAL_PRODS PAGE_PRODS SAMPLE_IMG <<< "$PROD_INFO"

if [ "$CAT_CODE" = "200" ] && [ "$TOTAL_PRODS" -gt 0 ] && check_latency_under_limit "$CAT_TIME" "$MAX_LATENCY_SEC"; then
    mark_pass "GET /api/productos -> HTTP 200, ${TOTAL_PRODS} productos, ${CAT_TIME}s"
else
    mark_fail "GET /api/productos -> HTTP $CAT_CODE, total=$TOTAL_PRODS, tiempo=${CAT_TIME}s (límite ${MAX_LATENCY_SEC}s)"
fi

if [ -n "$SAMPLE_IMG" ]; then
    if [[ "$SAMPLE_IMG" =~ ^https?:// ]]; then
        IMG_URL="$SAMPLE_IMG"
    else
        IMG_URL="$BASE_URL/${SAMPLE_IMG#/}"
    fi
    IMG_CODE="$(curl -sS -L -o /dev/null -w "%{http_code}" "$IMG_URL" || echo "000")"
    if [ "$IMG_CODE" = "200" ]; then
        mark_pass "Imagen accesible en Storage ($IMG_URL -> HTTP 200)"
    else
        mark_fail "Imagen inaccesible ($IMG_URL -> HTTP $IMG_CODE)"
    fi
else
    mark_skip "Sin imagenUrl en página inicial de productos"
fi

LOGIN_PAYLOAD="$(python3 -c 'import json,sys; print(json.dumps({"email":sys.argv[1],"password":sys.argv[2]}))' "$CLIENT_EMAIL" "$CLIENT_PASSWORD")"
read -r LOGIN_CODE LOGIN_TIME < <(
    curl -sS -o "$TMP_DIR/login.json" -w "%{http_code} %{time_total}\n" \
        -X POST "$BASE_URL/api/auth/login" \
        -H "Content-Type: application/json" \
        -d "$LOGIN_PAYLOAD"
)

if [ "$LOGIN_CODE" != "200" ]; then
    REG_PAYLOAD="$(python3 -c 'import json,sys; print(json.dumps({"nombres":"Usuario","apellidoPaterno":"Demo","tipoDocumento":"DNI","numeroDocumento":"71999901","email":sys.argv[1],"password":sys.argv[2]}))' "$CLIENT_EMAIL" "$CLIENT_PASSWORD")"
    curl -sS -o /dev/null -X POST "$BASE_URL/api/auth/registro" \
        -H "Content-Type: application/json" \
        -d "$REG_PAYLOAD" || true
    read -r LOGIN_CODE LOGIN_TIME < <(
        curl -sS -o "$TMP_DIR/login.json" -w "%{http_code} %{time_total}\n" \
            -X POST "$BASE_URL/api/auth/login" \
            -H "Content-Type: application/json" \
            -d "$LOGIN_PAYLOAD"
    )
fi

JWT_TOKEN=""
USER_ROLE=""
if [ "$LOGIN_CODE" = "200" ]; then
    AUTH_PARSED="$(python3 -c '
import json, sys
d = json.load(open(sys.argv[1], encoding="utf-8")).get("datos") or {}
print((d.get("token") or "") + "\t" + (d.get("rol") or ""))
' "$TMP_DIR/login.json")"
    IFS=$'\t' read -r JWT_TOKEN USER_ROLE <<< "$AUTH_PARSED"
fi

if [ -n "$JWT_TOKEN" ] && check_latency_under_limit "$LOGIN_TIME" "$MAX_LATENCY_SEC"; then
    mark_pass "POST /api/auth/login ($USER_ROLE) -> HTTP 200 en ${LOGIN_TIME}s"
else
    mark_fail "POST /api/auth/login falló -> HTTP $LOGIN_CODE en ${LOGIN_TIME}s"
fi

if [ -n "$JWT_TOKEN" ]; then
    read -r CART_CODE CART_TIME < <(measure_get "$BASE_URL/api/carrito" "$TMP_DIR/carrito.json" "$JWT_TOKEN")
    read -r ORD_CODE ORD_TIME < <(measure_get "$BASE_URL/api/pedidos/mios" "$TMP_DIR/pedidos.json" "$JWT_TOKEN")
    if [ "$CART_CODE" = "200" ] && check_latency_under_limit "$CART_TIME" "$MAX_LATENCY_SEC"; then
        mark_pass "GET /api/carrito autenticado -> HTTP 200 en ${CART_TIME}s"
    else
        mark_fail "GET /api/carrito -> HTTP $CART_CODE en ${CART_TIME}s"
    fi
    if [ "$ORD_CODE" = "200" ] && check_latency_under_limit "$ORD_TIME" "$MAX_LATENCY_SEC"; then
        mark_pass "GET /api/pedidos/mios autenticado -> HTTP 200 en ${ORD_TIME}s"
    else
        mark_fail "GET /api/pedidos/mios -> HTTP $ORD_CODE en ${ORD_TIME}s"
    fi
fi

echo ""

# -----------------------------------------------------------------
# P2: Escalabilidad horizontal y prueba de carga concurrente
# -----------------------------------------------------------------
echo ">>> P2. Prueba de carga y escalabilidad ($STRESS_REQUESTS req / $STRESS_CONCURRENT concurrentes)"
if command -v docker >/dev/null 2>&1; then
    REPLICAS="$(docker ps --filter "name=ecommerce-app" --format "{{.Names}}" 2>/dev/null | wc -l | tr -d ' ')"
    if [ "${REPLICAS:-0}" -gt 0 ]; then
        echo "   Réplicas Docker backend activas en este host: $REPLICAS"
    fi
fi

if "$SCRIPT_DIR/test-estres.sh" "$BASE_URL/api/categorias" "$STRESS_CONCURRENT" "$STRESS_REQUESTS"; then
    mark_pass "Prueba de estrés ($STRESS_REQUESTS/$STRESS_CONCURRENT) completada sin errores HTTP"
else
    mark_fail "Prueba de estrés reportó errores HTTP"
fi

echo ""

# -----------------------------------------------------------------
# P4: Seguridad (Prometheus cerrado, RBAC 403, Rate Limiting 429)
# -----------------------------------------------------------------
echo ">>> P4. Seguridad (perímetro, RBAC 403 y bloqueo fuerza bruta 429)"

PROM_CODE="$(curl -sS -o /dev/null -w "%{http_code}" "$BASE_URL/actuator/prometheus" || echo "000")"
if [ "$PROM_CODE" = "401" ] || [ "$PROM_CODE" = "403" ]; then
    mark_pass "GET /actuator/prometheus sin token bloqueado -> HTTP $PROM_CODE"
else
    mark_fail "GET /actuator/prometheus expuesto sin token -> HTTP $PROM_CODE"
fi

PRIV_NOAUTH_CODE="$(curl -sS -o /dev/null -w "%{http_code}" "$BASE_URL/api/pedidos/mios" || echo "000")"
if [ "$PRIV_NOAUTH_CODE" = "401" ] || [ "$PRIV_NOAUTH_CODE" = "403" ]; then
    mark_pass "GET /api/pedidos/mios sin JWT rechazado -> HTTP $PRIV_NOAUTH_CODE"
else
    mark_fail "GET /api/pedidos/mios sin JWT -> HTTP $PRIV_NOAUTH_CODE"
fi

if [ -n "$JWT_TOKEN" ] && [ "$USER_ROLE" = "CLIENTE" ]; then
    REP_CODE="$(printf 'header = "Authorization: Bearer %s"\n' "$JWT_TOKEN" | curl -sS -K - -o /dev/null -w "%{http_code}" "$BASE_URL/api/reportes/ventas" || echo "000")"
    ADM_CODE="$(printf 'header = "Authorization: Bearer %s"\n' "$JWT_TOKEN" | curl -sS -K - -o /dev/null -w "%{http_code}" "$BASE_URL/api/admin/configuracion/roles" || echo "000")"
    if [ "$REP_CODE" = "403" ] && [ "$ADM_CODE" = "403" ]; then
        mark_pass "RBAC estricto para CLIENTE (/api/reportes/ventas=$REP_CODE, /api/admin/configuracion/roles=$ADM_CODE)"
    else
        mark_fail "RBAC inesperado para CLIENTE (reportes=$REP_CODE, config=$ADM_CODE)"
    fi
else
    mark_skip "Prueba RBAC CLIENTE omitida (rol actual: ${USER_ROLE:-sin sesión})"
fi

PROBE_EMAIL="probe-c4-$(date +%s)-$$@electronix.local"
PROBE_PAYLOAD="$(python3 -c 'import json,sys; print(json.dumps({"email":sys.argv[1],"password":"WrongPassword#123"}))' "$PROBE_EMAIL")"
GOT_401=false
GOT_429=false
for intento in 1 2 3 4 5 6 7; do
    BF_CODE="$(curl -sS -o /dev/null -w "%{http_code}" -X POST "$BASE_URL/api/auth/login" -H "Content-Type: application/json" -d "$PROBE_PAYLOAD" || echo "000")"
    [ "$BF_CODE" = "401" ] && GOT_401=true
    [ "$BF_CODE" = "429" ] && GOT_429=true && break
done

if [ "$GOT_401" = "true" ] && [ "$GOT_429" = "true" ]; then
    mark_pass "Fuerza bruta bloqueada en intento <=7 (401 -> 429 Too Many Requests)"
else
    mark_fail "Lockout de login no activado como se esperaba (GOT_401=$GOT_401, GOT_429=$GOT_429)"
fi

echo ""

# -----------------------------------------------------------------
# P3: Tolerancia a fallas, degradación Redis y autorrecuperación RTO
# -----------------------------------------------------------------
echo ">>> P3. Tolerancia a fallas y autorrecuperación (Redis graceful degradation + SIGKILL RTO)"

SHOULD_RUN_CHAOS=false
if [ "$RUN_CHAOS" = "true" ]; then
    SHOULD_RUN_CHAOS=true
elif [ "$RUN_CHAOS" = "auto" ]; then
    if [[ "$BASE_URL" =~ ^https?://(localhost|127\.0\.0\.1)(:[0-9]+)?$ ]] && command -v docker >/dev/null 2>&1; then
        if docker ps --format "{{.Names}}" 2>/dev/null | grep -Eq "electronix-app|ecommerce-app"; then
            SHOULD_RUN_CHAOS=true
        fi
    fi
fi

if [ "$SHOULD_RUN_CHAOS" = "true" ]; then
    if HEALTH_URL="$BASE_URL/actuator/health" CATALOG_URL="$BASE_URL/api/categorias" "$SCRIPT_DIR/test-resiliencia-falla.sh"; then
        mark_pass "Resiliencia Redis + autorrecuperación tras SIGKILL verificadas"
    else
        mark_fail "Prueba de resiliencia falló"
    fi
else
    mark_skip "Chaos P3 omitido (ejecuta con RUN_CHAOS=true dentro de la VM de GCP o contra entorno local)"
fi

echo ""
echo "================================================================="
echo "  RESUMEN C4: PASS=$PASS_COUNT | SKIP=$SKIP_COUNT | FAIL=$FAIL_COUNT"
echo "================================================================="

if [ "$FAIL_COUNT" -gt 0 ]; then
    exit 1
fi
