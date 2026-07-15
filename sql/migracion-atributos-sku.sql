-- ══════════════════════════════════════════════════════════════════════════════
-- migracion-atributos-sku.sql — mini-fase atributos SKU-ready (spec 2026-07-14)
-- La aplica el usuario a mano en gestion_reparaciones. NO idempotente.
-- ══════════════════════════════════════════════════════════════════════════════

USE gestion_reparaciones;

-- Flag de variante eSIM (el importador lo marca solo si el texto trae "esim")
ALTER TABLE Telefono
    ADD COLUMN ES_ESIM BOOLEAN NOT NULL DEFAULT FALSE AFTER GRADO_PROPIO;

-- Escala de grado definitiva: A+ eliminado (decisión 2026-07-14, A = máximo)
UPDATE Telefono SET GRADO_PROPIO = 'A' WHERE GRADO_PROPIO = 'A+';
ALTER TABLE Telefono
    MODIFY COLUMN GRADO_PROPIO ENUM('C','B','A-','A') NULL;

-- Equivalencias de color recordadas por el importador ("blanco roto" → "White").
-- TEXTO_EXTERNO NORMALIZADO: minúsculas, solo [a-z0-9].
CREATE TABLE Color_equivalencia (
    TEXTO_EXTERNO  VARCHAR(100) NOT NULL,
    COLOR_OFICIAL  VARCHAR(50)  NOT NULL,
    UPDATED_AT     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (TEXTO_EXTERNO)
);
