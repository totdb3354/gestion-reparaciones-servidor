-- ══════════════════════════════════════════════════════════════════════════════
-- migracion-pedidos-tabla.sql: «Pedidos como la hoja» (el ERP sustituye a la hoja de pedidos)
-- (spec docs/superpowers/specs/2026-11-10-pedidos-tabla-design.md).
-- La aplica el usuario a mano en la VM, con vista previa, ANTES de desplegar el servidor con el modulo.
-- Aditiva: el servidor anterior no lee las tablas nuevas ni las columnas nuevas de Nsys_producto.
-- Idempotente (MariaDB 11): se puede relanzar entero si algo falla a medias (CREATE TABLE IF NOT EXISTS, ADD COLUMN IF NOT EXISTS;
-- los indices van dentro de los CREATE TABLE).
-- 1) Vista previa: en la primera vez YA_EXISTEN y COLUMNAS_YA deben dar 0; al relanzar dan lo que la vez anterior ya dejo hecho
--    (hasta 5 y 3).
-- 2) Cinco tablas nuevas y tres columnas en Nsys_producto. Pedido_fila ya trae FALTA_ENVIAR_EN, FALTA_ENVIAR_USU y SINCRONIZADA_EN
--    (envios parciales de pedidos de web), MARCA, MARCA_NOTA, MARCA_EN y MARCA_USU (marcas de la fila), ASOCIADA_EN y ASOCIADA_ORIGEN
--    (asociacion de IMEI y pedido en Amazon) y Pedido_fila_cambio, TIPO (IMEI, FALTA_ENVIAR, YA_ENVIADO, MARCA, MARCA_QUITADA, ASOCIADA o
--    IMEI_INTERCAMBIADO) y DETALLE; mas Pedido_asociacion_revisar y Pedido_asociacion_pasada. En una base que ya tiene las tablas se aplican
--    sql/migracion-pedidos-marcas.sql y sql/migracion-pedidos-asociacion.sql.
-- 3) Comprobacion final: CREADAS debe dar 5 y COLUMNAS, 3 (tambien en una segunda vez).
-- No lanzarla mientras corre la copia nocturna.
-- Vuelta atras: el servidor anterior funciona con todo puesto; no hace falta quitar nada.
-- ══════════════════════════════════════════════════════════════════════════════
USE gestion_reparaciones;
SET NAMES utf8mb4;
-- Si algo tiene en uso Usuario (la FK de Pedido_fila_cambio) o Nsys_producto (una importacion del maestro), no esperar mas
-- de 10 s por el bloqueo de metadatos: falla y se relanza (el script se puede repetir).
SET SESSION lock_wait_timeout = 10;

-- Vista previa (no modifica nada): la primera vez las dos dan 0; al relanzar, lo ya creado
SELECT COUNT(*) AS YA_EXISTEN FROM information_schema.TABLES
 WHERE TABLE_SCHEMA = 'gestion_reparaciones'
   AND TABLE_NAME IN ('Pedido_fila', 'Pedido_fila_cambio', 'Pedido_lectura', 'Pedido_asociacion_revisar', 'Pedido_asociacion_pasada');
SELECT COUNT(*) AS COLUMNAS_YA FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA = 'gestion_reparaciones' AND TABLE_NAME = 'Nsys_producto'
   AND COLUMN_NAME IN ('SKU_AMAZON_ESIM', 'SKU_PARTICULARES', 'SKU_BACKIPHONE');

CREATE TABLE IF NOT EXISTS Pedido_fila (
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
    CERRADA_EN   DATETIME     NULL COMMENT 'Cuando dejo de estar pendiente; NULL = pendiente',
    ENVIO_FECHA  DATETIME     NULL COMMENT 'Cuando ShippyPro creo el envio (hora de Madrid); NULL = sin envio conocido',
    TRANSPORTISTA VARCHAR(40) NULL,
    SEGUIMIENTO  VARCHAR(60)  NULL,
    ENVIO_ID     BIGINT       NULL COMMENT 'Id del envio en ShippyPro',
    IMPRESA_EN   DATETIME     NULL COMMENT 'Cuando se imprimio la etiqueta segun ShippyPro (hora de Madrid); NULL = sin imprimir',
    REABIERTA_EN DATETIME     NULL COMMENT 'Cuando el pedido volvio a estar pendiente',
    FALTA_ENVIAR_EN  DATETIME NULL COMMENT 'Cuando una persona marco que el movil falta por enviar (envio parcial de un pedido de web); NULL = sin marca',
    FALTA_ENVIAR_USU INT      NULL COMMENT 'Quien puso la marca',
    SINCRONIZADA_EN  DATETIME NULL COMMENT 'Ultima vez que el plugin de la web aviso de que el pedido ya lleva este movil; NULL = nunca',
    MARCA        VARCHAR(20)  NULL COMMENT 'ESPERA_STOCK, EN_REVISION, PENDIENTE_COLOR o CANCELADO; NULL = sin marca',
    MARCA_NOTA   VARCHAR(200) NULL COMMENT 'Nota libre de la marca',
    MARCA_EN     DATETIME     NULL COMMENT 'Cuando se puso la marca',
    MARCA_USU    INT          NULL COMMENT 'Quien puso la marca',
    ASOCIADA_EN     DATETIME    NULL COMMENT 'Amazon: cuando se confirmo el pedido del IMEI (libro de ventas o historico); NULL = sin asociar',
    ASOCIADA_ORIGEN VARCHAR(20) NULL COMMENT 'LIBRO (libro de ventas) o HISTORICO (carga del historico de la hoja); NULL = sin asociar',
    PRIMARY KEY (ID_FILA),
    UNIQUE KEY uq_fila_pedido (CANAL, PEDIDO, POSICION),
    KEY idx_fila_fecha (FECHA, ID_FILA),
    KEY idx_fila_viva (CERRADA_EN),
    KEY idx_fila_imei (IMEI),
    KEY idx_fila_pedido (PEDIDO),
    CONSTRAINT fk_fila_falta_usuario FOREIGN KEY (FALTA_ENVIAR_USU) REFERENCES Usuario (ID_USU) ON DELETE SET NULL,
    CONSTRAINT fk_fila_marca_usuario FOREIGN KEY (MARCA_USU) REFERENCES Usuario (ID_USU) ON DELETE SET NULL
);
CREATE TABLE IF NOT EXISTS Pedido_fila_cambio (
    ID_CAMBIO     INT         NOT NULL AUTO_INCREMENT,
    ID_FILA       INT         NOT NULL,
    ID_USU        INT         NULL,
    FECHA         DATETIME    NOT NULL,
    IMEI_ANTERIOR VARCHAR(60) NULL,
    IMEI_NUEVO    VARCHAR(60) NULL,
    TIPO          VARCHAR(20) NOT NULL DEFAULT 'IMEI' COMMENT 'IMEI, FALTA_ENVIAR (se marco que el movil falta por enviar), YA_ENVIADO (se quito esa marca), MARCA, MARCA_QUITADA, ASOCIADA o IMEI_INTERCAMBIADO',
    DETALLE       VARCHAR(250) NULL COMMENT 'Marca y nota en MARCA y MARCA_QUITADA; LIBRO o HISTORICO en ASOCIADA; con la fila <id> en IMEI_INTERCAMBIADO',
    PRIMARY KEY (ID_CAMBIO),
    KEY idx_cambio_fila (ID_FILA),
    CONSTRAINT fk_cambio_fila FOREIGN KEY (ID_FILA) REFERENCES Pedido_fila (ID_FILA) ON DELETE CASCADE,
    CONSTRAINT fk_cambio_usuario FOREIGN KEY (ID_USU) REFERENCES Usuario (ID_USU) ON DELETE SET NULL
);
CREATE TABLE IF NOT EXISTS Pedido_asociacion_revisar (
    ID       INT         NOT NULL AUTO_INCREMENT,
    CANAL    VARCHAR(30) NOT NULL COMMENT 'AMAZON o AMAZON_STOCKROOM',
    PEDIDO   VARCHAR(60) NOT NULL COMMENT 'El numero del libro, normalizado como lo guarda la lectura de ShippyPro',
    IMEI     VARCHAR(20) NOT NULL,
    MOTIVO   VARCHAR(30) NOT NULL COMMENT 'SIN_HUECO, YA_SALIO, PEDIDO_AJENO o CANCELADO',
    VISTO_EN DATETIME    NOT NULL COMMENT 'La pasada que lo dejo para revisar (hora de Madrid)',
    PRIMARY KEY (ID),
    KEY idx_asociacion_revisar_canal (CANAL)
);
CREATE TABLE IF NOT EXISTS Pedido_asociacion_pasada (
    CANAL          VARCHAR(30) NOT NULL COMMENT 'AMAZON o AMAZON_STOCKROOM',
    CUANDO         DATETIME    NOT NULL COMMENT 'La ultima pasada de ese canal (hora de Madrid)',
    CONFIRMADAS    INT         NOT NULL,
    INTERCAMBIADAS INT         NOT NULL,
    PUESTAS        INT         NOT NULL,
    YA_ESTABAN     INT         NOT NULL,
    PARA_REVISAR   INT         NOT NULL,
    DESCARTADAS    INT         NOT NULL,
    LIBRO_MODIFICADO DATETIME   NULL COMMENT 'Lector de los libros: fecha de modificacion en Drive del libro de su ultima lectura buena, en hora de Madrid; NULL = nunca leido',
    ERROR          VARCHAR(500) NULL COMMENT 'Lector de los libros: el fallo de su ultima vuelta; NULL si la ultima fue buena',
    ERROR_EN       DATETIME    NULL COMMENT 'Cuando fallo esa vuelta (hora de Madrid)',
    PRIMARY KEY (CANAL)
);
CREATE TABLE IF NOT EXISTS Pedido_lectura (
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

ALTER TABLE Nsys_producto
    ADD COLUMN IF NOT EXISTS SKU_AMAZON_ESIM  VARCHAR(60) NULL,
    ADD COLUMN IF NOT EXISTS SKU_PARTICULARES VARCHAR(60) NULL,
    ADD COLUMN IF NOT EXISTS SKU_BACKIPHONE   VARCHAR(60) NULL;

-- Comprobacion final (no modifica nada): CREADAS = 5 y COLUMNAS = 3; si no, NO desplegar el servidor.
SELECT COUNT(*) AS CREADAS FROM information_schema.TABLES
 WHERE TABLE_SCHEMA = 'gestion_reparaciones'
   AND TABLE_NAME IN ('Pedido_fila', 'Pedido_fila_cambio', 'Pedido_lectura', 'Pedido_asociacion_revisar', 'Pedido_asociacion_pasada');
SELECT COUNT(*) AS COLUMNAS FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA = 'gestion_reparaciones' AND TABLE_NAME = 'Nsys_producto'
   AND COLUMN_NAME IN ('SKU_AMAZON_ESIM', 'SKU_PARTICULARES', 'SKU_BACKIPHONE');
