-- V18: Módulo CRM - Billetera de Puntos de Fidelización y Ledger

-- 1. Tabla de Configuración de Puntos (Singleton, id = 1)
CREATE TABLE IF NOT EXISTS crm_puntos_config (
    id                          INTEGER PRIMARY KEY DEFAULT 1,
    factor_sol_por_punto        NUMERIC(10,4) NOT NULL DEFAULT 5.0000, -- S/ 5.00 de compra = 1 punto ganado
    valor_pen_por_punto         NUMERIC(10,4) NOT NULL DEFAULT 0.0500, -- 1 punto canjeado = S/ 0.05 (100 pts = S/ 5.00)
    minimo_puntos_canje         INTEGER       NOT NULL DEFAULT 100,    -- Mínimo 100 puntos para canjear
    max_cobertura_porcentaje    INTEGER       NOT NULL DEFAULT 50,     -- Máx 50% de descuento sobre el subtotal
    puntos_bienvenida           INTEGER       NOT NULL DEFAULT 50,     -- 50 puntos de regalo al registrarse
    activo                      BOOLEAN       NOT NULL DEFAULT TRUE,
    CONSTRAINT chk_config_puntos_id CHECK (id = 1),
    CONSTRAINT chk_config_puntos_porcentaje CHECK (max_cobertura_porcentaje > 0 AND max_cobertura_porcentaje <= 100),
    CONSTRAINT chk_config_puntos_minimo CHECK (minimo_puntos_canje >= 0)
);

INSERT INTO crm_puntos_config (id, factor_sol_por_punto, valor_pen_por_punto, minimo_puntos_canje, max_cobertura_porcentaje, puntos_bienvenida, activo)
VALUES (1, 5.0000, 0.0500, 100, 50, 50, true)
ON CONFLICT (id) DO NOTHING;

-- 2. Billetera de Puntos por Usuario
CREATE TABLE IF NOT EXISTS crm_puntos_billetera (
    id                      BIGSERIAL PRIMARY KEY,
    usuario_id              BIGINT        NOT NULL REFERENCES usuarios(id) UNIQUE,
    saldo_puntos            INTEGER       NOT NULL DEFAULT 0,
    puntos_historicos       INTEGER       NOT NULL DEFAULT 0,
    fecha_actualizacion     TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version                 BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT chk_billetera_saldo CHECK (saldo_puntos >= 0),
    CONSTRAINT chk_billetera_historicos CHECK (puntos_historicos >= 0)
);

CREATE INDEX IF NOT EXISTS idx_billetera_usuario ON crm_puntos_billetera(usuario_id);

-- 3. Ledger de Transacciones de Puntos (Inmutable)
CREATE TABLE IF NOT EXISTS crm_puntos_transacciones (
    id                  BIGSERIAL PRIMARY KEY,
    billetera_id        BIGINT          NOT NULL REFERENCES crm_puntos_billetera(id),
    tipo                VARCHAR(30)     NOT NULL, -- BONO_BIENVENIDA, ACREDITADO_COMPRA, CANJEADO_PEDIDO, REVERSION_CANCELACION, AJUSTE_ADMIN
    puntos              INTEGER         NOT NULL, -- Positivo (ganado/acreditado), Negativo (canjeado)
    saldo_resultante    INTEGER         NOT NULL CHECK (saldo_resultante >= 0),
    pedido_id           BIGINT          REFERENCES pedidos(id),
    descripcion         VARCHAR(255),
    admin_usuario_id    BIGINT          REFERENCES usuarios(id),
    fecha               TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_puntos_tx_billetera ON crm_puntos_transacciones(billetera_id, fecha DESC);
CREATE INDEX IF NOT EXISTS idx_puntos_tx_pedido ON crm_puntos_transacciones(pedido_id);

-- 4. Inicializar billetera con bono de bienvenida para usuarios existentes sin billetera
INSERT INTO crm_puntos_billetera (usuario_id, saldo_puntos, puntos_historicos, fecha_actualizacion, version)
SELECT u.id, 50, 50, CURRENT_TIMESTAMP, 0
FROM usuarios u
LEFT JOIN crm_puntos_billetera b ON u.id = b.usuario_id
WHERE b.id IS NULL
ON CONFLICT (usuario_id) DO NOTHING;

INSERT INTO crm_puntos_transacciones (billetera_id, tipo, puntos, saldo_resultante, descripcion, fecha)
SELECT b.id, 'BONO_BIENVENIDA', 50, 50, 'Bono de bienvenida al programa de fidelización', CURRENT_TIMESTAMP
FROM crm_puntos_billetera b
LEFT JOIN crm_puntos_transacciones t ON b.id = t.billetera_id
WHERE t.id IS NULL;
