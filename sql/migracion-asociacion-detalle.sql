-- 0.9.45 (spec 2026-11-24-asociacion-libro-manda-design.md): «para revisar» dice que es el movil cuando no cuadra con el pedido.
-- Aditiva e idempotente: la web y el servidor anteriores no leen la columna. Vuelta atras: ALTER TABLE Pedido_asociacion_revisar DROP COLUMN DETALLE;
ALTER TABLE Pedido_asociacion_revisar ADD COLUMN IF NOT EXISTS DETALLE VARCHAR(250) NULL
    COMMENT 'OTRO_MODELO: «el pedido es X y el movil es Y»; nulo en los demas' AFTER MOTIVO;
SELECT COLUMN_NAME FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'Pedido_asociacion_revisar' AND COLUMN_NAME = 'DETALLE';
