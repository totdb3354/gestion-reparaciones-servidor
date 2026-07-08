-- ══════════════════════════════════════════════════════════════════════════════
-- migracion-f2a-lotes.sql — Fase 2a: Lote, ciclo de vida de Telefono, movimientos
-- La aplica el usuario a mano en gestion_reparaciones. Idempotencia no requerida
-- (mismo criterio que las migraciones anteriores); relanzar = error de "ya existe".
-- ══════════════════════════════════════════════════════════════════════════════

USE gestion_reparaciones;

-- Lote de compra: todos los teléfonos entran por lote (grande o pequeño).
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

-- Ciclo de vida del teléfono. EN_REPARACION no se almacena: es implícito
-- (tiene trabajo abierto) y lo deriva UbicacionDerivador. ESTADO NULL = histórico
-- pre-fase, fuera del ciclo. GRADO_PROPIO = grado del chasis (escala propia).
ALTER TABLE Telefono
    ADD COLUMN ID_LOTE           INT NULL AFTER ID_CLI,
    ADD COLUMN ESTADO            ENUM('RECIBIDO','EN_REVISION','BLOQUEADO','OK','ENVIADO','DESGUACE') NULL AFTER ID_LOTE,
    ADD COLUMN STORAGE_GB        INT NULL AFTER ESTADO,
    ADD COLUMN COLOR             VARCHAR(50) NULL AFTER STORAGE_GB,
    ADD COLUMN GRADO_PROVEEDOR   VARCHAR(20) NULL AFTER COLOR,
    ADD COLUMN GRADO_PROPIO      ENUM('C','B','A-','A','A+') NULL AFTER GRADO_PROVEEDOR,
    ADD COLUMN PRECIO_COMPRA     DECIMAL(10,2) NULL AFTER GRADO_PROPIO,
    ADD COLUMN DIVISA            VARCHAR(3) NULL AFTER PRECIO_COMPRA,
    ADD COLUMN PRECIO_COMPRA_EUR DECIMAL(10,2) NULL AFTER DIVISA,
    ADD COLUMN ES_DEVOLUCION     BOOLEAN NOT NULL DEFAULT FALSE AFTER PRECIO_COMPRA_EUR,
    ADD CONSTRAINT fk_telefono_lote FOREIGN KEY (ID_LOTE) REFERENCES Lote (ID_LOTE);

-- Trazabilidad append-only de ubicaciones (diseño de la spec previa del usuario).
-- Ubicaciones canónicas: ALMACEN, PARA_REVISAR, BLOQUEO, REPARACIONES, LISTOS,
-- PEDIDOS, ENVIADO, DESGUACE. ORIGEN NULL = entrada al sistema.
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

-- Equivalencias de modelo recordadas por el importador ("iphone16esim" → "16").
-- TEXTO_EXTERNO se guarda NORMALIZADO (minúsculas, solo [a-z0-9], sin prefijo "iphone").
CREATE TABLE Modelo_equivalencia (
    TEXTO_EXTERNO  VARCHAR(100) NOT NULL,
    MODELO_INTERNO VARCHAR(100) NOT NULL,
    UPDATED_AT     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (TEXTO_EXTERNO)
);
