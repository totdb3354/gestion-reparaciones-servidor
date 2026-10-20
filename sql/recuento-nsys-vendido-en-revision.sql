-- 0.9.18.2: recuento de lo que arregla limpieza-nsys-vendido-en-revision.sql. Solo lee: las tablas temporales son de
-- la sesión. Se pasa antes y después de la limpieza. Spec en el repo raíz:
-- docs/superpowers/specs/2026-10-20-v09182-vendido-no-en-revision-design.md, §3.
-- NSYS vacía la caja al vender o dar de baja un móvil, y el ERP lo leía como «sin caja»: un «Pasa a En revisión» falso
-- en el rastro y la ubicación «En revisión» en la ficha de un vendido.
-- Los nombres con tilde salen de Nsys_ubicacion, para no teclearlos por ssh.
SET @sin_caja = (SELECT UBICACION FROM Nsys_ubicacion WHERE LOCATION = '');
SET @devolucion = (SELECT UBICACION FROM Nsys_ubicacion WHERE LOCATION = 'Funcionalidad pendiente');
SELECT @sin_caja AS sin_caja, HEX(@sin_caja) AS hex, @devolucion AS devolucion;
SELECT COUNT(*) AS eventos FROM Nsys_evento;

-- 1) «→ En revisión» de una caja vaciada con el lote cerrado. El estado del lote en ese momento es su último evento de
--    estado hasta esa hora; el del esqueleto (solo el día) cuenta al final de su día.
CREATE TEMPORARY TABLE tmp_falsos (ID_EVENTO BIGINT NOT NULL PRIMARY KEY, MISMA_HORA BOOLEAN NOT NULL)
SELECT x.ID_EVENTO, (s.FECHA = x.FECHA AND NOT s.SOLO_DIA) AS MISMA_HORA
  FROM (SELECT u.ID_EVENTO, u.FECHA,
               (SELECT s2.ID_EVENTO FROM Nsys_evento s2
                 WHERE s2.IMEI_SN = u.IMEI_SN AND s2.BATCH = u.BATCH
                   AND s2.TIPO IN ('VENTA', 'VENTA_DESHECHA', 'ESTADO')
                   AND IF(s2.SOLO_DIA, s2.FECHA + INTERVAL 1 DAY - INTERVAL 1 SECOND, s2.FECHA) <= u.FECHA
                 ORDER BY IF(s2.SOLO_DIA, s2.FECHA + INTERVAL 1 DAY - INTERVAL 1 SECOND, s2.FECHA) DESC, s2.ID_EVENTO DESC
                 LIMIT 1) AS ID_ESTADO
          FROM Nsys_evento u
         WHERE u.TIPO = 'UBICACION' AND u.A = @sin_caja AND COALESCE(u.A_NSYS, '') = '') x
  JOIN Nsys_evento s ON s.ID_EVENTO = x.ID_ESTADO
 WHERE s.TIPO = 'VENTA'
    OR (s.TIPO = 'ESTADO' AND (s.A IS NULL OR s.A NOT IN ('Available', 'InProcess', 'Repair')));
SELECT COUNT(*) AS falsos, COALESCE(SUM(MISMA_HORA), 0) AS a_la_hora_del_cierre,
       COALESCE(SUM(NOT MISMA_HORA), 0) AS despues
  FROM tmp_falsos;

-- 2) Vueltas de un vendido o de una baja a una caja con el «Desde: En revisión» falso.
CREATE TEMPORARY TABLE tmp_vueltas (ID_EVENTO BIGINT NOT NULL PRIMARY KEY)
SELECT u.ID_EVENTO
  FROM Nsys_evento u
 WHERE u.TIPO = 'UBICACION' AND u.DE = @sin_caja
   AND EXISTS (SELECT 1 FROM Nsys_evento v
                WHERE v.IMEI_SN = u.IMEI_SN AND v.BATCH = u.BATCH AND v.FECHA = u.FECHA AND NOT v.SOLO_DIA
                  AND (v.TIPO = 'VENTA_DESHECHA'
                       OR (v.TIPO = 'ESTADO' AND (v.DE IS NULL OR v.DE NOT IN ('Available', 'InProcess', 'Repair'))
                           AND v.A IN ('Available', 'InProcess', 'Repair'))));
SELECT COUNT(*) AS vueltas_con_desde_falso FROM tmp_vueltas;

-- 3) Vendidos y bajas sin caja en la foto.
SELECT COUNT(*) AS cerrados_sin_caja, COALESCE(SUM(UBICACION IS NOT NULL), 0) AS con_ubicacion,
       COALESCE(SUM(UBICACION_ULTIMA <=> @sin_caja), 0) AS ultima_sin_caja
  FROM Nsys_movil
 WHERE (STATUS IS NULL OR STATUS NOT IN ('Available', 'InProcess', 'Repair')) AND LOCATION = '';

-- 3b) De pedido con «En revisión» de última caja: los de verdad (vendidos desde «sin caja») y los de una venta deshecha
--     con el código viejo, que la limpieza corrige desde el rastro.
SELECT COUNT(*) AS de_pedido_con_ultima_sin_caja
  FROM Nsys_movil
 WHERE ESTADO = 'DE_PEDIDO' AND UBICACION_ULTIMA <=> @sin_caja;

-- 4) Móviles en la empresa pendientes de revisar tras una vuelta: para mirar su ficha antes y después.
SELECT m.IMEI_SN, m.BATCH, m.UBICACION, MAX(v.FECHA) AS vuelta
  FROM Nsys_movil m
  JOIN Nsys_evento v ON v.IMEI_SN = m.IMEI_SN AND v.BATCH = m.BATCH AND NOT v.SOLO_DIA
       AND (v.TIPO = 'VENTA_DESHECHA'
            OR (v.TIPO = 'ESTADO' AND (v.DE IS NULL OR v.DE NOT IN ('Available', 'InProcess', 'Repair'))
                AND v.A IN ('Available', 'InProcess', 'Repair')))
 WHERE m.ESTADO IN ('STOCK', 'DE_PEDIDO', 'NO_CUENTA') AND m.UBICACION IN (@sin_caja, @devolucion)
 GROUP BY m.IMEI_SN, m.BATCH, m.UBICACION
 ORDER BY vuelta DESC
 LIMIT 10;
