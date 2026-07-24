-- ══════════════════════════════════════════════════════════════════════════════
-- migracion-f2b-revision.sql — F2b: tabla Revision (spec 2026-07-21)
-- La aplica el usuario a mano en la VM con vista previa. NO idempotente
-- (relanzar = error de tabla existente). Solo CREATE TABLE: el enum ESTADO de
-- Telefono ya quedó completo en la migración F2a.
-- 1) Vista previa: el SELECT debe devolver 0 (la tabla no existe aún).
-- 2) Ejecutar el CREATE TABLE.
-- ══════════════════════════════════════════════════════════════════════════════
USE gestion_reparaciones;

-- Vista previa (no modifica nada): debe dar 0 filas
SELECT COUNT(*) AS YA_EXISTE
  FROM information_schema.TABLES
 WHERE TABLE_SCHEMA = 'gestion_reparaciones' AND TABLE_NAME = 'Revision';

-- Una fila por PASADA de revisión; la vigente = MAX(ID_REVISION) por IMEI.
-- La fila se crea vacía al pasar a EN_REVISION; FECHA_CREACION = "en revisión desde".
-- Parte guardada ≡ su *_FECHA IS NOT NULL. Checks funcionales: marcado = defecto.
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
