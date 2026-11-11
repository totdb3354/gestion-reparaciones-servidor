-- ══════════════════════════════════════════════════════════════════════════════
-- preprod-pedidos-etiqueta.sql: SOLO PARA PREPRODUCCION. NO es para produccion.
-- En preprod la tabla Pedido_fila ya existe (con CERRADA_EN, ENVIO_FECHA, TRANSPORTISTA, SEGUIMIENTO y REABIERTA_EN, de
-- sql/preprod-pedidos-envio.sql) pero sin ENVIO_ID ni IMPRESA_EN. En produccion la tabla aun no existe: alli la migracion
-- (sql/migracion-pedidos-tabla.sql) ya trae las dos columnas y este fichero no se aplica nunca.
-- (spec docs/superpowers/specs/2026-11-10-pedidos-tabla-design.md, seccion 6 bis).
-- La aplica el usuario a mano en la VM de preprod, con vista previa, ANTES de desplegar el servidor.
-- Idempotente: solo anade lo que falta (si ya estan las dos, no hace nada y se puede relanzar).
-- 1) Vista previa: YA_ESTAN debe dar 0 (primera vez) o 2 (ya aplicado).
-- 2) Anade ENVIO_ID e IMPRESA_EN detras de SEGUIMIENTO; si solo hay una de las dos, anade la que falta.
-- 3) Comprobacion final: NUEVAS debe dar 2.
-- Vuelta atras: el servidor anterior no las lee; bastaria con ignorarlas (o ALTER TABLE ... DROP COLUMN ENVIO_ID, IMPRESA_EN).
-- ══════════════════════════════════════════════════════════════════════════════
USE gestion_reparaciones;
SET NAMES utf8mb4;

-- Vista previa (no modifica nada)
SELECT COUNT(*) AS YA_ESTAN FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA = 'gestion_reparaciones' AND TABLE_NAME = 'Pedido_fila' AND COLUMN_NAME IN ('ENVIO_ID', 'IMPRESA_EN');

SET SESSION lock_wait_timeout = 10;

-- MariaDB: ADD COLUMN IF NOT EXISTS hace idempotente cada columna.
ALTER TABLE Pedido_fila
    ADD COLUMN IF NOT EXISTS ENVIO_ID   BIGINT   NULL COMMENT 'Id del envio en ShippyPro' AFTER SEGUIMIENTO,
    ADD COLUMN IF NOT EXISTS IMPRESA_EN DATETIME NULL COMMENT 'Cuando se imprimio la etiqueta segun ShippyPro (hora de Madrid); NULL = sin imprimir' AFTER ENVIO_ID;

-- Comprobacion final (no modifica nada): NUEVAS = 2; si no, NO desplegar el servidor.
SELECT COUNT(*) AS NUEVAS FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA = 'gestion_reparaciones' AND TABLE_NAME = 'Pedido_fila' AND COLUMN_NAME IN ('ENVIO_ID', 'IMPRESA_EN');
