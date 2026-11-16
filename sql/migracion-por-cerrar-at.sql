-- ══════════════════════════════════════════════════════════════════════════════
-- migracion-por-cerrar-at.sql — Hora de la marca «Por cerrar» (0.9.36)
-- La aplica el usuario a mano en gestion_reparaciones (orden: ALTER → servidor).
-- Aditiva: el servidor anterior la ignora. Las marcas de antes quedan sin hora.
-- ══════════════════════════════════════════════════════════════════════════════

USE gestion_reparaciones;

-- Cuándo se marcó «por cerrar»: ese día los 11/12 de la asignación cuentan como
-- hechos en la carga de técnicos (spec 2026-11-16-carga-por-cerrar-hecho).
ALTER TABLE Reparacion
    ADD COLUMN POR_CERRAR_AT DATETIME NULL AFTER POR_CERRAR;
