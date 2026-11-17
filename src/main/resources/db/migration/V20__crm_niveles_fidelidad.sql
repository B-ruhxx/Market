-- ========================================================
-- TABLA: crm_niveles
-- ========================================================
CREATE TABLE crm_niveles (
    codigo              VARCHAR(20)   PRIMARY KEY,
    nombre              VARCHAR(50)   NOT NULL,
    umbral_pen_anual    NUMERIC(19,4) NOT NULL DEFAULT 0,
    multiplicador       NUMERIC(5,2)  NOT NULL DEFAULT 1.00,
    beneficio_envio     BOOLEAN       NOT NULL DEFAULT FALSE,
    orden               INTEGER       NOT NULL,
    CONSTRAINT chk_niveles_multiplicador CHECK (multiplicador >= 1.00),
    CONSTRAINT chk_niveles_umbral CHECK (umbral_pen_anual >= 0)
);

-- Semilla de niveles VIP iniciales
INSERT INTO crm_niveles (codigo, nombre, umbral_pen_anual, multiplicador, beneficio_envio, orden) VALUES
  ('BRONCE', 'Bronce', 0.0000,    1.00, false, 1),
  ('PLATA',  'Plata',  600.0000,  1.25, false, 2),
  ('ORO',    'Oro',    1800.0000, 1.50, true,  3);

-- Agregar relación de nivel a la billetera de fidelización
ALTER TABLE crm_puntos_billetera ADD COLUMN nivel_codigo VARCHAR(20) NOT NULL DEFAULT 'BRONCE'
  REFERENCES crm_niveles(codigo);

CREATE INDEX idx_billetera_nivel ON crm_puntos_billetera (nivel_codigo);
CREATE INDEX idx_niveles_orden ON crm_niveles (orden ASC);
