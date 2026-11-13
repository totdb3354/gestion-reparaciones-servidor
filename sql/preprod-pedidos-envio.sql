-- ══════════════════════════════════════════════════════════════════════════════
-- preprod-pedidos-envio.sql: SOLO PARA PREPRODUCCION. NO es para produccion.
-- En preprod la tabla Pedido_fila ya existe con la columna ENVIADA_EN (la creo una version anterior de
-- sql/migracion-pedidos-tabla.sql). En produccion la tabla aun no existe: alli la migracion ya trae CERRADA_EN,
-- ENVIO_FECHA, TRANSPORTISTA y SEGUIMIENTO y este fichero no se aplica nunca.
-- (spec docs/superpowers/specs/2026-11-10-pedidos-tabla-design.md, seccion 6 bis).
-- La aplica el usuario a mano en la VM de preprod, con vista previa, ANTES de desplegar el servidor.
-- NO idempotente (relanzar = error de columna inexistente o existente).
-- 1) Vista previa: ANTES debe dar 1 (ENVIADA_EN) y NUEVAS_YA, 0.
-- 2) Renombra ENVIADA_EN a CERRADA_EN (los datos se conservan) y anade cuatro columnas.
-- 3) Comprobacion final: CERRADA debe dar 1, NUEVAS 4 y VIEJA 0.
-- Vuelta atras: el servidor anterior lee ENVIADA_EN; habria que deshacer el CHANGE y quitar las tres columnas.
-- ══════════════════════════════════════════════════════════════════════════════
USE gestion_reparaciones;
SET NAMES utf8mb4;

-- Vista previa (no modifica nada)
SELECT COUNT(*) AS ANTES FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA = 'gestion_reparaciones' AND TABLE_NAME = 'Pedido_fila' AND COLUMN_NAME = 'ENVIADA_EN';
SELECT COUNT(*) AS NUEVAS_YA FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA = 'gestion_reparaciones' AND TABLE_NAME = 'Pedido_fila'
   AND COLUMN_NAME IN ('CERRADA_EN', 'ENVIO_FECHA', 'TRANSPORTISTA', 'SEGUIMIENTO', 'REABIERTA_EN');

SET SESSION lock_wait_timeout = 10;

ALTER TABLE Pedido_fila
    CHANGE ENVIADA_EN CERRADA_EN DATETIME NULL COMMENT 'Cuando dejo de estar pendiente; NULL = pendiente',
    ADD COLUMN ENVIO_FECHA   DATETIME    NULL COMMENT 'Cuando ShippyPro creo el envio (hora de Madrid); NULL = sin envio conocido',
    ADD COLUMN TRANSPORTISTA VARCHAR(40) NULL,
    ADD COLUMN SEGUIMIENTO   VARCHAR(60) NULL,
    ADD COLUMN REABIERTA_EN  DATETIME    NULL COMMENT 'Cuando el pedido volvio a estar pendiente';

-- Comprobacion final (no modifica nada): CERRADA = 1, NUEVAS = 4 y VIEJA = 0; si no, NO desplegar el servidor.
SELECT COUNT(*) AS CERRADA FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA = 'gestion_reparaciones' AND TABLE_NAME = 'Pedido_fila' AND COLUMN_NAME = 'CERRADA_EN';
SELECT COUNT(*) AS NUEVAS FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA = 'gestion_reparaciones' AND TABLE_NAME = 'Pedido_fila'
   AND COLUMN_NAME IN ('ENVIO_FECHA', 'TRANSPORTISTA', 'SEGUIMIENTO', 'REABIERTA_EN');
SELECT COUNT(*) AS VIEJA FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA = 'gestion_reparaciones' AND TABLE_NAME = 'Pedido_fila' AND COLUMN_NAME = 'ENVIADA_EN';
