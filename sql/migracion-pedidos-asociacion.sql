-- ══════════════════════════════════════════════════════════════════════════════
-- migracion-pedidos-asociacion.sql: asociacion de IMEI y pedido en Amazon («plan B»): las filas de Amazon y Amazon Stockroom salen
-- «sin asociar» hasta que la asociacion IMEI -> pedido llega de los libros de ventas por POST /api/pedidos/asociaciones
-- (spec docs/superpowers/specs/2026-11-13-pedidos-amazon-asociacion-design.md). PRODUCCION Y PREPROD: el mismo fichero.
-- Las tablas Pedido_* ya existen, sin estas columnas ni estas tablas:
--   Pedido_fila: ASOCIADA_EN (cuando se confirmo la asociacion; NULL = sin asociar) y ASOCIADA_ORIGEN (LIBRO o HISTORICO);
--   Pedido_asociacion_revisar: lo que la ultima pasada de cada canal dejo «para revisar» (se rehace en cada pasada);
--   Pedido_asociacion_pasada: la ultima pasada de cada canal, con sus recuentos y, del lector de los libros desde Drive, LIBRO_MODIFICADO
--   (fecha de modificacion del libro de su ultima lectura buena), ERROR y ERROR_EN (el fallo de su ultima vuelta y cuando).
-- Se puede relanzar donde ya se aplico la version anterior (sin las tres columnas del lector): solo anade las tres columnas.
-- La aplica el usuario a mano en la VM, con vista previa, ANTES de desplegar el servidor.
-- Aditiva e idempotente: solo anade lo que falta (si ya esta todo, no hace nada y se puede relanzar). Sin claves foraneas. El servidor
-- anterior no lee las columnas ni las tablas nuevas: funciona con todo puesto. No lanzarla mientras corre la copia nocturna.
-- OJO: desde que se despliega el servidor nuevo, TODAS las filas de Amazon y Amazon Stockroom que ya hay salen «sin asociar» (ASOCIADA_EN
-- NULL) hasta que una pasada de los libros de ventas las asocie (o una carga del historico, que asocia las de la regla A).
-- 1) Vista previa: YA_ESTAN debe dar 0 (primera vez), 4 (aplicada la version anterior, sin las columnas del lector) o 7 (ya aplicado).
-- 2) Anade las dos columnas de Pedido_fila, crea las dos tablas que falten y anade a Pedido_asociacion_pasada las tres columnas del lector.
-- 3) Comprobacion final: NUEVAS debe dar 7; si no, NO desplegar el servidor.
-- Vuelta atras: el servidor anterior funciona con todo puesto; bastaria con ignorarlas (o ALTER TABLE Pedido_fila DROP COLUMN ASOCIADA_EN,
-- DROP COLUMN ASOCIADA_ORIGEN; DROP TABLE Pedido_asociacion_revisar, Pedido_asociacion_pasada). Las tres columnas del lector tampoco las lee
-- el servidor anterior.
-- ══════════════════════════════════════════════════════════════════════════════
USE gestion_reparaciones;
SET NAMES utf8mb4;

-- Vista previa (no modifica nada)
SELECT (SELECT COUNT(*) FROM information_schema.COLUMNS
         WHERE TABLE_SCHEMA = 'gestion_reparaciones' AND TABLE_NAME = 'Pedido_fila' AND COLUMN_NAME IN ('ASOCIADA_EN', 'ASOCIADA_ORIGEN'))
     + (SELECT COUNT(*) FROM information_schema.TABLES
         WHERE TABLE_SCHEMA = 'gestion_reparaciones' AND TABLE_NAME IN ('Pedido_asociacion_revisar', 'Pedido_asociacion_pasada'))
     + (SELECT COUNT(*) FROM information_schema.COLUMNS
         WHERE TABLE_SCHEMA = 'gestion_reparaciones' AND TABLE_NAME = 'Pedido_asociacion_pasada'
           AND COLUMN_NAME IN ('LIBRO_MODIFICADO', 'ERROR', 'ERROR_EN')) AS YA_ESTAN;

-- Si algo tiene en uso Pedido_fila (una vuelta de la lectura de ShippyPro), no esperar mas de 10 s por el bloqueo de metadatos: falla y se
-- relanza (el script se puede repetir).
SET SESSION lock_wait_timeout = 10;

-- MariaDB: ADD COLUMN IF NOT EXISTS y CREATE TABLE IF NOT EXISTS hacen idempotente cada paso.
ALTER TABLE Pedido_fila
    ADD COLUMN IF NOT EXISTS ASOCIADA_EN     DATETIME    NULL COMMENT 'Amazon: cuando se confirmo el pedido del IMEI (libro de ventas o historico); NULL = sin asociar',
    ADD COLUMN IF NOT EXISTS ASOCIADA_ORIGEN VARCHAR(20) NULL COMMENT 'LIBRO (libro de ventas) o HISTORICO (carga del historico de la hoja); NULL = sin asociar';

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
-- Donde la tabla ya existia (version anterior de esta migracion), las tres columnas del lector; si ya estan, no hace nada.
ALTER TABLE Pedido_asociacion_pasada
    ADD COLUMN IF NOT EXISTS LIBRO_MODIFICADO DATETIME   NULL COMMENT 'Lector de los libros: fecha de modificacion en Drive del libro de su ultima lectura buena, en hora de Madrid; NULL = nunca leido',
    ADD COLUMN IF NOT EXISTS ERROR          VARCHAR(500) NULL COMMENT 'Lector de los libros: el fallo de su ultima vuelta; NULL si la ultima fue buena',
    ADD COLUMN IF NOT EXISTS ERROR_EN       DATETIME    NULL COMMENT 'Cuando fallo esa vuelta (hora de Madrid)';

-- Comprobacion final (no modifica nada): NUEVAS = 7; si no, NO desplegar el servidor.
SELECT (SELECT COUNT(*) FROM information_schema.COLUMNS
         WHERE TABLE_SCHEMA = 'gestion_reparaciones' AND TABLE_NAME = 'Pedido_fila' AND COLUMN_NAME IN ('ASOCIADA_EN', 'ASOCIADA_ORIGEN'))
     + (SELECT COUNT(*) FROM information_schema.TABLES
         WHERE TABLE_SCHEMA = 'gestion_reparaciones' AND TABLE_NAME IN ('Pedido_asociacion_revisar', 'Pedido_asociacion_pasada'))
     + (SELECT COUNT(*) FROM information_schema.COLUMNS
         WHERE TABLE_SCHEMA = 'gestion_reparaciones' AND TABLE_NAME = 'Pedido_asociacion_pasada'
           AND COLUMN_NAME IN ('LIBRO_MODIFICADO', 'ERROR', 'ERROR_EN')) AS NUEVAS;
