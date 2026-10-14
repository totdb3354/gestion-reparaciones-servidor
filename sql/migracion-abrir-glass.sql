-- ══════════════════════════════════════════════════════════════════════════════
-- migracion-abrir-glass.sql — «Abrir» automático al entregar a glass (spec 2026-10-13)
-- Columna ID_REP_ABRIR en Reparacion (solo filas AG: el R… del «Abrir» que creó su
-- entrega, para que deshacer borre justo ese) y claves de puntos abrir (0,25) y
-- cerrar (0,17). Solo añade: el servidor y la web actuales siguen funcionando.
-- El ALTER NO es idempotente (relanzar = error de columna existente); el INSERT sí.
-- 1) Vista previa: EXISTE_COLUMNA = 0 y CLAVES = 0.
-- 2) Ejecutar la migración.
-- 3) Comprobación: EXISTE_COLUMNA = 1 y CLAVES = 2.
-- ══════════════════════════════════════════════════════════════════════════════
USE gestion_reparaciones;

-- 1) Vista previa (no modifica nada) ──────────────────────────────────────────
SELECT COUNT(*) AS EXISTE_COLUMNA
  FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA = 'gestion_reparaciones'
   AND TABLE_NAME   = 'Reparacion'
   AND COLUMN_NAME  = 'ID_REP_ABRIR';
SELECT COUNT(*) AS CLAVES FROM Dificultad_puntos WHERE CLAVE IN ('abrir', 'cerrar');

-- 2) Migración ────────────────────────────────────────────────────────────────
ALTER TABLE Reparacion
    ADD COLUMN ID_REP_ABRIR VARCHAR(30) NULL AFTER ENTREGADO_POR;

INSERT IGNORE INTO Dificultad_puntos (CLAVE, PUNTOS) VALUES
    ('abrir',  0.25),
    ('cerrar', 0.17);

-- 3) Comprobación ─────────────────────────────────────────────────────────────
SELECT COUNT(*) AS EXISTE_COLUMNA
  FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA = 'gestion_reparaciones'
   AND TABLE_NAME   = 'Reparacion'
   AND COLUMN_NAME  = 'ID_REP_ABRIR';
SELECT CLAVE, PUNTOS FROM Dificultad_puntos WHERE CLAVE IN ('abrir', 'cerrar') ORDER BY CLAVE;
