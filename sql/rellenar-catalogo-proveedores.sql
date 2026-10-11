-- 0.9.9 (ampliación): rellena las piezas de cada proveedor con el historial de pedidos (spec 2026-10-11 §3.2).
-- REPETIBLE: INSERT IGNORE, solo añade las parejas que falten. Se ejecuta tras migracion-catalogo-proveedores.sql y otra
-- vez después de desplegar el servidor (recoge los pedidos hechos entre medias).
USE gestion_reparaciones;

-- Cada pareja (proveedor, pieza) con alguna línea no cancelada. Los pedidos nuevos ya van al master; alguno antiguo
-- puede apuntar a un slave: por eso el COALESCE.
INSERT IGNORE INTO Proveedor_componente (ID_PROV, ID_COM)
SELECT DISTINCT cc.ID_PROV, COALESCE(c.ID_COM_MASTER, cc.ID_COM)
FROM Compra_componente cc
JOIN Componente c ON c.ID_COM = cc.ID_COM
WHERE cc.ESTADO <> 'cancelado';

-- ── Comprobación ──────────────────────────────────────────────────────────────
SELECT COUNT(*) AS parejas, COUNT(DISTINCT ID_COM) AS piezas, COUNT(DISTINCT ID_PROV) AS proveedores FROM Proveedor_componente;
SELECT COUNT(*) AS apuntan_a_slave FROM Proveedor_componente pc
  JOIN Componente c ON c.ID_COM = pc.ID_COM WHERE c.ID_COM_MASTER IS NOT NULL;                              -- 0
