-- ========================================================
-- TABLA: crm_clientes_notas (Ficha 360° - Notas Internas de Soporte)
-- ========================================================
CREATE TABLE crm_clientes_notas (
    id              BIGSERIAL PRIMARY KEY,
    usuario_id      BIGINT      NOT NULL REFERENCES usuarios(id),
    admin_id        BIGINT      NOT NULL REFERENCES usuarios(id),
    nota            VARCHAR(2000) NOT NULL,
    fecha           TIMESTAMP   NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_notas_usuario ON crm_clientes_notas (usuario_id, fecha DESC);
CREATE INDEX idx_notas_admin ON crm_clientes_notas (admin_id);
