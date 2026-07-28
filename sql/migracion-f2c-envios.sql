-- ══════════════════════════════════════════════════════════════════════════════
-- migracion-f2c-envios.sql — F2c: tablas Envio y Envio_Telefono (spec 2026-07-27)
-- La aplica el usuario a mano en la VM con vista previa, ANTES de desplegar el
-- servidor F2c. NO idempotente (relanzar = error de tabla existente).
-- El DROP de REVISION_LOGISTICA va en migracion-f2c-drop-check.sql (DESPUÉS del
-- deploy de servidor y clientes — el servidor viejo aún escribe esa columna).
-- 1) Vista previa: el SELECT debe devolver 0 (ninguna de las dos existe).
-- 2) Ejecutar los dos CREATE TABLE.
-- ══════════════════════════════════════════════════════════════════════════════
USE gestion_reparaciones;

-- Vista previa (no modifica nada): debe dar 0
SELECT COUNT(*) AS YA_EXISTEN
  FROM information_schema.TABLES
 WHERE TABLE_SCHEMA = 'gestion_reparaciones'
   AND TABLE_NAME IN ('Envio', 'Envio_Telefono');

-- Remesa de salida: venta individual (ID_CLI) o mayorista/plataforma (DESTINO_TEXTO).
-- Al menos un destino, validado en servidor. REFERENCIA = albarán/tracking externo.
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

-- Estancia de un teléfono en una remesa; la activa = MAX(ID_ET) con DEVUELTO=0.
-- La devolución marca la fila (DEVUELTO + motivo + fecha + usuario), nunca borra.
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
