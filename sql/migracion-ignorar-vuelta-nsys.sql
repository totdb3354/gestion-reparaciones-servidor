-- ══════════════════════════════════════════════════════════════════════════════
-- migracion-ignorar-vuelta-nsys.sql — 0.9.27, «ignorar» en los vendidos y de baja que salen en ámbar
-- (spec docs/superpowers/specs/2026-11-06-v0927-ignorar-vendidos-design.md §3, raíz).
-- La aplica el usuario a mano en la VM, con vista previa, ANTES de desplegar el servidor de la 0.9.27.
-- Aditiva: el servidor anterior no lee la tabla.
-- NO idempotente (relanzar = error de tabla existente).
-- 1) Vista previa: YA_EXISTEN debe dar 0.
-- 2) CREATE TABLE Taller_vuelta_ignorada.
-- 3) Comprobación final: CREADAS debe dar 1.
-- Vuelta atrás: el servidor anterior funciona con la tabla puesta; no hace falta quitarla.
-- ══════════════════════════════════════════════════════════════════════════════
USE gestion_reparaciones;
SET NAMES utf8mb4;

-- Vista previa (no modifica nada): debe dar 0
SELECT COUNT(*) AS YA_EXISTEN FROM information_schema.TABLES
 WHERE TABLE_SCHEMA = 'gestion_reparaciones' AND TABLE_NAME = 'Taller_vuelta_ignorada';

-- «ignorar» (spec §3): un vendido o de baja de NSYS que salía en ámbar en «Por pasar a NSYS» y no ha vuelto, con quién y cuándo.
-- Vale mientras el taller no le haga un trabajo nuevo; se borra sola cuando NSYS lo vuelve a tener en la empresa.
CREATE TABLE Taller_vuelta_ignorada (
    IMEI_SN  VARCHAR(40)  NOT NULL COMMENT 'El IMEI del espejo, como Taller_hecho',
    ID_USU   INT          NULL,
    FECHA    DATETIME     NOT NULL COMMENT 'Cuándo se ignoró (hora de Madrid)',
    PRIMARY KEY (IMEI_SN),
    CONSTRAINT fk_taller_vuelta_ignorada_usuario FOREIGN KEY (ID_USU) REFERENCES Usuario (ID_USU) ON DELETE SET NULL
);

-- Comprobación final (no modifica nada): debe dar 1; si no, NO desplegar el servidor de la 0.9.27 (lee la tabla).
SELECT COUNT(*) AS CREADAS FROM information_schema.TABLES
 WHERE TABLE_SCHEMA = 'gestion_reparaciones' AND TABLE_NAME = 'Taller_vuelta_ignorada';
