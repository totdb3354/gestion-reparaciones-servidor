-- ══════════════════════════════════════════════════════════════════════════════
-- prueba-otros-en-bloques.sql — base de PRUEBA con datos sintéticos para la migración de la 0.9.15
-- (spec 2026-10-14 «Añadir otro» §7.1). NO toca gestion_reparaciones: crea prueba_otros_bloques.
-- Uso (mismo cliente mariadb que la migración real; «sed» cambia la base de los scripts reales):
--   1) mariadb … < sql/prueba-otros-en-bloques.sql
--   2) sed 's/gestion_reparaciones/prueba_otros_bloques/g' sql/recuento-otros-en-bloques.sql | mariadb …
--        → otros 5 (cancelado 1, en_camino 1, recibido 3), a_bloque_antiguo_existente 2, a_bloque_antiguo_vivo 0,
--          bloques_antiguos_nuevos 3, ultima_modificacion_otros 2026-10-14 11:30:00, cuatro colaciones iguales
--   3) sed 's/gestion_reparaciones/prueba_otros_bloques/g' sql/migracion-otros-en-bloques.sql | mariadb …
--        → EXISTE_COLUMNA 0, después 1; OTROS_CON_BLOQUE 0
--   4) sed 's/gestion_reparaciones/prueba_otros_bloques/g' sql/reconstruir-bloques-otros.sql | mariadb …
--        → 0, 0, 0, reconstruidos 4, ultima_modificacion_otros = antes_… = 2026-10-14 11:30:00
--      (y otra vez el mismo comando: las mismas cifras, no crea nada)
--   5) mariadb … < sql/prueba-otros-en-bloques-comprobar.sql   → todas las filas con ok = 1; borra la base de prueba
-- ══════════════════════════════════════════════════════════════════════════════
DROP DATABASE IF EXISTS prueba_otros_bloques;
CREATE DATABASE prueba_otros_bloques CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE prueba_otros_bloques;

-- Solo las tablas y columnas que tocan los scripts (con sus claves ajenas), con la forma de la 0.9.14.
CREATE TABLE Proveedor (
    ID_PROV    INT          NOT NULL,
    NOMBRE     VARCHAR(100) NOT NULL,
    PRIMARY KEY (ID_PROV)
);

CREATE TABLE Componente (
    ID_COM     INT          NOT NULL,
    TIPO       VARCHAR(100) NOT NULL UNIQUE,
    PRIMARY KEY (ID_COM)
);

CREATE TABLE Bloque_compra (
    ID_BLOQUE    INT          NOT NULL AUTO_INCREMENT,
    ID_PROV      INT          NOT NULL,
    FECHA        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    NOTA         VARCHAR(200),
    RECONSTRUIDO BOOLEAN      NOT NULL DEFAULT FALSE,
    UPDATED_AT   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (ID_BLOQUE),
    CONSTRAINT fk_bloque_proveedor FOREIGN KEY (ID_PROV) REFERENCES Proveedor (ID_PROV)
);

CREATE TABLE Compra_componente (
    ID_COMPRA            INT           NOT NULL AUTO_INCREMENT,
    ID_COM               INT           NOT NULL,
    ID_PROV              INT           NOT NULL,
    ID_BLOQUE            INT,
    CANTIDAD             INT           NOT NULL,
    FECHA_PEDIDO         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ESTADO               ENUM('pendiente','en_camino','parcial','recibido','cancelado') NOT NULL DEFAULT 'pendiente',
    UPDATED_AT           TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (ID_COMPRA),
    CONSTRAINT fk_compra_componente FOREIGN KEY (ID_COM)    REFERENCES Componente    (ID_COM),
    CONSTRAINT fk_compra_proveedor  FOREIGN KEY (ID_PROV)   REFERENCES Proveedor     (ID_PROV),
    CONSTRAINT fk_compra_bloque     FOREIGN KEY (ID_BLOQUE) REFERENCES Bloque_compra (ID_BLOQUE)
);

CREATE TABLE Compra_otro (
    ID_COMPRA_OTRO       INT           NOT NULL AUTO_INCREMENT,
    ID_PROV              INT           NOT NULL,
    CONCEPTO             VARCHAR(255)  NOT NULL,
    CANTIDAD             INT           NOT NULL,
    FECHA_PEDIDO         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ESTADO               ENUM('pendiente','en_camino','parcial','recibido','cancelado') NOT NULL DEFAULT 'pendiente',
    UPDATED_AT           TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (ID_COMPRA_OTRO),
    CONSTRAINT fk_compra_otro_proveedor FOREIGN KEY (ID_PROV) REFERENCES Proveedor (ID_PROV)
);

INSERT INTO Proveedor (ID_PROV, NOMBRE) VALUES (1, 'Proveedor A'), (2, 'Proveedor B');
INSERT INTO Componente (ID_COM, TIPO) VALUES (1, 'lcd-x-negro');

-- Bloque 1: antiguo (0.9.9) del proveedor A, día 2026-07-01, con una pieza recibida.
-- Bloque 2: actual y abierto del proveedor A, día 2026-10-14, con una pieza pendiente. NO se toca.
INSERT INTO Bloque_compra (ID_BLOQUE, ID_PROV, FECHA, RECONSTRUIDO, UPDATED_AT) VALUES
    (1, 1, '2026-07-01 09:00:00', TRUE,  '2026-07-01 09:00:00'),
    (2, 1, '2026-10-14 10:00:00', FALSE, '2026-10-14 10:00:00');
INSERT INTO Compra_componente (ID_COMPRA, ID_COM, ID_PROV, ID_BLOQUE, CANTIDAD, FECHA_PEDIDO, ESTADO, UPDATED_AT) VALUES
    (1, 1, 1, 1, 2, '2026-07-01 09:00:00', 'recibido',  '2026-07-03 12:00:00'),
    (2, 1, 1, 2, 1, '2026-10-14 10:00:00', 'pendiente', '2026-10-14 10:00:00');

-- Otros, sin bloque como en la 0.9.14:
--   10 y 11: proveedor A, 2026-07-01 → al bloque antiguo 1.
--   12: proveedor B, 2026-07-01 → bloque antiguo nuevo (B, 01/07).
--   13: proveedor A, 2026-08-05 → bloque antiguo nuevo (A, 05/08).
--   14: proveedor A, 2026-10-14, el día del bloque actual 2 → bloque antiguo nuevo (A, 14/10), NO al 2.
INSERT INTO Compra_otro (ID_COMPRA_OTRO, ID_PROV, CONCEPTO, CANTIDAD, FECHA_PEDIDO, ESTADO, UPDATED_AT) VALUES
    (10, 1, 'Cinta de embalar',      2, '2026-07-01 11:00:00', 'recibido',  '2026-07-02 10:00:00'),
    (11, 1, 'Bolsas antiestaticas',  5, '2026-07-01 15:00:00', 'recibido',  '2026-07-02 10:05:00'),
    (12, 2, 'Tapa trasera modelo x', 1, '2026-07-01 12:00:00', 'recibido',  '2026-07-04 09:00:00'),
    (13, 1, 'Botonera modelo y',     3, '2026-08-05 10:00:00', 'cancelado', '2026-08-06 10:00:00'),
    (14, 1, 'Pegamento B-7000',      1, '2026-10-14 11:00:00', 'en_camino', '2026-10-14 11:30:00');
