-- ══════════════════════════════════════════════════════════════════════════════
-- migracion-por-cerrar.sql — Marca "Por cerrar" en asignaciones de reparación
-- La aplica el usuario a mano en gestion_reparaciones (orden: ALTER → servidor → cliente).
-- ══════════════════════════════════════════════════════════════════════════════

USE gestion_reparaciones;

-- Asignación de reparación normal mayoritariamente hecha: solo queda cerrar el
-- móvil (habitualmente esperando el glass de otro técnico). Marca manual.
ALTER TABLE Reparacion
    ADD COLUMN POR_CERRAR BOOLEAN NOT NULL DEFAULT FALSE AFTER ES_CHASIS;
