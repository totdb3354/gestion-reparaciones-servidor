-- ══════════════════════════════════════════════════════════════════════════════
-- migracion-nsys-espejo.sql — 0.9.17, pieza 1 del módulo de teléfonos: espejo de NSYS
-- (spec docs/superpowers/specs/2026-10-16-pieza1-stock-telefonos-design.md §4, raíz).
-- La aplica el usuario a mano en la VM, con vista previa, ANTES de desplegar el
-- servidor 0.9.17. Aditiva: el servidor y la web anteriores no usan estas tablas.
-- NO idempotente (relanzar = error de tabla existente).
-- 1) Vista previa: el primer SELECT debe devolver 0 (ninguna existe) y, en el segundo,
--    BD_COLLATION debe ser igual a IMEI_COLLATION.
-- 2) Ejecutar los CREATE TABLE y el INSERT de Nsys_ubicacion.
-- 3) Comprobación final: ubicaciones_utf8_ok debe dar 6.
-- Horas de Madrid en todas las columnas DATETIME.
-- ══════════════════════════════════════════════════════════════════════════════
USE gestion_reparaciones;
SET NAMES utf8mb4;

-- Vista previa (no modifica nada): debe dar 0
SELECT COUNT(*) AS YA_EXISTEN
  FROM information_schema.TABLES
 WHERE TABLE_SCHEMA = 'gestion_reparaciones'
   AND TABLE_NAME IN ('Nsys_importacion', 'Nsys_movil', 'Nsys_evento', 'Nsys_producto', 'Nsys_ubicacion');

-- Vista previa (no modifica nada): las tablas nuevas toman el juego de caracteres y la intercalación por defecto de
-- la base de datos. Si BD_COLLATION no es igual a IMEI_COLLATION, el relleno de modelos del taller (JOIN de
-- Nsys_movil.IMEI_SN con Telefono.IMEI) falla con el error 1267 (Illegal mix of collations). Si no coinciden, NO
-- seguir: hay que crear las tablas con la intercalación de Telefono.IMEI.
SELECT s.DEFAULT_CHARACTER_SET_NAME AS BD_CHARSET,
       s.DEFAULT_COLLATION_NAME     AS BD_COLLATION,
       t.TABLE_COLLATION            AS TELEFONO_COLLATION,
       c.COLLATION_NAME             AS IMEI_COLLATION
  FROM information_schema.SCHEMATA s
  LEFT JOIN information_schema.TABLES t
         ON t.TABLE_SCHEMA = s.SCHEMA_NAME AND t.TABLE_NAME = 'Telefono'
  LEFT JOIN information_schema.COLUMNS c
         ON c.TABLE_SCHEMA = s.SCHEMA_NAME AND c.TABLE_NAME = 'Telefono' AND c.COLUMN_NAME = 'IMEI'
 WHERE s.SCHEMA_NAME = 'gestion_reparaciones';

-- Un registro por fichero procesado. FICHERO no es único: un fichero que da ERROR se reintenta.
CREATE TABLE Nsys_importacion (
    ID_IMPORTACION    INT          NOT NULL AUTO_INCREMENT,
    FICHERO           VARCHAR(120) NOT NULL,
    FECHA_EXPORTACION DATETIME     NOT NULL COMMENT 'Items: la del nombre del fichero; maestro: su modifiedTime',
    ORIGEN            ENUM('ACTUAL','HISTORICO','MAESTRO') NOT NULL,
    EMPEZADA          DATETIME     NOT NULL,
    TERMINADA         DATETIME     NULL,
    FILAS             INT          NOT NULL DEFAULT 0,
    CAMBIOS           INT          NOT NULL DEFAULT 0,
    RESULTADO         ENUM('EN_CURSO','OK','RECHAZADO','ERROR') NOT NULL DEFAULT 'EN_CURSO',
    MENSAJE           VARCHAR(500) NULL,
    PRIMARY KEY (ID_IMPORTACION),
    KEY idx_nsys_importacion (ORIGEN, RESULTADO, FECHA_EXPORTACION)
);

-- La foto actual: una fila por móvil y lote, como en la última exportación. Sin costes ni precios.
CREATE TABLE Nsys_movil (
    IMEI_SN          VARCHAR(40)  NOT NULL COMMENT '«IMEI/SN» de NSYS: puede ser un número de serie',
    BATCH            VARCHAR(40)  NOT NULL,
    GOODS            VARCHAR(150) NULL,
    GRADE            VARCHAR(20)  NULL,
    STATUS           VARCHAR(30)  NULL,
    LOCATION         VARCHAR(80)  NOT NULL DEFAULT '' COMMENT 'Caja de NSYS tal cual; vacía = sin caja',
    SUPPLIER         VARCHAR(80)  NULL,
    RECEIVING_DATE   DATE         NULL,
    SOLD_DATE        DATE         NULL,
    SKU              VARCHAR(60)  NULL,
    MODELO           VARCHAR(20)  NULL COMMENT 'Código de modelo del ERP (13pro…); NULL si no está en el catálogo',
    MODELO_MAESTRO   VARCHAR(40)  NULL,
    CAPACIDAD        VARCHAR(20)  NULL,
    COLOR            VARCHAR(40)  NULL,
    GRADO_DETALLE    VARCHAR(20)  NULL,
    ES_ESIM          BOOLEAN      NOT NULL DEFAULT FALSE,
    UBICACION        VARCHAR(40)  NULL COMMENT 'Ubicación del ERP; NULL si la caja no se trae',
    UBICACION_ULTIMA VARCHAR(40)  NULL COMMENT 'Última ubicación del ERP antes de «Pedidos realizados»',
    ESTADO           ENUM('STOCK','DE_PEDIDO','NO_CUENTA','VENDIDO','BAJA') NOT NULL,
    VISTO_PRIMERO    DATETIME     NOT NULL,
    CAMBIADO         DATETIME     NOT NULL,
    PRIMARY KEY (IMEI_SN, BATCH),
    KEY idx_nsys_movil_estado (ESTADO),
    KEY idx_nsys_movil_modelo (MODELO, CAPACIDAD)
);

-- El historial para el rastro: solo se añade, nunca se modifica.
CREATE TABLE Nsys_evento (
    ID_EVENTO      BIGINT       NOT NULL AUTO_INCREMENT,
    IMEI_SN        VARCHAR(40)  NOT NULL,
    BATCH          VARCHAR(40)  NOT NULL,
    FECHA          DATETIME     NOT NULL COMMENT 'Hora de la exportación en que se vio el cambio',
    SOLO_DIA       BOOLEAN      NOT NULL DEFAULT FALSE COMMENT 'Esqueleto: fecha de NSYS sin hora',
    TIPO           ENUM('ENTRADA','UBICACION','GRADO','PRODUCTO','ESTADO','VENTA','VENTA_DESHECHA','REENTRADA') NOT NULL,
    DE             VARCHAR(150) NULL,
    A              VARCHAR(150) NULL,
    A_NSYS         VARCHAR(80)  NULL COMMENT 'En UBICACION: la caja de NSYS tal cual',
    ID_IMPORTACION INT          NULL,
    PRIMARY KEY (ID_EVENTO),
    KEY idx_nsys_evento_imei (IMEI_SN, FECHA),
    CONSTRAINT fk_nsys_evento_importacion FOREIGN KEY (ID_IMPORTACION) REFERENCES Nsys_importacion (ID_IMPORTACION)
);

-- El maestro de productos: se recarga entero cuando cambia en Drive.
CREATE TABLE Nsys_producto (
    CLAVE           VARCHAR(160) NOT NULL COMMENT '«Product Name Calidad» sin espacios y en minúsculas',
    MODELO          VARCHAR(20)  NULL COMMENT 'Código de modelo del ERP; NULL si no está en el catálogo',
    MODELO_MAESTRO  VARCHAR(40)  NULL,
    CAPACIDAD       VARCHAR(20)  NULL,
    COLOR           VARCHAR(40)  NULL,
    CALIDAD_DETALLE VARCHAR(20)  NULL,
    SKU_WEB         VARCHAR(60)  NULL,
    SKU_AMAZON      VARCHAR(60)  NULL,
    SKU_SIN_GRADO   VARCHAR(60)  NULL,
    ES_ESIM         BOOLEAN      NOT NULL DEFAULT FALSE,
    PRIMARY KEY (CLAVE)
);

-- Traducción de cajas de NSYS a ubicaciones del ERP. Es una tabla, no código: se ajusta sin programar.
CREATE TABLE Nsys_ubicacion (
    LOCATION     VARCHAR(80) NOT NULL COMMENT 'Caja de NSYS, texto exacto; vacía = sin caja',
    UBICACION    VARCHAR(40) NULL COMMENT 'Ubicación del ERP; NULL = no se trae',
    CUENTA_STOCK BOOLEAN     NOT NULL DEFAULT FALSE,
    ES_PEDIDO    BOOLEAN     NOT NULL DEFAULT FALSE,
    ORDEN        INT         NOT NULL DEFAULT 0,
    PRIMARY KEY (LOCATION)
);

-- Propuesta del 16/10/2026 (spec §4.5), pendiente de que el usuario la confirme: solo cambia esta carga.
INSERT INTO Nsys_ubicacion (LOCATION, UBICACION, CUENTA_STOCK, ES_PEDIDO, ORDEN) VALUES
    ('',                                                 'En revisión',            TRUE,  FALSE,  10),
    ('Funcionalidad pendiente',                          'Devolución',             TRUE,  FALSE,  20),
    ('CAJA PULIR',                                       'Taller · Pulir',         TRUE,  FALSE,  30),
    ('CAJA GLASS',                                       'Taller · Glass',         TRUE,  FALSE,  40),
    ('CAJA REP. OFICINA',                                'Taller · Reparación',    TRUE,  FALSE,  50),
    ('C.R. Pantalla Rota',                               'Taller · Pantalla rota', TRUE,  FALSE,  60),
    ('CAJA LISTOS',                                      'Listos',                 TRUE,  FALSE,  70),
    ('AFaltaRepuestos',                                  'Esperando pieza',        FALSE, FALSE,  80),
    ('CF021',                                            'No vendible',            FALSE, FALSE,  90),
    ('Pedidos realizados',                               NULL,                     FALSE, TRUE,  100),
    ('Desguace',                                         NULL,                     FALSE, FALSE, 200),
    ('Pendiente revisar si hay error de inventario',     NULL,                     FALSE, FALSE, 210),
    ('CF018',                                            NULL,                     FALSE, FALSE, 220),
    ('CF029',                                            NULL,                     FALSE, FALSE, 230),
    ('Incidencia (pérdida, robo, transportista)',        NULL,                     FALSE, FALSE, 240),
    ('MV CF018',                                         NULL,                     FALSE, FALSE, 250),
    ('MV CF021',                                         NULL,                     FALSE, FALSE, 260),
    ('MV Desguace',                                      NULL,                     FALSE, FALSE, 270),
    ('MV Pedidos Realizados',                            NULL,                     FALSE, FALSE, 280),
    ('MV Pendiente revisar si hay error de inventario',  NULL,                     FALSE, FALSE, 290);

-- Comprobación (no modifica nada): debe dar 6. Compara los bytes UTF-8 de los seis nombres con tilde o «·», así no
-- la engaña un cliente que enseñe mal los acentos. Si da menos, se guardaron mal (cliente sin utf8mb4) y la web,
-- que busca esos nombres exactos, no los reconocerá.
SELECT COUNT(*) AS ubicaciones_utf8_ok
  FROM Nsys_ubicacion
 WHERE HEX(UBICACION) IN (
       '456E20726576697369C3B36E',                          -- En revisión
       '4465766F6C756369C3B36E',                            -- Devolución
       '54616C6C657220C2B72050756C6972',                    -- Taller · Pulir
       '54616C6C657220C2B720476C617373',                    -- Taller · Glass
       '54616C6C657220C2B7205265706172616369C3B36E',        -- Taller · Reparación
       '54616C6C657220C2B72050616E74616C6C6120726F7461');   -- Taller · Pantalla rota
