-- Migración exclusión de técnicos de estadísticas (spec 2026-09-02-estadisticas-puntos-ronda2-design).
USE gestion_reparaciones;

-- Vista previa antes de aplicar:
--   SELECT COUNT(*) FROM information_schema.COLUMNS
--    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'Tecnico'
--      AND COLUMN_NAME = 'ES_ESTADISTICA';  -- esperado: 0

ALTER TABLE Tecnico ADD COLUMN ES_ESTADISTICA BOOLEAN NOT NULL DEFAULT TRUE;

-- Verificación post: SELECT ID_TEC, NOMBRE, ES_ESTADISTICA FROM Tecnico;  -- todos a 1
