-- ══════════════════════════════════════════════════════════════════════════════
-- migracion-descartar-nsys.sql — 0.9.22 pieza 3, «descartar» en «No está en NSYS»
-- (spec docs/superpowers/specs/2026-10-27-v0922-descartar-y-avisos-design.md §3, raíz).
-- La aplica el usuario a mano en la VM, con vista previa, ANTES de desplegar el
-- servidor de la pieza 3 (después de las migraciones de las piezas 1 y 2).
-- Aditiva: el servidor anterior no lee la tabla.
-- NO idempotente (relanzar = error de tabla existente).
-- 1) Vista previa: YA_EXISTEN debe dar 0.
-- 2) CREATE TABLE Taller_descarte.
-- 3) Comprobación final: CREADAS debe dar 1.
-- Vuelta atrás: el servidor anterior funciona con la tabla puesta; no hace falta quitarla.
-- ══════════════════════════════════════════════════════════════════════════════
USE gestion_reparaciones;
SET NAMES utf8mb4;

-- Vista previa (no modifica nada): debe dar 0
SELECT COUNT(*) AS YA_EXISTEN FROM information_schema.TABLES
 WHERE TABLE_SCHEMA = 'gestion_reparaciones' AND TABLE_NAME = 'Taller_descarte';

-- «descartar» de «No está en NSYS» (spec §3): un IMEI del taller que no va a NSYS (no es un móvil, es de un cliente…), con quién,
-- cuándo y por qué. Vale mientras el taller no le haga un trabajo nuevo; se borra sola cuando NSYS ya conoce el IMEI.
CREATE TABLE Taller_descarte (
    IMEI_SN  VARCHAR(40)  NOT NULL COMMENT 'El IMEI del taller, como Taller_hecho',
    ID_USU   INT          NULL,
    FECHA    DATETIME     NOT NULL COMMENT 'Cuándo se descartó (hora de Madrid)',
    MOTIVO   VARCHAR(500) NOT NULL,
    PRIMARY KEY (IMEI_SN),
    CONSTRAINT fk_taller_descarte_usuario FOREIGN KEY (ID_USU) REFERENCES Usuario (ID_USU) ON DELETE SET NULL
);

-- Comprobación final (no modifica nada): debe dar 1; si no, NO desplegar el servidor de la pieza 3 (lee la tabla).
SELECT COUNT(*) AS CREADAS FROM information_schema.TABLES
 WHERE TABLE_SCHEMA = 'gestion_reparaciones' AND TABLE_NAME = 'Taller_descarte';
