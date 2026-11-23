-- 0.9.42 (spec 2026-11-23-pedidos-cancelar-movil-design.md §4): «Cancelado» y «Falta por enviar» ya no van juntos.
-- Desde la 0.9.42 cancelar quita la falta; esto limpia las filas que ya tuvieran las dos. Idempotente: con 0 filas no cambia nada.
-- Sin apunte en el historial (se anota en docs/modulos/pedidos.md).
SELECT COUNT(*) AS canceladas_con_falta FROM Pedido_fila WHERE MARCA = 'CANCELADO' AND FALTA_ENVIAR_EN IS NOT NULL;
UPDATE Pedido_fila SET FALTA_ENVIAR_EN = NULL, FALTA_ENVIAR_USU = NULL WHERE MARCA = 'CANCELADO' AND FALTA_ENVIAR_EN IS NOT NULL;
SELECT COUNT(*) AS quedan FROM Pedido_fila WHERE MARCA = 'CANCELADO' AND FALTA_ENVIAR_EN IS NOT NULL;
