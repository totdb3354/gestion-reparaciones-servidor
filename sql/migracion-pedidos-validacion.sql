-- ══════════════════════════════════════════════════════════════════════════════
-- migracion-pedidos-validacion.sql: «Validar SKU» en el aviso naranja del IMEI de una fila de «Pedidos»
-- (spec docs/superpowers/specs/2026-11-16-pedidos-validar-sku-design.md). PRODUCCION Y PREPROD: el mismo fichero.
-- La tabla Pedido_fila ya existe, sin estas columnas:
--   VALIDADA_IMEI (VARCHAR(20): el IMEI para el que se dio por bueno el aviso; solo vale mientras sea el IMEI de la fila),
--   VALIDADA_EN (cuando) y VALIDADA_USU (quien; clave foranea a Usuario, ON DELETE SET NULL).
-- Los apuntes nuevos del historial (tipos VALIDADA y VALIDACION_QUITADA) caben en Pedido_fila_cambio tal como esta (TIPO es VARCHAR(20)).
-- La aplica el usuario a mano en la VM, con vista previa, ANTES de desplegar el servidor.
-- Aditiva e idempotente: solo anade lo que falta (si ya esta todo, no hace nada y se puede relanzar). El servidor anterior no lee las
-- columnas nuevas: funciona con todo puesto. No lanzarla mientras corre la copia nocturna.
-- 1) Vista previa: YA_ESTAN debe dar 0 (primera vez) o 3 (ya aplicado).
-- 2) Anade las columnas (y la clave foranea de VALIDADA_USU) que falten.
-- 3) Comprobacion final: NUEVAS debe dar 3 y LA_FK, 1; si no, NO desplegar el servidor.
-- Vuelta atras: bastaria con ignorarlas (o ALTER TABLE ... DROP COLUMN; antes, DROP FOREIGN KEY fk_fila_validada_usuario).
-- ══════════════════════════════════════════════════════════════════════════════
USE gestion_reparaciones;
SET NAMES utf8mb4;

-- Vista previa (no modifica nada)
SELECT COUNT(*) AS YA_ESTAN FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA = 'gestion_reparaciones' AND TABLE_NAME = 'Pedido_fila'
   AND COLUMN_NAME IN ('VALIDADA_IMEI', 'VALIDADA_EN', 'VALIDADA_USU');

-- Si algo tiene en uso Pedido_fila o Usuario, no esperar mas de 10 s por el bloqueo de metadatos: falla y se relanza.
SET SESSION lock_wait_timeout = 10;

-- MariaDB: ADD COLUMN IF NOT EXISTS y ADD CONSTRAINT ... FOREIGN KEY IF NOT EXISTS hacen idempotente cada paso
-- (MariaDB no admite «ADD CONSTRAINT IF NOT EXISTS ... FOREIGN KEY»: da error 1064).
ALTER TABLE Pedido_fila
    ADD COLUMN IF NOT EXISTS VALIDADA_IMEI VARCHAR(20) NULL COMMENT 'El IMEI para el que una persona dio por bueno el aviso de SKU; solo vale mientras sea el IMEI de la fila',
    ADD COLUMN IF NOT EXISTS VALIDADA_EN   DATETIME    NULL COMMENT 'Cuando se valido',
    ADD COLUMN IF NOT EXISTS VALIDADA_USU  INT         NULL COMMENT 'Quien valido';
ALTER TABLE Pedido_fila
    ADD CONSTRAINT fk_fila_validada_usuario FOREIGN KEY IF NOT EXISTS (VALIDADA_USU) REFERENCES Usuario (ID_USU) ON DELETE SET NULL;

-- Comprobacion final (no modifica nada): NUEVAS = 3 y LA_FK = 1; si no, NO desplegar el servidor.
SELECT COUNT(*) AS NUEVAS FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA = 'gestion_reparaciones' AND TABLE_NAME = 'Pedido_fila'
   AND COLUMN_NAME IN ('VALIDADA_IMEI', 'VALIDADA_EN', 'VALIDADA_USU');
SELECT COUNT(*) AS LA_FK FROM information_schema.TABLE_CONSTRAINTS
 WHERE CONSTRAINT_SCHEMA = 'gestion_reparaciones' AND TABLE_NAME = 'Pedido_fila' AND CONSTRAINT_NAME = 'fk_fila_validada_usuario';
