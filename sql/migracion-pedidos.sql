-- ══════════════════════════════════════════════════════════════════════════════
-- migracion-pedidos.sql — 0.9.26, pieza 2 del módulo de teléfonos: «Pedidos» (1ª entrega)
-- (spec docs/superpowers/specs/2026-11-04-pieza2-pedidos-design.md §5, raíz).
-- La aplica el usuario a mano en la VM, con vista previa, ANTES de desplegar el servidor 0.9.26.
-- Aditiva: el servidor anterior no lee las tablas nuevas ni las columnas nuevas de Nsys_producto.
-- NO idempotente (relanzar = error de tabla o columna existente).
-- 1) Vista previa: YA_EXISTEN y COLUMNAS_YA deben dar 0.
-- 2) Tres tablas nuevas y tres columnas en Nsys_producto.
-- 3) Comprobación final: CREADAS debe dar 3 y COLUMNAS, 3.
-- Vuelta atrás: el servidor anterior funciona con todo puesto; no hace falta quitar nada.
-- ══════════════════════════════════════════════════════════════════════════════
USE gestion_reparaciones;
SET NAMES utf8mb4;

-- Vista previa (no modifica nada): las dos deben dar 0
SELECT COUNT(*) AS YA_EXISTEN FROM information_schema.TABLES
 WHERE TABLE_SCHEMA = 'gestion_reparaciones' AND TABLE_NAME IN ('Pedido', 'Pedido_unidad', 'Pedido_lectura');
SELECT COUNT(*) AS COLUMNAS_YA FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA = 'gestion_reparaciones' AND TABLE_NAME = 'Nsys_producto'
   AND COLUMN_NAME IN ('SKU_AMAZON_ESIM', 'SKU_PARTICULARES', 'SKU_BACKIPHONE');

-- Un pedido de un cliente (spec §5.1): de ShippyPro (Amazon y webs) o del Simulador. Los cerrados no se borran.
CREATE TABLE Pedido (
    ID_PEDIDO     INT          NOT NULL AUTO_INCREMENT,
    CANAL         VARCHAR(20)  NOT NULL COMMENT 'AMAZON, AMAZON_STOCKROOM, DISTRIBUIDORES, PARTICULARES, BACKIPHONE o SIMULADOR',
    NUMERO        VARCHAR(60)  NOT NULL COMMENT 'Nº de pedido de ShippyPro u Order id del Simulador',
    CLIENTE       VARCHAR(150) NULL,
    PAIS          VARCHAR(10)  NULL,
    FECHA         DATETIME     NOT NULL COMMENT 'Hora de Madrid',
    FUENTE        VARCHAR(12)  NOT NULL COMMENT 'SHIPPYPRO o SIMULADOR',
    VISTO_PRIMERO DATETIME     NOT NULL,
    VISTO_ULTIMO  DATETIME     NOT NULL,
    CERRADO_EN    DATETIME     NULL COMMENT 'NULL = abierto',
    MOTIVO_CIERRE VARCHAR(20)  NULL COMMENT 'YA_NO_PENDIENTE, TODO_VENDIDO, ABONADO o FUERA_DE_VENTANA',
    PRIMARY KEY (ID_PEDIDO),
    UNIQUE KEY uq_pedido_canal_numero (CANAL, NUMERO),
    KEY idx_pedido_cerrado (CERRADO_EN)
);

-- Una unidad de un pedido (spec §5.2): (pedido, posición) no cambia entre lecturas.
CREATE TABLE Pedido_unidad (
    ID_UNIDAD           INT         NOT NULL AUTO_INCREMENT,
    ID_PEDIDO           INT         NOT NULL,
    POSICION            INT         NOT NULL,
    SKU                 VARCHAR(60) NOT NULL COMMENT 'El SKU tal como lo da la fuente',
    IMEI                VARCHAR(40) NULL,
    IMEI_FUENTE         VARCHAR(14) NULL COMMENT 'PEDIDOS_2026 o SIMULADOR',
    IMEI_REF            INT         NULL COMMENT 'Fila de la hoja',
    IMEI_TECLEADO_EN    DATETIME    NULL,
    SIN_PEDIDO_CONCRETO BOOLEAN     NOT NULL DEFAULT FALSE COMMENT 'Amazon: fila «AMZ» o «FNSK/FSNK» de PEDIDOS 2026 sin «Pedido Amazon»',
    OTRO_ORDER_ID       VARCHAR(60) NULL COMMENT 'Simulador: otro Order id del mismo IMEI',
    CERRADA_EN          DATETIME    NULL,
    MOTIVO_CIERRE       VARCHAR(12) NULL COMMENT 'RELEASED, ABONO o REVENDIDO',
    PRIMARY KEY (ID_UNIDAD),
    UNIQUE KEY uq_unidad_posicion (ID_PEDIDO, POSICION),
    KEY idx_unidad_imei (IMEI),
    CONSTRAINT fk_unidad_pedido FOREIGN KEY (ID_PEDIDO) REFERENCES Pedido (ID_PEDIDO) ON DELETE CASCADE
);

-- Una lectura de una fuente (spec §5.3), para la píldora. Se borran a los 30 días.
CREATE TABLE Pedido_lectura (
    ID_LECTURA       INT           NOT NULL AUTO_INCREMENT,
    FUENTE           VARCHAR(14)   NOT NULL COMMENT 'SHIPPYPRO, PEDIDOS_2026 o SIMULADOR',
    INICIO           DATETIME      NOT NULL,
    FIN              DATETIME      NULL,
    RESULTADO        VARCHAR(12)   NOT NULL DEFAULT 'EN_CURSO' COMMENT 'EN_CURSO, OK, ERROR, RECHAZADA o INCOMPLETA',
    FILAS            INT           NOT NULL DEFAULT 0,
    SKU_DESCONOCIDOS VARCHAR(500)  NULL,
    MENSAJE          VARCHAR(1000) NULL,
    PRIMARY KEY (ID_LECTURA),
    KEY idx_lectura_fuente (FUENTE, ID_LECTURA),
    KEY idx_lectura_inicio (INICIO)
);

-- Los SKU de los canales que faltaban en el maestro (spec §5.4).
ALTER TABLE Nsys_producto
    ADD COLUMN SKU_AMAZON_ESIM  VARCHAR(60) NULL COMMENT '«SKU (Amazon) con eSIM» del maestro (0.9.26)',
    ADD COLUMN SKU_PARTICULARES VARCHAR(60) NULL COMMENT '«SKU (stock fonestore.es) Conjunto» (0.9.26)',
    ADD COLUMN SKU_BACKIPHONE   VARCHAR(60) NULL COMMENT '«SKU BackiPhone» (0.9.26)';

-- Comprobación final (no modifica nada): CREADAS = 3 y COLUMNAS = 3; si no, NO desplegar el servidor 0.9.26.
SELECT COUNT(*) AS CREADAS FROM information_schema.TABLES
 WHERE TABLE_SCHEMA = 'gestion_reparaciones' AND TABLE_NAME IN ('Pedido', 'Pedido_unidad', 'Pedido_lectura');
SELECT COUNT(*) AS COLUMNAS FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA = 'gestion_reparaciones' AND TABLE_NAME = 'Nsys_producto'
   AND COLUMN_NAME IN ('SKU_AMAZON_ESIM', 'SKU_PARTICULARES', 'SKU_BACKIPHONE');
