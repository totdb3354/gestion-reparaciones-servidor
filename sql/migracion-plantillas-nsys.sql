-- ══════════════════════════════════════════════════════════════════════════════
-- migracion-plantillas-nsys.sql — 0.9.22 pieza 2, lo que no cuadra con NSYS
-- (spec docs/superpowers/specs/2026-10-25-v0922-plantillas-nsys-design.md §3.5 y §3.6, raíz).
-- La aplica el usuario a mano en la VM, con vista previa, ANTES de desplegar el
-- servidor de la pieza 2. Aditiva: el servidor anterior no lee la tabla ni las columnas.
-- NO idempotente (relanzar = error de tabla o columna existente).
-- 1) Vista previa: YA_EXISTEN debe dar 0.
-- 2) CREATE TABLE Taller_hecho y ALTER TABLE Taller_aviso.
-- 3) Comprobación final: CREADAS debe dar 3.
-- Vuelta atrás: el servidor anterior funciona con todo puesto; no hace falta quitar nada.
-- ══════════════════════════════════════════════════════════════════════════════
USE gestion_reparaciones;
SET NAMES utf8mb4;

-- Vista previa (no modifica nada): debe dar 0
SELECT (SELECT COUNT(*) FROM information_schema.TABLES
         WHERE TABLE_SCHEMA = 'gestion_reparaciones' AND TABLE_NAME = 'Taller_hecho')
     + (SELECT COUNT(*) FROM information_schema.COLUMNS
         WHERE TABLE_SCHEMA = 'gestion_reparaciones' AND TABLE_NAME = 'Taller_aviso'
           AND COLUMN_NAME IN ('REVISADO_POR', 'REVISADO_EN')) AS YA_EXISTEN;

-- «Hecho» de «Por pasar a NSYS» (spec §3.6): una marca por IMEI y tarjeta, con quién y cuándo. VOLVIO: la importación vio NSYS
-- igual después de marcarla, y la fila vuelve a salir.
CREATE TABLE Taller_hecho (
    IMEI_SN  VARCHAR(40) NOT NULL COMMENT 'Como Nsys_movil.IMEI_SN; en ALTA, el IMEI del taller',
    TARJETA  ENUM('COLOR','GRADO','ALTA') NOT NULL,
    ID_USU   INT         NULL,
    FECHA    DATETIME    NOT NULL COMMENT 'Cuándo se marcó (hora de Madrid)',
    VOLVIO   DATETIME    NULL COMMENT 'La importación vio NSYS igual tras marcarlo: la marca deja de valer',
    PRIMARY KEY (IMEI_SN, TARJETA),
    CONSTRAINT fk_taller_hecho_usuario FOREIGN KEY (ID_USU) REFERENCES Usuario (ID_USU) ON DELETE SET NULL
);

-- «Revisado» de «Revisar a mano» (spec §3.5): quién y cuándo. Taller_aviso tiene unas decenas de filas: el ALTER es inmediato.
ALTER TABLE Taller_aviso
    ADD COLUMN REVISADO_POR INT NULL COMMENT '«Revisado» (0.9.22): quién' AFTER FECHA,
    ADD COLUMN REVISADO_EN DATETIME NULL COMMENT '«Revisado» (0.9.22): cuándo, hora de Madrid' AFTER REVISADO_POR,
    ADD CONSTRAINT fk_taller_aviso_revisado FOREIGN KEY (REVISADO_POR) REFERENCES Usuario (ID_USU) ON DELETE SET NULL;

-- Comprobación final (no modifica nada): debe dar 3; si no, NO desplegar el servidor de la pieza 2 (lee la tabla y las columnas).
SELECT (SELECT COUNT(*) FROM information_schema.TABLES
         WHERE TABLE_SCHEMA = 'gestion_reparaciones' AND TABLE_NAME = 'Taller_hecho')
     + (SELECT COUNT(*) FROM information_schema.COLUMNS
         WHERE TABLE_SCHEMA = 'gestion_reparaciones' AND TABLE_NAME = 'Taller_aviso'
           AND COLUMN_NAME IN ('REVISADO_POR', 'REVISADO_EN')) AS CREADAS;
