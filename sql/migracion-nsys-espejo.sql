-- ══════════════════════════════════════════════════════════════════════════════
-- migracion-nsys-espejo.sql — 0.9.17, pieza 1 del módulo de teléfonos: espejo de NSYS
-- (spec docs/superpowers/specs/2026-10-16-pieza1-stock-telefonos-design.md §4, raíz).
-- La aplica el usuario a mano en la VM, con vista previa, ANTES de desplegar el
-- servidor 0.9.17. Aditiva: el servidor y la web anteriores no usan estas tablas.
-- NO idempotente (relanzar = error de tabla existente).
-- 1) Vista previa: el SELECT debe devolver 0 (ninguna existe).
-- 2) Ejecutar los CREATE TABLE y el INSERT de Nsys_ubicacion.
-- Horas de Madrid en todas las columnas DATETIME.
-- ══════════════════════════════════════════════════════════════════════════════
USE gestion_reparaciones;

-- Vista previa (no modifica nada): debe dar 0
SELECT COUNT(*) AS YA_EXISTEN
  FROM information_schema.TABLES
 WHERE TABLE_SCHEMA = 'gestion_reparaciones'
   AND TABLE_NAME IN ('Nsys_importacion', 'Nsys_movil', 'Nsys_evento', 'Nsys_producto', 'Nsys_ubicacion');

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
