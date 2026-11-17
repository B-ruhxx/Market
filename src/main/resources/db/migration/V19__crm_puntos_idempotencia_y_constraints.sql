-- V19: Idempotencia y restricciones de integridad para el Loyalty Ledger CRM
-- Previene carreras concurrentes de acreditación múltiple de puntos para un mismo pedido

CREATE UNIQUE INDEX IF NOT EXISTS idx_puntos_tx_acreditado_pedido
ON crm_puntos_transacciones (pedido_id)
WHERE tipo = 'ACREDITADO_COMPRA';
