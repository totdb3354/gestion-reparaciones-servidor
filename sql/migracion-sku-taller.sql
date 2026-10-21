-- ══════════════════════════════════════════════════════════════════════════════
-- migracion-sku-taller.sql — 0.9.21, SKU del taller (piezas 1 y 2)
-- (spec docs/superpowers/specs/2026-10-21-v0921-sku-del-taller-base-design.md §4, raíz).
-- La aplica el usuario a mano en la VM, con vista previa, ANTES de desplegar el
-- servidor 0.9.21. Aditiva: el servidor y la web anteriores no usan estas tablas.
-- NO idempotente (relanzar = error de tabla existente).
-- 1) Vista previa: el SELECT debe devolver 0 (no existe).
-- 2) Ejecutar los dos CREATE TABLE (Taller_cambio y Taller_aviso).
-- 3) Comprobación final: CREADAS debe dar 2.
-- Vuelta atrás: primero el servidor anterior; nunca DROP de estas tablas con el servidor 0.9.21 en marcha.
-- FECHA y CERRADO_EN van en hora de Madrid, como el espejo de NSYS: las pone el servidor.
-- ══════════════════════════════════════════════════════════════════════════════
USE gestion_reparaciones;
SET NAMES utf8mb4;

-- Vista previa (no modifica nada): debe dar 0
SELECT COUNT(*) AS YA_EXISTE
  FROM information_schema.TABLES
 WHERE TABLE_SCHEMA = 'gestion_reparaciones' AND TABLE_NAME IN ('Taller_cambio', 'Taller_aviso');

-- Lo del taller por encima de NSYS, por IMEI (no por lote): un cambio por fila. Como mucho un VIGENTE por IMEI y dato (lo
-- garantiza el servidor). QUITADO: un cambio a mano que vuelve a lo de NSYS (nace cerrado). SUSTITUIDO: lo tapó otro cambio
-- del mismo dato, que apunta a él con ID_CAMBIO_ANTERIOR.
CREATE TABLE Taller_cambio (
    ID_CAMBIO          INT          NOT NULL AUTO_INCREMENT,
    IMEI_SN            VARCHAR(40)  NOT NULL COMMENT 'Como Nsys_movil.IMEI_SN',
    DATO               ENUM('COLOR','GRADO') NOT NULL,
    VALOR              VARCHAR(40)  NULL COMMENT 'Lo del taller: color del maestro o grado como NSYS ([Ms]<letra>[100]); NULL solo al volver a un NSYS sin ese dato',
    VALOR_NSYS         VARCHAR(40)  NULL COMMENT 'Lo que NSYS tenía al hacer el cambio',
    ORIGEN             ENUM('MANO','REPARACION') NOT NULL DEFAULT 'MANO',
    ID_REP             VARCHAR(30)  NULL COMMENT 'Pieza 2: la reparación que lo cambió',
    ID_USU             INT          NULL,
    MOTIVO             VARCHAR(500) NULL,
    FECHA              DATETIME     NOT NULL,
    ESTADO             ENUM('VIGENTE','PASADO','MANDA_NSYS','QUITADO','SUSTITUIDO') NOT NULL DEFAULT 'VIGENTE',
    CERRADO_EN         DATETIME     NULL,
    VALOR_CIERRE       VARCHAR(40)  NULL COMMENT 'PASADO y MANDA_NSYS: lo que NSYS tenía al cerrarse',
    ID_CAMBIO_ANTERIOR INT          NULL COMMENT 'El VIGENTE del mismo IMEI y dato al que sustituye (pasa a SUSTITUIDO)',
    PRIMARY KEY (ID_CAMBIO),
    KEY idx_taller_cambio_imei (IMEI_SN, DATO, ESTADO),
    KEY idx_taller_cambio_estado (ESTADO),
    KEY idx_taller_cambio_rep (ID_REP),
    CONSTRAINT fk_taller_cambio_usuario FOREIGN KEY (ID_USU) REFERENCES Usuario (ID_USU) ON DELETE SET NULL,
    CONSTRAINT fk_taller_cambio_anterior FOREIGN KEY (ID_CAMBIO_ANTERIOR) REFERENCES Taller_cambio (ID_CAMBIO)
);

-- Pieza 2 (lo automático desde las reparaciones): lo que el taller avisa al guardar, editar o borrar una reparación sin
-- cambiar el dato (el color de un chasis que no se puede deducir, un grado que no se puede calcular, un cambio que se
-- deshace y uno que ya no se puede deshacer). Solo se añade; lo lee el rastro del IMEI.
CREATE TABLE Taller_aviso (
    ID_AVISO   INT          NOT NULL AUTO_INCREMENT,
    IMEI_SN    VARCHAR(40)  NOT NULL COMMENT 'Como Nsys_movil.IMEI_SN',
    ID_REP     VARCHAR(30)  NOT NULL COMMENT 'La reparación R… (puede estar ya borrada)',
    TIPO       ENUM('SIN_COLOR','SIN_GRADO','DESHECHO','SIN_DESHACER') NOT NULL,
    CAUSA      ENUM('BORRADA','EDITADA') NULL COMMENT 'DESHECHO y SIN_DESHACER: por qué',
    DATO       ENUM('COLOR','GRADO') NOT NULL,
    DE         VARCHAR(100) NULL COMMENT 'SIN_COLOR: el SKU; SIN_GRADO: el grado; DESHECHO y SIN_DESHACER: lo que puso la reparación',
    A          VARCHAR(40)  NULL COMMENT 'DESHECHO: lo que vuelve a valer',
    MOTIVO     VARCHAR(500) NULL COMMENT 'La pieza: «chasis chai13red nuevo»',
    ID_USU     INT          NULL,
    FECHA      DATETIME     NOT NULL,
    PRIMARY KEY (ID_AVISO),
    KEY idx_taller_aviso_imei (IMEI_SN, FECHA),
    KEY idx_taller_aviso_rep (ID_REP),
    CONSTRAINT fk_taller_aviso_usuario FOREIGN KEY (ID_USU) REFERENCES Usuario (ID_USU) ON DELETE SET NULL
);

-- Comprobación final (no modifica nada): debe dar 2; si no, NO desplegar el servidor 0.9.21 (sin estas tablas falla
-- el guardado de reparaciones entero).
SELECT COUNT(*) AS CREADAS FROM information_schema.TABLES WHERE TABLE_SCHEMA = 'gestion_reparaciones' AND TABLE_NAME IN ('Taller_cambio', 'Taller_aviso');
