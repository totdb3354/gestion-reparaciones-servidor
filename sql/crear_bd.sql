-- ══════════════════════════════════════════════════════════════════════════════
-- crear_bd.sql  —  Reset completo + creación desde cero
-- Se puede relanzar cuantas veces se quiera — borra y recrea todo.
-- ══════════════════════════════════════════════════════════════════════════════

CREATE DATABASE IF NOT EXISTS gestion_reparaciones
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE gestion_reparaciones;
SET NAMES utf8mb4;

SET FOREIGN_KEY_CHECKS = 0;

DROP TABLE IF EXISTS Nsys_evento;
DROP TABLE IF EXISTS Nsys_importacion;
DROP TABLE IF EXISTS Nsys_movil;
DROP TABLE IF EXISTS Nsys_producto;
DROP TABLE IF EXISTS Nsys_ubicacion;
DROP TABLE IF EXISTS Envio_Telefono;
DROP TABLE IF EXISTS Envio;
DROP TABLE IF EXISTS Movimiento_telefono;
DROP TABLE IF EXISTS Modelo_equivalencia;
DROP TABLE IF EXISTS Lote;
DROP TABLE IF EXISTS Log_Actividad;
DROP TABLE IF EXISTS Solicitud_Stock;
DROP TABLE IF EXISTS Reparacion_componente;
DROP TABLE IF EXISTS Compra_otro;
DROP TABLE IF EXISTS Compra_componente;
DROP TABLE IF EXISTS Proveedor_tipo_excluido;
DROP TABLE IF EXISTS Proveedor_tipo;
DROP TABLE IF EXISTS Bloque_compra;
DROP TABLE IF EXISTS Reparacion_borrador;
DROP TABLE IF EXISTS Reparacion;
DROP TABLE IF EXISTS Usuario;
DROP TABLE IF EXISTS TipoCambio;
DROP TABLE IF EXISTS Proveedor;
DROP TABLE IF EXISTS Componente;
DROP TABLE IF EXISTS Revision;
DROP TABLE IF EXISTS Telefono;
DROP TABLE IF EXISTS Cliente;
DROP TABLE IF EXISTS Parametro;
DROP TABLE IF EXISTS Tecnico;

SET FOREIGN_KEY_CHECKS = 1;

-- ── Tablas base (sin dependencias) ────────────────────────────────────────────

CREATE TABLE Componente (
    ID_COM         INT          NOT NULL AUTO_INCREMENT,
    TIPO           VARCHAR(100) NOT NULL UNIQUE,
    STOCK          INT          NOT NULL DEFAULT 0,
    STOCK_MINIMO   INT          NOT NULL DEFAULT 0,
    ACTIVO         BOOLEAN      NOT NULL DEFAULT TRUE,
    ID_COM_MASTER  INT          NULL,
    AUTO_PEDIDO    BOOLEAN      NOT NULL DEFAULT FALSE,
    FECHA_REGISTRO TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UPDATED_AT     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (ID_COM),
    CONSTRAINT fk_componente_master FOREIGN KEY (ID_COM_MASTER) REFERENCES Componente (ID_COM)
);

CREATE TABLE Tecnico (
    ID_TEC         INT          NOT NULL AUTO_INCREMENT,
    NOMBRE         VARCHAR(100) NOT NULL,
    ACTIVO         BOOLEAN      NOT NULL DEFAULT TRUE,
    ES_ESTADISTICA BOOLEAN      NOT NULL DEFAULT TRUE,
    ES_GLASS       BOOLEAN      NOT NULL DEFAULT FALSE,
    PRIMARY KEY (ID_TEC)
);

CREATE TABLE Cliente (
    ID_CLI     INT          NOT NULL AUTO_INCREMENT,
    NOMBRE     VARCHAR(150) NOT NULL,
    ACTIVO     BOOLEAN      NOT NULL DEFAULT TRUE,
    UPDATED_AT TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (ID_CLI)
);

CREATE TABLE Telefono (
    IMEI                VARCHAR(15)  NOT NULL,
    MODELO              VARCHAR(100),
    OBSERVACION         TEXT,
    ID_CLI              INT          NULL,
    ID_LOTE             INT          NULL,
    ESTADO              ENUM('RECIBIDO','EN_REVISION','BLOQUEADO','OK','ENVIADO','DESGUACE') NULL,
    STORAGE_GB          INT          NULL,
    COLOR               VARCHAR(50)  NULL,
    GRADO_PROVEEDOR     VARCHAR(20)  NULL,
    GRADO_PROPIO        ENUM('C','B','A-','A') NULL,
    ES_ESIM             BOOLEAN      NOT NULL DEFAULT FALSE,
    PRECIO_COMPRA       DECIMAL(10,2) NULL,
    DIVISA              VARCHAR(3)   NULL,
    PRECIO_COMPRA_EUR   DECIMAL(10,2) NULL,
    ES_DEVOLUCION       BOOLEAN      NOT NULL DEFAULT FALSE,
    UPDATED_AT          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (IMEI),
    CONSTRAINT fk_telefono_cliente FOREIGN KEY (ID_CLI) REFERENCES Cliente (ID_CLI)
);

-- ── Tablas con dependencias ────────────────────────────────────────────────────

CREATE TABLE Usuario (
    ID_USU            INT          NOT NULL AUTO_INCREMENT,
    NOMBRE_USUARIO    VARCHAR(50)  NOT NULL UNIQUE,
    PASSWORD          VARCHAR(255) NOT NULL,
    ROL               ENUM('ADMIN','SUPERTECNICO','TECNICO') NOT NULL,
    ID_TEC            INT          NULL,
    PASSWORD_TEMPORAL TINYINT(1)   NOT NULL DEFAULT 0,
    PRIMARY KEY (ID_USU),
    CONSTRAINT fk_usuario_tecnico FOREIGN KEY (ID_TEC) REFERENCES Tecnico (ID_TEC)
);

CREATE TABLE Revision (
    ID_REVISION      INT          NOT NULL AUTO_INCREMENT,
    IMEI             VARCHAR(15)  NOT NULL,
    FECHA_CREACION   DATETIME     NOT NULL,
    EST_GRADO        ENUM('C','B','A-','A') NULL,
    EST_PANT         ENUM('P','G') NULL,
    EST_ID_USU       INT          NULL,
    EST_FECHA        DATETIME     NULL,
    FUN_BATERIA_PCT  TINYINT UNSIGNED NULL,
    FUN_PANT_TACTIL  BOOLEAN NOT NULL DEFAULT FALSE,
    FUN_PANT_QUEMADA BOOLEAN NOT NULL DEFAULT FALSE,
    FUN_PANT_MAL     BOOLEAN NOT NULL DEFAULT FALSE,
    FUN_CAM_MANCHA   BOOLEAN NOT NULL DEFAULT FALSE,
    FUN_CAM_LENTE    BOOLEAN NOT NULL DEFAULT FALSE,
    FUN_ALT_SUP      BOOLEAN NOT NULL DEFAULT FALSE,
    FUN_ALT_INF      BOOLEAN NOT NULL DEFAULT FALSE,
    FUN_MIC          BOOLEAN NOT NULL DEFAULT FALSE,
    FUN_FACE_ID      BOOLEAN NOT NULL DEFAULT FALSE,
    FUN_MS           BOOLEAN NOT NULL DEFAULT FALSE,
    FUN_MS_TEXTO     VARCHAR(100) NULL,
    FUN_BLOQUEO_OP   BOOLEAN NOT NULL DEFAULT FALSE,
    FUN_OBSERVACION  VARCHAR(500) NULL,
    FUN_ID_USU       INT          NULL,
    FUN_FECHA        DATETIME     NULL,
    UPDATED_AT       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (ID_REVISION),
    KEY idx_revision_imei (IMEI, ID_REVISION),
    CONSTRAINT fk_revision_telefono FOREIGN KEY (IMEI) REFERENCES Telefono (IMEI),
    CONSTRAINT fk_revision_usu_est  FOREIGN KEY (EST_ID_USU) REFERENCES Usuario (ID_USU),
    CONSTRAINT fk_revision_usu_fun  FOREIGN KEY (FUN_ID_USU) REFERENCES Usuario (ID_USU)
);

CREATE TABLE Envio (
    ID_ENVIO      INT          NOT NULL AUTO_INCREMENT,
    FECHA         DATETIME     NOT NULL,
    ID_CLI        INT          NULL,
    DESTINO_TEXTO VARCHAR(150) NULL,
    REFERENCIA    VARCHAR(100) NULL,
    ID_USU        INT          NOT NULL,
    UPDATED_AT    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (ID_ENVIO),
    CONSTRAINT fk_envio_cliente FOREIGN KEY (ID_CLI) REFERENCES Cliente (ID_CLI),
    CONSTRAINT fk_envio_usuario FOREIGN KEY (ID_USU) REFERENCES Usuario (ID_USU)
);

CREATE TABLE Envio_Telefono (
    ID_ET             INT          NOT NULL AUTO_INCREMENT,
    ID_ENVIO          INT          NOT NULL,
    IMEI              VARCHAR(15)  NOT NULL,
    DEVUELTO          BOOLEAN      NOT NULL DEFAULT FALSE,
    MOTIVO_DEVOLUCION VARCHAR(255) NULL,
    FECHA_DEVOLUCION  DATETIME     NULL,
    ID_USU_DEVOLUCION INT          NULL,
    UPDATED_AT        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (ID_ET),
    KEY idx_et_imei (IMEI, ID_ET),
    CONSTRAINT fk_et_envio    FOREIGN KEY (ID_ENVIO)          REFERENCES Envio (ID_ENVIO),
    CONSTRAINT fk_et_telefono FOREIGN KEY (IMEI)              REFERENCES Telefono (IMEI),
    CONSTRAINT fk_et_usu_dev  FOREIGN KEY (ID_USU_DEVOLUCION) REFERENCES Usuario (ID_USU)
);

CREATE TABLE Reparacion (
    ID_REP               VARCHAR(30)  NOT NULL,
    FECHA_ASIG           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FECHA_FIN            DATETIME,
    IMEI                 VARCHAR(15)  NOT NULL,
    ID_TEC               INT          NOT NULL,
    ID_TEC_ASIGNA        INT          NULL,
    ID_REP_ANTERIOR      VARCHAR(30),
    COMENTARIO_ASIGNACION TEXT,
    URGENTE              BOOLEAN      NOT NULL DEFAULT FALSE,
    ES_CHASIS            BOOLEAN      NOT NULL DEFAULT FALSE,
    POR_CERRAR           BOOLEAN      NOT NULL DEFAULT FALSE,
    ENTREGADO_AT         DATETIME     NULL,
    ENTREGADO_POR        INT          NULL,
    ID_REP_ABRIR         VARCHAR(30)  NULL COMMENT 'Solo filas AG: el R… del «Abrir» que creó su entrega (2026-10-13)',
    ID_CLI               INT          NULL COMMENT 'Cliente con el que se cerró el trabajo (0.9.8); abierto: NULL',
    UPDATED_AT           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (ID_REP),
    CONSTRAINT Reparacion_ibfk_1 FOREIGN KEY (IMEI)            REFERENCES Telefono  (IMEI),
    CONSTRAINT fk_rep_tecnico    FOREIGN KEY (ID_TEC)          REFERENCES Tecnico   (ID_TEC),
    CONSTRAINT Reparacion_ibfk_3 FOREIGN KEY (ID_REP_ANTERIOR) REFERENCES Reparacion(ID_REP),
    CONSTRAINT fk_rep_tec_asigna FOREIGN KEY (ID_TEC_ASIGNA)   REFERENCES Tecnico   (ID_TEC),
    CONSTRAINT fk_rep_entregado_por FOREIGN KEY (ENTREGADO_POR) REFERENCES Tecnico  (ID_TEC),
    CONSTRAINT fk_reparacion_cliente FOREIGN KEY (ID_CLI)       REFERENCES Cliente  (ID_CLI)
);

-- Borrador del modal de reparación por componente (1:0..1 con Reparacion, entidad débil).
CREATE TABLE Reparacion_borrador (
    ID_REP     VARCHAR(30) NOT NULL,
    CONTENIDO  LONGTEXT    NOT NULL COMMENT 'Borrador del modal serializado en JSON',
    UPDATED_AT TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (ID_REP),
    CONSTRAINT fk_borrador_reparacion FOREIGN KEY (ID_REP)
        REFERENCES Reparacion (ID_REP) ON DELETE CASCADE
);

-- ── Tablas de stock ───────────────────────────────────────────────────────────

CREATE TABLE Proveedor (
    ID_PROV    INT          NOT NULL AUTO_INCREMENT,
    NOMBRE     VARCHAR(100) NOT NULL,
    ACTIVO     BOOLEAN      NOT NULL DEFAULT TRUE,
    DIVISA     VARCHAR(3)   NOT NULL DEFAULT 'EUR',
    TIPO       ENUM('COMPONENTES','TELEFONOS') NOT NULL DEFAULT 'COMPONENTES',
    COMENTARIO TEXT,
    PRIMARY KEY (ID_PROV)
);

CREATE TABLE Lote (
    ID_LOTE      INT          NOT NULL AUTO_INCREMENT,
    BATCH_NUMBER VARCHAR(100) NOT NULL,
    ID_PROV      INT          NOT NULL,
    FECHA_IMPORT DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    NOTA         TEXT,
    UPDATED_AT   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (ID_LOTE),
    UNIQUE KEY uq_lote_batch_prov (BATCH_NUMBER, ID_PROV),
    CONSTRAINT fk_lote_proveedor FOREIGN KEY (ID_PROV) REFERENCES Proveedor (ID_PROV)
);

ALTER TABLE Telefono ADD CONSTRAINT fk_telefono_lote FOREIGN KEY (ID_LOTE) REFERENCES Lote (ID_LOTE);

CREATE TABLE TipoCambio (
    DIVISA   VARCHAR(3)    NOT NULL,
    FECHA    DATE          NOT NULL,
    TASA     DECIMAL(10,6) NOT NULL,
    PRIMARY KEY (DIVISA, FECHA)
);

CREATE TABLE Dificultad_puntos (
    CLAVE      VARCHAR(20)  NOT NULL,
    PUNTOS     DECIMAL(4,2) NOT NULL,
    UPDATED_AT TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (CLAVE)
);

INSERT INTO Dificultad_puntos (CLAVE, PUNTOS) VALUES
    ('bateria',  1.00),
    ('camara',   0.70),
    ('chasis',   2.00),
    ('marco',    0.50),
    ('pantalla', 1.00),
    ('tapa',     1.00),
    ('glass',    0.50),
    ('otro',     0.50),
    ('pulido',   0.25),
    ('abrir',    0.25),
    ('cerrar',   0.17);

CREATE TABLE Parametro (
    CLAVE      VARCHAR(50) NOT NULL,
    VALOR      INT         NOT NULL,
    UPDATED_AT TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (CLAVE)
);

INSERT INTO Parametro (CLAVE, VALOR) VALUES
    ('PREVISION_PESO_1', 50),
    ('PREVISION_PESO_2', 30),
    ('PREVISION_PESO_3', 20);

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

CREATE TABLE Proveedor_tipo (
    ID_PROV INT         NOT NULL,
    PREFIJO VARCHAR(10) NOT NULL,
    PRIMARY KEY (ID_PROV, PREFIJO),
    CONSTRAINT fk_provtipo_proveedor FOREIGN KEY (ID_PROV) REFERENCES Proveedor (ID_PROV) ON DELETE CASCADE
);

CREATE TABLE Proveedor_tipo_excluido (
    ID_PROV INT         NOT NULL,
    PREFIJO VARCHAR(10) NOT NULL,
    MODELO  VARCHAR(20) NOT NULL,
    PRIMARY KEY (ID_PROV, PREFIJO, MODELO),
    CONSTRAINT fk_provexcl_tipo FOREIGN KEY (ID_PROV, PREFIJO) REFERENCES Proveedor_tipo (ID_PROV, PREFIJO) ON DELETE CASCADE
);

CREATE TABLE Compra_componente (
    ID_COMPRA            INT           NOT NULL AUTO_INCREMENT,
    ID_COM               INT           NOT NULL,
    ID_PROV              INT           NOT NULL,
    ID_BLOQUE            INT,
    CANTIDAD             INT           NOT NULL,
    CANTIDAD_RECIBIDA    INT,
    ES_URGENTE           BOOLEAN       NOT NULL DEFAULT FALSE,
    FECHA_PEDIDO         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FECHA_LLEGADA        DATETIME,
    PRECIO_UNIDAD_PEDIDO DECIMAL(10,2) NOT NULL,
    DIVISA               VARCHAR(3)    NOT NULL DEFAULT 'EUR',
    PRECIO_EUR           DECIMAL(10,2) NOT NULL,
    ESTADO               ENUM('pendiente','en_camino','parcial','recibido','cancelado') NOT NULL DEFAULT 'pendiente',
    UPDATED_AT           TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (ID_COMPRA),
    CONSTRAINT fk_compra_componente FOREIGN KEY (ID_COM)    REFERENCES Componente    (ID_COM),
    CONSTRAINT fk_compra_proveedor  FOREIGN KEY (ID_PROV)   REFERENCES Proveedor     (ID_PROV),
    CONSTRAINT fk_compra_bloque     FOREIGN KEY (ID_BLOQUE) REFERENCES Bloque_compra (ID_BLOQUE),
    INDEX idx_compra_bloque (ID_BLOQUE)
);

CREATE TABLE Compra_otro (
    ID_COMPRA_OTRO       INT           NOT NULL AUTO_INCREMENT,
    ID_PROV              INT           NOT NULL,
    ID_BLOQUE            INT,
    CONCEPTO             VARCHAR(255)  NOT NULL,
    CANTIDAD             INT           NOT NULL,
    CANTIDAD_RECIBIDA    INT,
    ES_URGENTE           BOOLEAN       NOT NULL DEFAULT FALSE,
    FECHA_PEDIDO         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FECHA_LLEGADA        DATETIME,
    PRECIO_UNIDAD_PEDIDO DECIMAL(10,2) NOT NULL DEFAULT 0,
    DIVISA               VARCHAR(3)    NOT NULL DEFAULT 'EUR',
    PRECIO_EUR           DECIMAL(10,2) NOT NULL DEFAULT 0,
    ESTADO               ENUM('pendiente','en_camino','parcial','recibido','cancelado') NOT NULL DEFAULT 'pendiente',
    UPDATED_AT           TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (ID_COMPRA_OTRO),
    CONSTRAINT fk_compra_otro_proveedor FOREIGN KEY (ID_PROV)   REFERENCES Proveedor     (ID_PROV),
    CONSTRAINT fk_compra_otro_bloque    FOREIGN KEY (ID_BLOQUE) REFERENCES Bloque_compra (ID_BLOQUE),
    INDEX idx_compra_otro_bloque (ID_BLOQUE)
);

-- ── Tablas de reparaciones ────────────────────────────────────────────────────

CREATE TABLE Reparacion_componente (
    ID_RC                 INT          NOT NULL AUTO_INCREMENT,
    ID_REP                VARCHAR(30)  NOT NULL,
    ID_COM                INT,
    ES_REUTILIZADO        BOOLEAN      NOT NULL DEFAULT FALSE,
    ES_INCIDENCIA         BOOLEAN      NOT NULL DEFAULT FALSE,
    ES_RESUELTO           BOOLEAN      NOT NULL DEFAULT FALSE,
    INCIDENCIA            TEXT,
    OBSERVACIONES         TEXT,
    ES_SOLICITUD          BOOLEAN      NOT NULL DEFAULT FALSE,
    DESCRIPCION_SOLICITUD TEXT,
    ESTADO_SOLICITUD      ENUM('PENDIENTE','GESTIONADA','RECHAZADA') NOT NULL DEFAULT 'PENDIENTE',
    CANTIDAD              INT          NOT NULL DEFAULT 1,
    UPDATED_AT            TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (ID_RC),
    CONSTRAINT fk_rc_reparacion FOREIGN KEY (ID_REP) REFERENCES Reparacion (ID_REP),
    CONSTRAINT fk_rc_componente FOREIGN KEY (ID_COM) REFERENCES Componente  (ID_COM)
);

-- ── Solicitudes de stock preventivas ─────────────────────────────────────────

CREATE TABLE Solicitud_Stock (
    ID_SOL      INT          NOT NULL AUTO_INCREMENT,
    ID_COM      INT          NOT NULL,
    ID_USU      INT          NOT NULL,
    DESCRIPCION TEXT,
    ESTADO      ENUM('PENDIENTE','GESTIONADA','RECHAZADA') NOT NULL DEFAULT 'PENDIENTE',
    FECHA       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UPDATED_AT  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (ID_SOL),
    CONSTRAINT fk_sol_componente FOREIGN KEY (ID_COM) REFERENCES Componente (ID_COM),
    CONSTRAINT fk_sol_usuario    FOREIGN KEY (ID_USU) REFERENCES Usuario    (ID_USU)
);

-- ── Log de actividad ──────────────────────────────────────────────────────────

CREATE TABLE Log_Actividad (
    ID_LOG         INT          NOT NULL AUTO_INCREMENT,
    FECHA          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ID_USU         INT          NULL,
    NOMBRE_USUARIO VARCHAR(50)  NULL,
    ACCION         VARCHAR(50)  NOT NULL,
    DETALLE        TEXT,
    MOTIVO         TEXT,
    PRIMARY KEY (ID_LOG),
    CONSTRAINT fk_log_usuario FOREIGN KEY (ID_USU) REFERENCES Usuario (ID_USU) ON DELETE SET NULL
);

-- ── Trazabilidad de movimientos telefónicos ────────────────────────────────────

CREATE TABLE Movimiento_telefono (
    ID_MOV            INT          NOT NULL AUTO_INCREMENT,
    IMEI              VARCHAR(15)  NOT NULL,
    UBICACION_ORIGEN  VARCHAR(30)  NULL,
    UBICACION_DESTINO VARCHAR(30)  NOT NULL,
    FECHA             DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ID_USU            INT          NOT NULL,
    MOTIVO            VARCHAR(255) NULL,
    REFERENCIA        VARCHAR(100) NULL,
    PRIMARY KEY (ID_MOV),
    KEY idx_mov_imei (IMEI),
    CONSTRAINT fk_mov_telefono FOREIGN KEY (IMEI)   REFERENCES Telefono (IMEI),
    CONSTRAINT fk_mov_usuario  FOREIGN KEY (ID_USU) REFERENCES Usuario  (ID_USU)
);

-- ── Equivalencias de modelos ──────────────────────────────────────────────────

CREATE TABLE Modelo_equivalencia (
    TEXTO_EXTERNO  VARCHAR(100) NOT NULL,
    MODELO_INTERNO VARCHAR(100) NOT NULL,
    UPDATED_AT     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (TEXTO_EXTERNO)
);

-- ── Equivalencias de colores ──────────────────────────────────────────────────

CREATE TABLE Color_equivalencia (
    TEXTO_EXTERNO  VARCHAR(100) NOT NULL,
    COLOR_OFICIAL  VARCHAR(50)  NOT NULL,
    UPDATED_AT     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (TEXTO_EXTERNO)
);

-- ── Seed data ─────────────────────────────────────────────────────────────────

INSERT INTO Cliente (NOMBRE, ACTIVO) VALUES ('WEB', TRUE), ('OTRO', TRUE);

-- 0.9.17: espejo de NSYS (pieza 1 del módulo de teléfonos; ver sql/migracion-nsys-espejo.sql)
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
