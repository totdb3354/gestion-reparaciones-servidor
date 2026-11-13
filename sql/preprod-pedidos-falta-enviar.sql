-- ══════════════════════════════════════════════════════════════════════════════
-- preprod-pedidos-falta-enviar.sql: SOLO PARA PREPRODUCCION. NO es para produccion.
-- En preprod las tablas Pedido_fila y Pedido_fila_cambio ya existen, sin las columnas de los envios parciales de los pedidos de web:
--   Pedido_fila: FALTA_ENVIAR_EN (cuando se marco que el movil falta por enviar), FALTA_ENVIAR_USU (quien) y SINCRONIZADA_EN (el aviso del
--   plugin de la web); Pedido_fila_cambio: TIPO ('IMEI', 'FALTA_ENVIAR' o 'YA_ENVIADO'; los apuntes que ya hay quedan como 'IMEI').
-- En produccion las tablas aun no existen: alli la migracion (sql/migracion-pedidos-tabla.sql) ya las trae y este fichero no se aplica nunca.
-- La aplica el usuario a mano en la VM de preprod, con vista previa, ANTES de desplegar el servidor.
-- Aditiva e idempotente: solo anade lo que falta (si ya esta todo, no hace nada y se puede relanzar). El servidor anterior no lee las
-- columnas nuevas: funciona con todo puesto.
-- 1) Vista previa: YA_ESTAN debe dar 0 (primera vez) o 4 (ya aplicado).
-- 2) Anade las columnas (y la clave foranea de FALTA_ENVIAR_USU) que falten.
-- 3) Comprobacion final: NUEVAS debe dar 4 y LA_FK, 1.
-- Vuelta atras: bastaria con ignorarlas (o ALTER TABLE ... DROP COLUMN; antes, DROP FOREIGN KEY fk_fila_falta_usuario).
-- ══════════════════════════════════════════════════════════════════════════════
USE gestion_reparaciones;
SET NAMES utf8mb4;

-- Vista previa (no modifica nada)
SELECT COUNT(*) AS YA_ESTAN FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA = 'gestion_reparaciones'
   AND ((TABLE_NAME = 'Pedido_fila' AND COLUMN_NAME IN ('FALTA_ENVIAR_EN', 'FALTA_ENVIAR_USU', 'SINCRONIZADA_EN'))
     OR (TABLE_NAME = 'Pedido_fila_cambio' AND COLUMN_NAME = 'TIPO'));

SET SESSION lock_wait_timeout = 10;

-- MariaDB: ADD COLUMN IF NOT EXISTS y ADD CONSTRAINT ... FOREIGN KEY IF NOT EXISTS hacen idempotente cada paso
-- (MariaDB no admite «ADD CONSTRAINT IF NOT EXISTS ... FOREIGN KEY»: da error 1064).
ALTER TABLE Pedido_fila
    ADD COLUMN IF NOT EXISTS FALTA_ENVIAR_EN  DATETIME NULL COMMENT 'Cuando una persona marco que el movil falta por enviar (envio parcial de un pedido de web); NULL = sin marca' AFTER REABIERTA_EN,
    ADD COLUMN IF NOT EXISTS FALTA_ENVIAR_USU INT      NULL COMMENT 'Quien puso la marca' AFTER FALTA_ENVIAR_EN,
    ADD COLUMN IF NOT EXISTS SINCRONIZADA_EN  DATETIME NULL COMMENT 'Ultima vez que el plugin de la web aviso de que el pedido ya lleva este movil; NULL = nunca' AFTER FALTA_ENVIAR_USU;
ALTER TABLE Pedido_fila
    ADD CONSTRAINT fk_fila_falta_usuario FOREIGN KEY IF NOT EXISTS (FALTA_ENVIAR_USU) REFERENCES Usuario (ID_USU) ON DELETE SET NULL;
ALTER TABLE Pedido_fila_cambio
    ADD COLUMN IF NOT EXISTS TIPO VARCHAR(20) NOT NULL DEFAULT 'IMEI' COMMENT 'IMEI, FALTA_ENVIAR (se marco que el movil falta por enviar) o YA_ENVIADO (se quito la marca)' AFTER IMEI_NUEVO;

-- Comprobacion final (no modifica nada): NUEVAS = 4 y LA_FK = 1; si no, NO desplegar el servidor.
SELECT COUNT(*) AS NUEVAS FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA = 'gestion_reparaciones'
   AND ((TABLE_NAME = 'Pedido_fila' AND COLUMN_NAME IN ('FALTA_ENVIAR_EN', 'FALTA_ENVIAR_USU', 'SINCRONIZADA_EN'))
     OR (TABLE_NAME = 'Pedido_fila_cambio' AND COLUMN_NAME = 'TIPO'));
SELECT COUNT(*) AS LA_FK FROM information_schema.TABLE_CONSTRAINTS
 WHERE CONSTRAINT_SCHEMA = 'gestion_reparaciones' AND TABLE_NAME = 'Pedido_fila' AND CONSTRAINT_NAME = 'fk_fila_falta_usuario';
