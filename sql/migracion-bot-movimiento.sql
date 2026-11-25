-- Spec 2026-11-24-bot-devoluciones §4.1: lo que el bot de NSYS informa que ha movido. Solo se añade; nunca se cambia ni se borra.
-- Aditiva e idempotente: el servidor anterior no la lee. Vuelta atras: DROP TABLE Bot_movimiento; (pierde lo informado) y DROP INDEX idx_cambio_imei_anterior / idx_cambio_imei_nuevo ON Pedido_fila_cambio;.
CREATE TABLE IF NOT EXISTS Bot_movimiento (
    ID           BIGINT      NOT NULL AUTO_INCREMENT,
    RUN_ID       VARCHAR(40) NOT NULL COMMENT 'La vuelta del bot',
    IMEI         VARCHAR(40) NOT NULL,
    ACCION       VARCHAR(20) NOT NULL COMMENT 'DESCONTADO o DEVUELTO',
    RESULTADO    VARCHAR(20) NOT NULL COMMENT 'OK, NO_APARECE o ERROR',
    ORIGEN_NSYS  VARCHAR(80) NULL COMMENT 'Location que leyo el bot antes de mover, tal cual',
    DESTINO_NSYS VARCHAR(80) NULL,
    MOTIVO       VARCHAR(30) NULL COMMENT 'En DEVUELTO: el motivo de la lista (IMEI_CORREGIDO, RESERVA_LIBERADA)',
    FECHA        DATETIME    NOT NULL COMMENT 'Hora de Madrid del bot',
    RECIBIDO     DATETIME    NOT NULL COMMENT 'Hora de Madrid en que lo guardo el ERP',
    PRIMARY KEY (ID),
    UNIQUE KEY uq_bot_movimiento (RUN_ID, IMEI, ACCION),
    KEY idx_bot_movimiento_imei (IMEI, ACCION, FECHA)
);
-- Indices de Pedido_fila_cambio para las busquedas por IMEI de la lista de devoluciones (en linea y aditivos).
CREATE INDEX IF NOT EXISTS idx_cambio_imei_anterior ON Pedido_fila_cambio (IMEI_ANTERIOR);
CREATE INDEX IF NOT EXISTS idx_cambio_imei_nuevo ON Pedido_fila_cambio (IMEI_NUEVO);
-- Comprobacion: COLUMNAS = 10 e INDICES = 2.
SELECT COUNT(*) AS COLUMNAS FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'Bot_movimiento';
SELECT COUNT(DISTINCT INDEX_NAME) AS INDICES FROM information_schema.STATISTICS
 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'Pedido_fila_cambio'
   AND INDEX_NAME IN ('idx_cambio_imei_anterior', 'idx_cambio_imei_nuevo');
