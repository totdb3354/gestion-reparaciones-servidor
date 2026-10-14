-- ══════════════════════════════════════════════════════════════════════════════
-- reconstruir-bloques-otros.sql — los otros pedidos que ya existen entran en bloques antiguos (0.9.15, spec §3.3)
-- Mismo patrón que reconstruir-bloques-compra.sql (0.9.9), pero SOLO con bloques RECONSTRUIDO: un otro recibido
-- metido en un bloque actual abierto lo dejaría «en curso» y «Nuevo pedido» dejaría de sumarle líneas.
-- REPETIBLE: solo toca otros sin bloque (o en el bloque de otro proveedor) y bloques vacíos; es seguro con el servidor
-- 0.9.15 funcionando. Se ejecuta justo después de migracion-otros-en-bloques.sql, otra vez después de desplegar el
-- servidor 0.9.15 (recoge los otros que el 0.9.14 creó en el hueco) y después de cualquier vuelta atrás al 0.9.14.
-- En el hueco (bloques antiguos con otros y servidor 0.9.14), vaciar uno de esos bloques desde el 0.9.14 (juntarlo,
-- borrarlo entero o mover su última pieza) falla por la clave ajena sin cambiar nada: el servidor 0.9.15 se despliega
-- justo después.
-- ══════════════════════════════════════════════════════════════════════════════
USE gestion_reparaciones;

-- Se compara con la del final: tiene que ser la misma.
SELECT MAX(UPDATED_AT) AS antes_ultima_modificacion_otros FROM Compra_otro;

-- ── Reparación ────────────────────────────────────────────────────────────────
-- Lo que un 0.9.14 hace sobre el esquema nuevo (hueco o vuelta atrás): editar el proveedor de un otro lo deja en el
-- bloque de otro proveedor. Se suelta (sin tocar UPDATED_AT) y la reconstrucción de abajo lo recoge.
UPDATE Compra_otro co JOIN Bloque_compra b ON b.ID_BLOQUE = co.ID_BLOQUE
SET co.ID_BLOQUE = NULL, co.UPDATED_AT = co.UPDATED_AT
WHERE b.ID_PROV <> co.ID_PROV;

-- ── Bloques antiguos nuevos ───────────────────────────────────────────────────
-- Uno RECONSTRUIDO por cada (proveedor, día de FECHA_PEDIDO) con otros sin bloque que aún no tenga uno; su FECHA es la
-- FECHA_PEDIDO más antigua del grupo. Por orden de fecha, para que entre ellos el número siga el orden cronológico.
INSERT INTO Bloque_compra (ID_PROV, FECHA, RECONSTRUIDO)
SELECT co.ID_PROV, MIN(co.FECHA_PEDIDO), TRUE
FROM Compra_otro co
WHERE co.ID_BLOQUE IS NULL
  AND NOT EXISTS (SELECT 1 FROM Bloque_compra b
                  WHERE b.RECONSTRUIDO AND b.ID_PROV = co.ID_PROV AND DATE(b.FECHA) = DATE(co.FECHA_PEDIDO))
GROUP BY co.ID_PROV, DATE(co.FECHA_PEDIDO)
ORDER BY MIN(co.FECHA_PEDIDO), co.ID_PROV;

-- ── Asignación ────────────────────────────────────────────────────────────────
-- Cada otro sin bloque, al RECONSTRUIDO de su proveedor y día (nunca a un bloque actual). UPDATED_AT = UPDATED_AT: sin
-- él, el ON UPDATE CURRENT_TIMESTAMP cambiaría la fecha de modificación de todos los otros.
UPDATE Compra_otro co
JOIN Bloque_compra b ON b.RECONSTRUIDO AND b.ID_PROV = co.ID_PROV AND DATE(b.FECHA) = DATE(co.FECHA_PEDIDO)
SET co.ID_BLOQUE = b.ID_BLOQUE, co.UPDATED_AT = co.UPDATED_AT
WHERE co.ID_BLOQUE IS NULL;

-- ── Bloques vacíos ────────────────────────────────────────────────────────────
-- Borrar un otro pendiente en el 0.9.14, o la reparación de arriba, puede haber dejado un bloque sin líneas: un bloque
-- sin piezas ni otros no existe.
DELETE b FROM Bloque_compra b
WHERE NOT EXISTS (SELECT 1 FROM Compra_componente cc WHERE cc.ID_BLOQUE = b.ID_BLOQUE)
  AND NOT EXISTS (SELECT 1 FROM Compra_otro co WHERE co.ID_BLOQUE = b.ID_BLOQUE);

-- ── Comprobación ──────────────────────────────────────────────────────────────
SELECT COUNT(*) AS otros_sin_bloque FROM Compra_otro WHERE ID_BLOQUE IS NULL;                                 -- 0
SELECT COUNT(*) AS bloques_vacios FROM Bloque_compra b
 WHERE NOT EXISTS (SELECT 1 FROM Compra_componente cc WHERE cc.ID_BLOQUE = b.ID_BLOQUE)
   AND NOT EXISTS (SELECT 1 FROM Compra_otro co WHERE co.ID_BLOQUE = b.ID_BLOQUE);                            -- 0
SELECT COUNT(*) AS otros_de_otro_proveedor FROM Compra_otro co
  JOIN Bloque_compra b ON b.ID_BLOQUE = co.ID_BLOQUE WHERE b.ID_PROV <> co.ID_PROV;                           -- 0
SELECT COUNT(*) AS reconstruidos FROM Bloque_compra WHERE RECONSTRUIDO;
SELECT MAX(UPDATED_AT) AS ultima_modificacion_otros FROM Compra_otro;  -- igual que antes_ultima_modificacion_otros
