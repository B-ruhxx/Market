-- V24: Endurecimiento idempotente del chat híbrido IA para entornos ya migrados en V23

-- Permite mantener desactivada la cuenta virtual ASISTENTE_IA sin violar proteger_rol_sistema()
CREATE OR REPLACE FUNCTION proteger_rol_sistema() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
 IF OLD.sistema AND OLD.codigo <> 'ASISTENTE_IA'
    AND (TG_OP = 'DELETE' OR NEW.codigo IS DISTINCT FROM OLD.codigo OR NOT NEW.activo OR NEW.sistema IS DISTINCT FROM OLD.sistema) THEN
   RAISE EXCEPTION 'No se puede eliminar, renombrar o desactivar un rol del sistema';
 END IF;
 RETURN CASE WHEN TG_OP = 'DELETE' THEN OLD ELSE NEW END;
END $$;

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
ON CONFLICT (email) DO UPDATE
SET activo = FALSE,
    rol = 'ASISTENTE_IA',
    numero_documento = 'SYSBOT001';
