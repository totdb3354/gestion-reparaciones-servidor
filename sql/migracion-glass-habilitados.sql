-- Migración técnicos habilitados para glass (spec 2026-09-05-glass-prediccion-design).
USE gestion_reparaciones;

-- Vista previa antes de aplicar:
--   SELECT COUNT(*) FROM information_schema.COLUMNS
--    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'Tecnico'
--      AND COLUMN_NAME = 'ES_GLASS';  -- esperado: 0

ALTER TABLE Tecnico ADD COLUMN ES_GLASS BOOLEAN NOT NULL DEFAULT FALSE;

-- Verificación post: SELECT ID_TEC, NOMBRE, ES_GLASS FROM Tecnico;  -- todos a 0 (nadie habilitado hasta marcarlo)
