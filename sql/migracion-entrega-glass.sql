-- ══════════════════════════════════════════════════════════════════════════════
-- migracion-entrega-glass.sql — entrega del teléfono al técnico de glass
-- Sella en la asignación de glass (filas AG) cuándo y quién bajó el teléfono.
-- APLICADA en preproducción el 2026-08-28 (vista previa 0 → ALTER OK, 13.564
-- filas → comprobación 2). NO idempotente (relanzar = error de columna existente).
-- 1) Vista previa: el SELECT debe devolver 0 (las columnas no existen aún).
-- 2) Ejecutar el ALTER.
-- 3) Comprobación: el SELECT final debe devolver 2.
-- ══════════════════════════════════════════════════════════════════════════════
USE gestion_reparaciones;

-- 1) Vista previa (no modifica nada) ──────────────────────────────────────────
SELECT COUNT(*) AS EXISTEN_COLUMNAS
  FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA = 'gestion_reparaciones'
   AND TABLE_NAME   = 'Reparacion'
   AND COLUMN_NAME IN ('ENTREGADO_AT', 'ENTREGADO_POR');

-- 2) Migración ────────────────────────────────────────────────────────────────
ALTER TABLE Reparacion
    ADD COLUMN ENTREGADO_AT  DATETIME NULL AFTER POR_CERRAR,
    ADD COLUMN ENTREGADO_POR INT      NULL AFTER ENTREGADO_AT,
    ADD CONSTRAINT fk_rep_entregado_por FOREIGN KEY (ENTREGADO_POR) REFERENCES Tecnico (ID_TEC);

-- 3) Comprobación ─────────────────────────────────────────────────────────────
SELECT COUNT(*) AS EXISTEN_COLUMNAS
  FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA = 'gestion_reparaciones'
   AND TABLE_NAME   = 'Reparacion'
   AND COLUMN_NAME IN ('ENTREGADO_AT', 'ENTREGADO_POR');
