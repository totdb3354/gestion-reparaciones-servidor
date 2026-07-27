-- ══════════════════════════════════════════════════════════════════════════════
-- migracion-f2c-drop-check.sql — F2c: retirar el check antiguo REVISION_LOGISTICA
-- APLICAR SOLO DESPUÉS de desplegar el servidor F2c Y actualizar los clientes
-- (el servidor viejo aún escribe la columna; el nuevo ya no la usa).
-- 1) Vista previa: debe devolver 1 (la columna existe todavía).
-- 2) Ejecutar el ALTER.
-- ══════════════════════════════════════════════════════════════════════════════
USE gestion_reparaciones;

SELECT COUNT(*) AS EXISTE_COLUMNA
  FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA = 'gestion_reparaciones' AND TABLE_NAME = 'Telefono'
   AND COLUMN_NAME = 'REVISION_LOGISTICA';

ALTER TABLE Telefono DROP COLUMN REVISION_LOGISTICA;
