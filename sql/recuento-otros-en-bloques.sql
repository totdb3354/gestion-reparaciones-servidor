-- ══════════════════════════════════════════════════════════════════════════════
-- recuento-otros-en-bloques.sql — recuento ANTES de migracion-otros-en-bloques.sql (0.9.15, spec §3.2)
-- SOLO LEE. No usa Compra_otro.ID_BLOQUE (aún no existe): todos los otros están sin bloque.
-- Dice cuántos otros hay, cuántos entrarán en un bloque antiguo que ya existe (mismo proveedor y día) y cuántos
-- bloques antiguos nuevos creará reconstruir-bloques-otros.sql. La guía decide con él si se migra en horario.
-- ══════════════════════════════════════════════════════════════════════════════
USE gestion_reparaciones;

SELECT COUNT(*) AS otros FROM Compra_otro;
SELECT ESTADO, COUNT(*) AS otros FROM Compra_otro GROUP BY ESTADO ORDER BY ESTADO;

-- Entrarán en un bloque RECONSTRUIDO que ya existe, de su proveedor y del día de su FECHA_PEDIDO.
SELECT COUNT(*) AS a_bloque_antiguo_existente FROM Compra_otro co
 WHERE EXISTS (SELECT 1 FROM Bloque_compra b
                WHERE b.RECONSTRUIDO AND b.ID_PROV = co.ID_PROV AND DATE(b.FECHA) = DATE(co.FECHA_PEDIDO));

-- Informativo: de esos, los que irán a un bloque antiguo con alguna pieza viva (pendiente, en camino o parcial); ese
-- bloque puede cambiar de situación en la web (por ejemplo, de «abierto» a «en curso»).
SELECT COUNT(*) AS a_bloque_antiguo_vivo FROM Compra_otro co
 WHERE EXISTS (SELECT 1 FROM Bloque_compra b
                WHERE b.RECONSTRUIDO AND b.ID_PROV = co.ID_PROV AND DATE(b.FECHA) = DATE(co.FECHA_PEDIDO)
                  AND EXISTS (SELECT 1 FROM Compra_componente cc
                               WHERE cc.ID_BLOQUE = b.ID_BLOQUE AND cc.ESTADO IN ('pendiente','en_camino','parcial')));

-- Bloques antiguos nuevos: un grupo (proveedor, día) de otros sin bloque antiguo de ese proveedor y día.
SELECT COUNT(*) AS bloques_antiguos_nuevos FROM (
    SELECT co.ID_PROV, DATE(co.FECHA_PEDIDO) AS DIA
      FROM Compra_otro co
     WHERE NOT EXISTS (SELECT 1 FROM Bloque_compra b
                        WHERE b.RECONSTRUIDO AND b.ID_PROV = co.ID_PROV AND DATE(b.FECHA) = DATE(co.FECHA_PEDIDO))
     GROUP BY co.ID_PROV, DATE(co.FECHA_PEDIDO)) g;

-- Se apunta: reconstruir-bloques-otros.sql no la cambia.
SELECT MAX(UPDATED_AT) AS ultima_modificacion_otros FROM Compra_otro;

-- Colaciones: la lista de líneas de un bloque une Componente.TIPO con Compra_otro.CONCEPTO y los dos ESTADO con UNION.
-- Las cuatro filas tienen que tener la misma COLLATION_NAME; si no, PARAR y avisar antes de desplegar el servidor.
SELECT TABLE_NAME, COLUMN_NAME, COLLATION_NAME
  FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA = 'gestion_reparaciones'
   AND ((TABLE_NAME = 'Componente'  AND COLUMN_NAME = 'TIPO')
     OR (TABLE_NAME = 'Compra_otro' AND COLUMN_NAME = 'CONCEPTO')
     OR (TABLE_NAME IN ('Compra_componente', 'Compra_otro') AND COLUMN_NAME = 'ESTADO'))
 ORDER BY TABLE_NAME, COLUMN_NAME;
