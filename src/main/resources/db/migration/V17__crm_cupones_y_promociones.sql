-- V17: Módulo CRM - Cupones y Promociones

-- 1. Permisos del módulo CRM
INSERT INTO permisos(codigo, nombre) VALUES
 ('CRM_GESTIONAR', 'Administrar cupones, promociones y fidelización CRM'),
 ('CRM_VER_PUNTOS', 'Consultar y redimir puntos y beneficios CRM')
ON CONFLICT DO NOTHING;

INSERT INTO rol_permisos(rol_codigo, permiso_codigo) VALUES ('ADMIN', 'CRM_GESTIONAR') ON CONFLICT DO NOTHING;
INSERT INTO rol_permisos(rol_codigo, permiso_codigo) VALUES ('ADMIN', 'CRM_VER_PUNTOS') ON CONFLICT DO NOTHING;
INSERT INTO rol_permisos(rol_codigo, permiso_codigo) VALUES ('CLIENTE', 'CRM_VER_PUNTOS') ON CONFLICT DO NOTHING;

-- 2. Tabla de Cupones de Promoción
CREATE TABLE IF NOT EXISTS crm_cupones (
    id                      BIGSERIAL PRIMARY KEY,
    codigo                  VARCHAR(30)     NOT NULL,
    descripcion             VARCHAR(255),
    tipo_descuento          VARCHAR(20)     NOT NULL, -- PORCENTAJE | MONTO_FIJO | ENVIO_GRATIS
    valor                   NUMERIC(19,4)   NOT NULL CHECK (valor > 0),
    monto_minimo_pen        NUMERIC(19,4)   NOT NULL DEFAULT 0,
    tope_descuento_pen      NUMERIC(19,4),
    limite_usos_total       INTEGER,
    usos_actuales           INTEGER         NOT NULL DEFAULT 0 CHECK (usos_actuales >= 0),
    limite_por_usuario      INTEGER         NOT NULL DEFAULT 1 CHECK (limite_por_usuario >= 1),
    permite_combinar_puntos BOOLEAN         NOT NULL DEFAULT FALSE,
    fecha_inicio            TIMESTAMP       NOT NULL,
    fecha_fin               TIMESTAMP       NOT NULL,
    activo                  BOOLEAN         NOT NULL DEFAULT TRUE,
    version                 BIGINT          NOT NULL DEFAULT 0,
    CONSTRAINT uk_cupones_codigo UNIQUE (codigo),
    CONSTRAINT chk_cupones_fechas CHECK (fecha_fin > fecha_inicio)
);

CREATE INDEX IF NOT EXISTS idx_cupones_activo_fechas ON crm_cupones (activo, fecha_inicio, fecha_fin);

-- 3. Tabla de Usos de Cupones
CREATE TABLE IF NOT EXISTS crm_cupones_usos (
    id                      BIGSERIAL PRIMARY KEY,
    cupon_id                BIGINT          NOT NULL REFERENCES crm_cupones(id),
    usuario_id              BIGINT          NOT NULL REFERENCES usuarios(id),
    pedido_id               BIGINT          REFERENCES pedidos(id),
    descuento_aplicado_pen  NUMERIC(19,4)   NOT NULL,
    activo                  BOOLEAN         NOT NULL DEFAULT TRUE,
    fecha_uso               TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_cupon_uso_activo UNIQUE (cupon_id, usuario_id, pedido_id)
);

CREATE INDEX IF NOT EXISTS idx_cupon_usos_usuario ON crm_cupones_usos (usuario_id);
CREATE INDEX IF NOT EXISTS idx_cupon_usos_cupon ON crm_cupones_usos (cupon_id);

-- 4. Ampliar tabla de pedidos para registrar beneficios CRM
ALTER TABLE pedidos ADD COLUMN IF NOT EXISTS cupon_codigo VARCHAR(30);
ALTER TABLE pedidos ADD COLUMN IF NOT EXISTS descuento_pen NUMERIC(19,4) DEFAULT 0;
ALTER TABLE pedidos ADD COLUMN IF NOT EXISTS puntos_canjeados INTEGER DEFAULT 0;
ALTER TABLE pedidos ADD COLUMN IF NOT EXISTS descuento_puntos_pen NUMERIC(19,4) DEFAULT 0;
