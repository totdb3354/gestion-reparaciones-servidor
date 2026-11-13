-- ══════════════════════════════════════════════════════════════════════════════
-- migracion-pedidos-marcas.sql: marcas de la fila en «Pedidos» (En espera de stock, En revision, Pendiente de color, Cancelado, con nota).
-- (spec docs/superpowers/specs/2026-11-13-pedidos-marcas-design.md). PRODUCCION Y PREPROD: el mismo fichero.
-- Las tablas Pedido_fila y Pedido_fila_cambio ya existen, sin estas columnas:
--   Pedido_fila: MARCA (ESPERA_STOCK, EN_REVISION, PENDIENTE_COLOR o CANCELADO; NULL = sin marca), MARCA_NOTA (hasta 200), MARCA_EN (cuando) y
--   MARCA_USU (quien; clave foranea a Usuario); Pedido_fila_cambio: DETALLE (la marca y su nota en los apuntes MARCA y MARCA_QUITADA).
-- La aplica el usuario a mano en la VM, con vista previa, ANTES de desplegar el servidor.
-- Aditiva e idempotente: solo anade lo que falta (si ya esta todo, no hace nada y se puede relanzar). El servidor anterior no lee las
-- columnas nuevas: funciona con todo puesto. No lanzarla mientras corre la copia nocturna.
-- 1) Vista previa: YA_ESTAN debe dar 0 (primera vez) o 5 (ya aplicado).
-- 2) Anade las columnas (y la clave foranea de MARCA_USU) que falten.
-- 3) Comprobacion final: NUEVAS debe dar 5 y LA_FK, 1; si no, NO desplegar el servidor.
-- Vuelta atras: bastaria con ignorarlas (o ALTER TABLE ... DROP COLUMN; antes, DROP FOREIGN KEY fk_fila_marca_usuario).
-- ══════════════════════════════════════════════════════════════════════════════
USE gestion_reparaciones;
SET NAMES utf8mb4;

-- Vista previa (no modifica nada)
SELECT COUNT(*) AS YA_ESTAN FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA = 'gestion_reparaciones'
   AND ((TABLE_NAME = 'Pedido_fila' AND COLUMN_NAME IN ('MARCA', 'MARCA_NOTA', 'MARCA_EN', 'MARCA_USU'))
     OR (TABLE_NAME = 'Pedido_fila_cambio' AND COLUMN_NAME = 'DETALLE'));

SET SESSION lock_wait_timeout = 10;

-- MariaDB: ADD COLUMN IF NOT EXISTS y ADD CONSTRAINT ... FOREIGN KEY IF NOT EXISTS hacen idempotente cada paso
-- (MariaDB no admite «ADD CONSTRAINT IF NOT EXISTS ... FOREIGN KEY»: da error 1064).
ALTER TABLE Pedido_fila
    ADD COLUMN IF NOT EXISTS MARCA      VARCHAR(20)  NULL COMMENT 'ESPERA_STOCK, EN_REVISION, PENDIENTE_COLOR o CANCELADO; NULL = sin marca',
    ADD COLUMN IF NOT EXISTS MARCA_NOTA VARCHAR(200) NULL COMMENT 'Nota libre de la marca',
    ADD COLUMN IF NOT EXISTS MARCA_EN   DATETIME     NULL COMMENT 'Cuando se puso la marca',
    ADD COLUMN IF NOT EXISTS MARCA_USU  INT          NULL COMMENT 'Quien puso la marca';
ALTER TABLE Pedido_fila
    ADD CONSTRAINT fk_fila_marca_usuario FOREIGN KEY IF NOT EXISTS (MARCA_USU) REFERENCES Usuario (ID_USU) ON DELETE SET NULL;
ALTER TABLE Pedido_fila_cambio
    ADD COLUMN IF NOT EXISTS DETALLE VARCHAR(250) NULL COMMENT 'Marca y nota en los apuntes MARCA y MARCA_QUITADA';

-- Comprobacion final (no modifica nada): NUEVAS = 5 y LA_FK = 1; si no, NO desplegar el servidor.
SELECT COUNT(*) AS NUEVAS FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA = 'gestion_reparaciones'
   AND ((TABLE_NAME = 'Pedido_fila' AND COLUMN_NAME IN ('MARCA', 'MARCA_NOTA', 'MARCA_EN', 'MARCA_USU'))
     OR (TABLE_NAME = 'Pedido_fila_cambio' AND COLUMN_NAME = 'DETALLE'));
SELECT COUNT(*) AS LA_FK FROM information_schema.TABLE_CONSTRAINTS
 WHERE CONSTRAINT_SCHEMA = 'gestion_reparaciones' AND TABLE_NAME = 'Pedido_fila' AND CONSTRAINT_NAME = 'fk_fila_marca_usuario';
