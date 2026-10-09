-- 0.9.9: reconstrucción de los bloques de las líneas que no tienen (spec «Pedidos por bloques» §4).
-- REPETIBLE: solo toca líneas sin bloque. Se ejecuta tras migracion-bloques-compra.sql, otra vez después de desplegar
-- el servidor 0.9.9 (recoge las líneas que el 0.9.8 creó en el hueco) y después de cualquier vuelta atrás al 0.9.8.
USE gestion_reparaciones;

-- ── Reconstrucción ────────────────────────────────────────────────────────────
-- Un bloque RECONSTRUIDO por cada (proveedor, día de FECHA_PEDIDO) con líneas sin bloque que aún no tenga uno; su
-- FECHA es la FECHA_PEDIDO más antigua del grupo.
INSERT INTO Bloque_compra (ID_PROV, FECHA, RECONSTRUIDO)
SELECT cc.ID_PROV, MIN(cc.FECHA_PEDIDO), TRUE
FROM Compra_componente cc
WHERE cc.ID_BLOQUE IS NULL
  AND NOT EXISTS (SELECT 1 FROM Bloque_compra b
                  WHERE b.RECONSTRUIDO AND b.ID_PROV = cc.ID_PROV AND DATE(b.FECHA) = DATE(cc.FECHA_PEDIDO))
GROUP BY cc.ID_PROV, DATE(cc.FECHA_PEDIDO);

-- Cada línea sin bloque, al reconstruido de su proveedor y día. UPDATED_AT = UPDATED_AT: sin él, el
-- ON UPDATE CURRENT_TIMESTAMP cambiaría la fecha de modificación de todas las líneas.
UPDATE Compra_componente cc
JOIN Bloque_compra b ON b.RECONSTRUIDO AND b.ID_PROV = cc.ID_PROV AND DATE(b.FECHA) = DATE(cc.FECHA_PEDIDO)
SET cc.ID_BLOQUE = b.ID_BLOQUE, cc.UPDATED_AT = cc.UPDATED_AT
WHERE cc.ID_BLOQUE IS NULL;

-- ── Comprobación ──────────────────────────────────────────────────────────────
SELECT COUNT(*) AS lineas_sin_bloque FROM Compra_componente WHERE ID_BLOQUE IS NULL;                         -- 0
SELECT COUNT(*) AS bloques_vacios FROM Bloque_compra b
 WHERE NOT EXISTS (SELECT 1 FROM Compra_componente cc WHERE cc.ID_BLOQUE = b.ID_BLOQUE);                  -- 0
SELECT COUNT(*) AS lineas_de_otro_proveedor FROM Compra_componente cc
  JOIN Bloque_compra b ON b.ID_BLOQUE = cc.ID_BLOQUE WHERE b.ID_PROV <> cc.ID_PROV;                       -- 0
SELECT COUNT(*) AS reconstruidos FROM Bloque_compra WHERE RECONSTRUIDO;
SELECT MAX(UPDATED_AT) AS ultima_modificacion FROM Compra_componente;  -- igual que antes de la migración
