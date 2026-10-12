-- 0.9.9 (ajuste): rellena los tipos de pieza de cada proveedor con el historial de pedidos (spec 2026-10-12 §3.2).
-- REPETIBLE: INSERT IGNORE, solo añade los tipos que falten. Se ejecuta tras migracion-catalogo-proveedores.sql y otra
-- vez después de desplegar el servidor (recoge los pedidos hechos entre medias).
-- Solo en el despliegue (antes y justo después de arrancar el servidor): más tarde volvería a añadir los tipos
-- quitados a mano en Proveedores. No crea excepciones: cada tipo entra con todos los modelos.
USE gestion_reparaciones;

-- Cada tipo pedido alguna vez (sin cancelados) a un proveedor de componentes. El prefijo se saca como en el servidor
-- (CatalogoPiezas.prefijo): lo que va antes de la primera «i».
INSERT IGNORE INTO Proveedor_tipo (ID_PROV, PREFIJO)
SELECT DISTINCT cc.ID_PROV, SUBSTRING(c.TIPO, 1, LOCATE('i', c.TIPO) - 1)
FROM Compra_componente cc
JOIN Componente c ON c.ID_COM = cc.ID_COM
JOIN Proveedor p ON p.ID_PROV = cc.ID_PROV AND p.TIPO = 'COMPONENTES'
WHERE cc.ESTADO <> 'cancelado' AND c.TIPO NOT LIKE 'otro%' AND LOCATE('i', c.TIPO) > 1;

-- ── Comprobación ──────────────────────────────────────────────────────────────
SELECT COUNT(*) AS tipos, COUNT(DISTINCT ID_PROV) AS proveedores FROM Proveedor_tipo;
SELECT COUNT(*) AS prefijos_raros FROM Proveedor_tipo pt
 WHERE NOT EXISTS (SELECT 1 FROM Componente c WHERE c.TIPO NOT LIKE 'otro%' AND LOCATE('i', c.TIPO) > 1
                     AND SUBSTRING(c.TIPO, 1, LOCATE('i', c.TIPO) - 1) = pt.PREFIJO);                          -- 0
SELECT COUNT(*) AS excepciones FROM Proveedor_tipo_excluido;                                                  -- 0 en el despliegue
