-- 0.9.8: cliente guardado en cada trabajo (spec 2026-10-09-v098-cliente-por-trabajo §3).
-- Aditivo: el servidor 0.9.7 sigue funcionando sobre este esquema (no nombra la columna).
--
-- ORDEN DE APLICACIÓN: antes de desplegar el servidor 0.9.8. Antes de aplicar, hacer una copia de la base.
-- DESPUÉS del arranque del 0.9.8: relleno-cliente-por-trabajo.sql, en consola, con su análisis y COMMIT a mano.
-- REEJECUCIÓN: idempotente (IF NOT EXISTS de MariaDB).
-- La clave ajena puede bloquear la escritura en Reparacion unos segundos: aplicarla en un momento sin actividad.
USE gestion_reparaciones;

ALTER TABLE Reparacion
    ADD COLUMN IF NOT EXISTS ID_CLI INT NULL,
    ADD CONSTRAINT fk_reparacion_cliente FOREIGN KEY IF NOT EXISTS (ID_CLI) REFERENCES Cliente (ID_CLI);

-- Verificación post: SELECT COUNT(*) FROM Reparacion WHERE ID_CLI IS NOT NULL;  -- 0
