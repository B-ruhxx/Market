#!/usr/bin/env bash
# ==============================================================================
# Script de Prueba de Carga y Estrés (Criterio C4 - Demostración en Vivo)
# Simula concurrencia sobre endpoint cacheado para verificar throughput y p95
# ==============================================================================
set -euo pipefail

TARGET_URL="${1:-http://localhost:8090/api/categorias}"
CONCURRENT="${2:-20}"
REQUESTS="${3:-200}"

if ! [[ "$CONCURRENT" =~ ^[0-9]+$ ]] || [ "$CONCURRENT" -lt 1 ]; then
    echo "[ERROR] Concurrencia debe ser un entero >= 1 (recibido: $CONCURRENT)" >&2
    exit 1
fi
if ! [[ "$REQUESTS" =~ ^[0-9]+$ ]] || [ "$REQUESTS" -lt 1 ]; then
    echo "[ERROR] Total de peticiones debe ser un entero >= 1 (recibido: $REQUESTS)" >&2
    exit 1
fi

echo "=========================================================="
echo "   ELECTRONIX - PRUEBA DE ESTRÉS Y ESCALABILIDAD CLOUD   "
echo "=========================================================="
echo "Destino:        $TARGET_URL"
echo "Concurrencia:   $CONCURRENT"
echo "Total Requests: $REQUESTS"
echo "Fecha y Hora:   $(date)"
echo "----------------------------------------------------------"

if command -v autocannon >/dev/null 2>&1; then
    echo "[INFO] Ejecutando autocannon (Node.js)..."
    autocannon -c "$CONCURRENT" -a "$REQUESTS" "$TARGET_URL"
elif command -v ab >/dev/null 2>&1; then
    echo "[INFO] Ejecutando Apache Benchmark (ab)..."
    ab -n "$REQUESTS" -c "$CONCURRENT" "$TARGET_URL"
else
    echo "[INFO] Ejecutando batería paralela con curl..."
    TMP_RES="$(mktemp)"
    trap 'rm -f "$TMP_RES"' EXIT

    START_NS="$(date +%s%N)"
    for i in $(seq 1 "$REQUESTS"); do
        (
            curl -s -o /dev/null -w "%{http_code} %{time_total}\n" "$TARGET_URL" >> "$TMP_RES" || true
        ) &
        if [ $((i % CONCURRENT)) -eq 0 ]; then
            wait
        fi
    done
    wait
    END_NS="$(date +%s%N)"

    ELAPSED_MS=$(( (END_NS - START_NS) / 1000000 ))
    ELAPSED_MS=$(( ELAPSED_MS > 0 ? ELAPSED_MS : 1 ))

    awk -v total="$REQUESTS" -v elapsed_ms="$ELAPSED_MS" '
    BEGIN { ok=0; err=0; sum_ms=0; max_ms=0; n=0 }
    {
        code = $1 + 0
        ms = $2 * 1000
        if (code >= 200 && code < 400) ok++; else err++
        sum_ms += ms
        if (ms > max_ms) max_ms = ms
        lat[++n] = ms
    }
    END {
        for (i = 1; i <= n; i++) {
            for (j = i + 1; j <= n; j++) {
                if (lat[i] > lat[j]) { tmp = lat[i]; lat[i] = lat[j]; lat[j] = tmp }
            }
        }
        p95_idx = int(n * 0.95)
        if (p95_idx < 1) p95_idx = 1
        rps = (total * 1000.0) / elapsed_ms
        avg = n > 0 ? (sum_ms / n) : 0
        printf "----------------------------------------------------------\n"
        printf "Completadas:      %d (OK: %d | Errores: %d)\n", n, ok, err
        printf "Tiempo total:     %d ms\n", elapsed_ms
        printf "Throughput:       %.2f req/s\n", rps
        printf "Latencia prom:    %.2f ms\n", avg
        printf "Latencia p95:     %.2f ms\n", lat[p95_idx]
        printf "Latencia max:     %.2f ms\n", max_ms
        printf "==========================================================\n"
    }' "$TMP_RES"
fi
