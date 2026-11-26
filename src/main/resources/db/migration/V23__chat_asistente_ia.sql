-- V23: Soporte de chat híbrido (Asistente IA + traspaso a asesor humano)
ALTER TABLE conversaciones_chat
    ADD COLUMN IF NOT EXISTS modo_atencion VARCHAR(25) NOT NULL DEFAULT 'BOT';

UPDATE conversaciones_chat
SET modo_atencion = 'HUMANO_ACTIVO'
WHERE admin_id IS NOT NULL AND modo_atencion = 'BOT';

ALTER TABLE conversaciones_chat
    DROP CONSTRAINT IF EXISTS chk_conversacion_modo_atencion;
ALTER TABLE conversaciones_chat
    ADD CONSTRAINT chk_conversacion_modo_atencion
    CHECK (modo_atencion IN ('BOT', 'ESPERANDO_HUMANO', 'HUMANO_ACTIVO'));

-- Archiva salas activas duplicadas heredadas dejando solo la más reciente por cliente
WITH activas_ordenadas AS (
    SELECT id,
           ROW_NUMBER() OVER (PARTITION BY cliente_id ORDER BY fecha_creacion DESC, id DESC) AS rn
    FROM conversaciones_chat
    WHERE estado = 'ACTIVA'
)
UPDATE conversaciones_chat
SET estado = 'ARCHIVADA'
WHERE id IN (SELECT id FROM activas_ordenadas WHERE rn > 1);

CREATE UNIQUE INDEX IF NOT EXISTS uk_conversacion_activa_cliente
    ON conversaciones_chat (cliente_id)
    WHERE estado = 'ACTIVA';

ALTER TABLE chat_mensajes
    ALTER COLUMN contenido TYPE TEXT;

-- Rol y cuenta virtual del sistema (activo=FALSE para impedir login/autenticación interactiva)
INSERT INTO roles_config (codigo, nombre, descripcion, sistema, activo)
VALUES ('ASISTENTE_IA', 'Asistente Virtual IA', 'Asistente inteligente de compras y soporte', TRUE, FALSE)
ON CONFLICT (codigo) DO UPDATE
SET activo = FALSE, sistema = TRUE;

INSERT INTO usuarios (
    nombres, apellido_paterno, apellido_materno,
    tipo_documento, numero_documento, email,
    password_hash, rol, activo, fecha_registro
)
VALUES (
    'Electronix', 'AI', NULL,
    'CE', 'SYSBOT001', 'bot@electronix.com',
    '$2a$10$disabledSystemBotAccountHashNoInteractiveLogin00000000000',
    'ASISTENTE_IA', FALSE, CURRENT_TIMESTAMP
)
ON CONFLICT (email) DO NOTHING;
