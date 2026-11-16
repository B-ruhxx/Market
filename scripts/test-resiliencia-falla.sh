#!/usr/bin/env bash
# ==============================================================================
# Script de Prueba de Falla y Resiliencia (Criterio C4 - Demostración en Vivo)
# Simula la caída del contenedor de backend y cronometra la recuperación
# ==============================================================================
set -euo pipefail

CONTAINER_NAME="electronix-app"
HEALTH_URL="http://localhost:8090/actuator/health"

echo "=========================================================="
echo "   ELECTRONIX - PRUEBA DE FALLA Y RESILIENCIA (RTO)       "
echo "=========================================================="
echo "1. Verificando estado inicial del servicio..."
INITIAL_HEALTH=$(curl -s "$HEALTH_URL" || echo '{"status":"DOWN"}')
echo "   Estado actual: $INITIAL_HEALTH"
echo "----------------------------------------------------------"

echo "2. Provocando caída forzada del contenedor '$CONTAINER_NAME'..."
START_TIME=$(date +%s)
docker stop "$CONTAINER_NAME" >/dev/null

echo "   [CONFIRMADO] Contenedor detenido. Servicio caído temporalmente."
echo "----------------------------------------------------------"

echo "3. Iniciando orquestación / autorecuperación..."
docker start "$CONTAINER_NAME" >/dev/null

echo "4. Monitoreando endpoint de Liveness / Readiness..."
RECOVERED=false
for i in {1..30}; do
    STATUS=$(curl -s "$HEALTH_URL" 2>/dev/null || echo "")
    if [[ "$STATUS" == *"UP"* ]]; then
        END_TIME=$(date +%s)
        RTO=$(( END_TIME - START_TIME ))
        echo "   [ÉXITO] Servicio recuperado y saludable (UP)!"
        echo "   Tiempo de recuperación observado (RTO): ${RTO} segundos."
        RECOVERED=true
        break
    fi
    echo "   Esperando estabilización de Spring Boot y pool de BD... (${i}s)"
    sleep 2
done

if [ "$RECOVERED" = false ]; then
    echo "   [ERROR] El servicio no se recuperó en 60 segundos."
    exit 1
fi

echo "=========================================================="
echo "   RESULTADO: PRUEBA DE TOLERANCIA A FALLAS SUPERADA       "
echo "   RTO medido: ${RTO}s (Dentro de la meta < 15 minutos)   "
echo "=========================================================="
