#!/usr/bin/env bash
# ==============================================================================
# Script de Prueba de Carga y Estrés (Criterio C4 - Demostración en Vivo)
# Simula concurrencia para verificar latencia y tasa de errores
# ==============================================================================
set -euo pipefail

TARGET_URL="${1:-http://localhost:8090/api/productos}"
CONCURRENT="${2:-20}"
REQUESTS="${3:-200}"

echo "=========================================================="
echo "   ELECTRONIX - PRUEBA DE ESTRÉS Y ESCALABILIDAD CLOUD   "
echo "=========================================================="
echo "Destino:       $TARGET_URL"
echo "Concurrencia:  $CONCURRENT"
echo "Total Requests: $REQUESTS"
echo "Fecha y Hora:  $(date)"
echo "----------------------------------------------------------"

if command -v autocannon >/dev/null 2>&1; then
    echo "[INFO] Ejecutando autocannon (Node.js)..."
    autocannon -c "$CONCURRENT" -a "$REQUESTS" "$TARGET_URL"
elif command -v ab >/dev/null 2>&1; then
    echo "[INFO] Ejecutando Apache Benchmark (ab)..."
    ab -n "$REQUESTS" -c "$CONCURRENT" "$TARGET_URL"
else
    echo "[INFO] Ni ab ni autocannon instalados. Ejecutando batería en paralelo con curl..."
    START_TIME=$(date +%s%N)
    SUCCESS=0
    FAILED=0

    for i in $(seq 1 "$REQUESTS"); do
        (
            HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" "$TARGET_URL" || echo "000")
            if [ "$HTTP_CODE" -ge 200 ] && [ "$HTTP_CODE" -lt 400 ]; then
                echo "200"
            else
                echo "ERR"
            fi
        ) &
        if [ $((i % CONCURRENT)) -eq 0 ]; then
            wait
        fi
    done
    wait

    END_TIME=$(date +%s%N)
    ELAPSED_MS=$(( (END_TIME - START_TIME) / 1000000 ))
    echo "----------------------------------------------------------"
    echo "Total peticiones completadas: $REQUESTS"
    echo "Tiempo total transcurrido:    ${ELAPSED_MS} ms"
    echo "Promedio aproximado:          $(( ELAPSED_MS / REQUESTS )) ms/petición"
    echo "=========================================================="
fi
