-- Migración: flag de chasis en asignaciones de reparación (Fase 1 quick wins, 2026-07).
-- Se fija al crear la asignación (solo tipo Reparación) y se conserva al completar;
-- la UI solo lo usa mientras la fila es asignación pendiente (A%).
ALTER TABLE Reparacion
    ADD COLUMN IF NOT EXISTS ES_CHASIS BOOLEAN NOT NULL DEFAULT FALSE AFTER URGENTE;
