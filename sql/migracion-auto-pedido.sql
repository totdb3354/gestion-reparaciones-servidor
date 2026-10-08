-- 0.9.6: marca del pedido automático por componente (spec 2026-10-08 §4.1). En un grupo compartido vale la del master.
-- Aditivo: el servidor 0.9.5 sigue funcionando sobre este esquema. Tras aplicarla no queda ninguna pieza marcada.
--
-- ORDEN DE APLICACIÓN: antes de desplegar el servidor 0.9.6. Antes de aplicar, hacer una copia de la base.
-- REEJECUCIÓN: idempotente (ADD COLUMN IF NOT EXISTS de MariaDB).
USE gestion_reparaciones;

ALTER TABLE Componente ADD COLUMN IF NOT EXISTS AUTO_PEDIDO BOOLEAN NOT NULL DEFAULT FALSE;

-- Verificación post: SELECT COUNT(*) FROM Componente WHERE AUTO_PEDIDO;  -- 0
