-- ══════════════════════════════════════════════════════════════════════════════
-- migracion-nombres-y-coste-nsys.sql — 0.9.22 pieza 1, leer mejor el maestro y NSYS
-- (spec docs/superpowers/specs/2026-10-25-v0922-plantillas-nsys-design.md §2.4, raíz).
-- La aplica el usuario a mano en la VM, con vista previa, ANTES de desplegar el
-- servidor 0.9.22. Aditiva: el servidor anterior no lee estas columnas.
-- NO idempotente (relanzar = error de columna existente).
-- 1) Vista previa: YA_EXISTEN debe dar 0.
-- 2) Los dos ALTER TABLE.
-- 3) Comprobación final: CREADAS debe dar 2.
-- Vuelta atrás: el servidor anterior funciona con las columnas puestas; no hace falta quitarlas.
-- ══════════════════════════════════════════════════════════════════════════════
USE gestion_reparaciones;
SET NAMES utf8mb4;

-- Vista previa (no modifica nada): debe dar 0
SELECT COUNT(*) AS YA_EXISTEN
  FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA = 'gestion_reparaciones'
   AND ((TABLE_NAME = 'Nsys_producto' AND COLUMN_NAME = 'NOMBRE') OR (TABLE_NAME = 'Nsys_movil' AND COLUMN_NAME = 'COSTE'));

-- El «Product Name» del maestro tal cual: da el nombre en inglés de cada color de cada modelo. Se rellena al recargar el maestro.
ALTER TABLE Nsys_producto
    ADD COLUMN NOMBRE VARCHAR(150) NULL COMMENT '«Product Name» del maestro tal cual (0.9.22)' AFTER CLAVE;

-- El «Total cost» de NSYS de cada lote: solo para la plantilla de color. Se rellena con la siguiente importación de Items.
ALTER TABLE Nsys_movil
    ADD COLUMN COSTE DECIMAL(10,2) NULL COMMENT '«Total cost» de NSYS de este lote (0.9.22)' AFTER SUPPLIER;

-- Comprobación final (no modifica nada): debe dar 2; si no, NO desplegar el servidor 0.9.22 (lee las dos columnas).
SELECT COUNT(*) AS CREADAS
  FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA = 'gestion_reparaciones'
   AND ((TABLE_NAME = 'Nsys_producto' AND COLUMN_NAME = 'NOMBRE') OR (TABLE_NAME = 'Nsys_movil' AND COLUMN_NAME = 'COSTE'));
