-- ══════════════════════════════════════════════════════════════════════════════
-- migracion-pedidos-tabla.sql: «Pedidos como la hoja» (el ERP sustituye a la hoja de pedidos)
-- (spec docs/superpowers/specs/2026-11-10-pedidos-tabla-design.md).
-- La aplica el usuario a mano en la VM, con vista previa, ANTES de desplegar el servidor con el modulo.
-- Aditiva: el servidor anterior no lee las tablas nuevas ni las columnas nuevas de Nsys_producto.
-- NO idempotente (relanzar = error de tabla o columna existente).
-- 1) Vista previa: YA_EXISTEN y COLUMNAS_YA deben dar 0.
-- 2) Tres tablas nuevas y tres columnas en Nsys_producto.
-- 3) Comprobacion final: CREADAS debe dar 3 y COLUMNAS, 3.
-- Vuelta atras: el servidor anterior funciona con todo puesto; no hace falta quitar nada.
-- ══════════════════════════════════════════════════════════════════════════════
USE gestion_reparaciones;
SET NAMES utf8mb4;

-- Vista previa (no modifica nada): las dos deben dar 0
SELECT COUNT(*) AS YA_EXISTEN FROM information_schema.TABLES
 WHERE TABLE_SCHEMA = 'gestion_reparaciones' AND TABLE_NAME IN ('Pedido_fila', 'Pedido_fila_cambio', 'Pedido_lectura');
SELECT COUNT(*) AS COLUMNAS_YA FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA = 'gestion_reparaciones' AND TABLE_NAME = 'Nsys_producto'
   AND COLUMN_NAME IN ('SKU_AMAZON_ESIM', 'SKU_PARTICULARES', 'SKU_BACKIPHONE');

CREATE TABLE Pedido_fila (
    ID_FILA      INT          NOT NULL AUTO_INCREMENT,
    FECHA        DATE         NOT NULL COMMENT 'El dia en que el ERP ve el pedido (hora de Madrid)',
    PLATAFORMA   VARCHAR(150) NOT NULL COMMENT 'AMZ, FNSK o el cliente de la web; en el historico, lo de la hoja',
    SKU          VARCHAR(60)  NULL,
    IMEI         VARCHAR(60)  NULL COMMENT 'En el historico, el texto de la hoja tal cual',
    PEDIDO       VARCHAR(60)  NULL,
    FECHA_COMPRA DATE         NULL,
    ORIGEN       VARCHAR(10)  NOT NULL COMMENT 'SHIPPYPRO o HISTORICO',
    CANAL        VARCHAR(20)  NULL COMMENT 'AMAZON, AMAZON_STOCKROOM, DISTRIBUIDORES, PARTICULARES o BACKIPHONE',
    POSICION     INT          NULL,
    ENVIADA_EN   DATETIME     NULL COMMENT 'NULL = viva',
    PRIMARY KEY (ID_FILA),
    UNIQUE KEY uq_fila_pedido (CANAL, PEDIDO, POSICION),
    KEY idx_fila_fecha (FECHA, ID_FILA),
    KEY idx_fila_viva (ENVIADA_EN),
    KEY idx_fila_imei (IMEI),
    KEY idx_fila_pedido (PEDIDO)
);
CREATE TABLE Pedido_fila_cambio (
    ID_CAMBIO     INT         NOT NULL AUTO_INCREMENT,
    ID_FILA       INT         NOT NULL,
    ID_USU        INT         NULL,
    FECHA         DATETIME    NOT NULL,
    IMEI_ANTERIOR VARCHAR(60) NULL,
    IMEI_NUEVO    VARCHAR(60) NULL,
    PRIMARY KEY (ID_CAMBIO),
    KEY idx_cambio_fila (ID_FILA),
    CONSTRAINT fk_cambio_fila FOREIGN KEY (ID_FILA) REFERENCES Pedido_fila (ID_FILA) ON DELETE CASCADE,
    CONSTRAINT fk_cambio_usuario FOREIGN KEY (ID_USU) REFERENCES Usuario (ID_USU) ON DELETE SET NULL
);
CREATE TABLE Pedido_lectura (
    ID_LECTURA       INT           NOT NULL AUTO_INCREMENT,
    INICIO           DATETIME      NOT NULL,
    FIN              DATETIME      NULL,
    RESULTADO        VARCHAR(12)   NOT NULL DEFAULT 'EN_CURSO' COMMENT 'EN_CURSO, OK, ERROR o RECHAZADA',
    FILAS            INT           NOT NULL DEFAULT 0,
    SKU_DESCONOCIDOS VARCHAR(500)  NULL,
    MENSAJE          VARCHAR(1000) NULL,
    PRIMARY KEY (ID_LECTURA),
    KEY idx_lectura_inicio (INICIO)
);

-- Si una importacion del maestro tiene la tabla en uso, no esperar mas de 10 s por el bloqueo de metadatos.
SET SESSION lock_wait_timeout = 10;

ALTER TABLE Nsys_producto
    ADD COLUMN SKU_AMAZON_ESIM  VARCHAR(60) NULL,
    ADD COLUMN SKU_PARTICULARES VARCHAR(60) NULL,
    ADD COLUMN SKU_BACKIPHONE   VARCHAR(60) NULL;

-- Comprobacion final (no modifica nada): CREADAS = 3 y COLUMNAS = 3; si no, NO desplegar el servidor.
SELECT COUNT(*) AS CREADAS FROM information_schema.TABLES
 WHERE TABLE_SCHEMA = 'gestion_reparaciones' AND TABLE_NAME IN ('Pedido_fila', 'Pedido_fila_cambio', 'Pedido_lectura');
SELECT COUNT(*) AS COLUMNAS FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA = 'gestion_reparaciones' AND TABLE_NAME = 'Nsys_producto'
   AND COLUMN_NAME IN ('SKU_AMAZON_ESIM', 'SKU_PARTICULARES', 'SKU_BACKIPHONE');
