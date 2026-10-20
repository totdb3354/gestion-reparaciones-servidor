-- 0.9.18.2: recuento de lo que arregla limpieza-nsys-vendido-en-revision.sql. Solo lee: las tablas temporales son de
-- la sesión. Se pasa antes y después de la limpieza. Spec en el repo raíz:
-- docs/superpowers/specs/2026-10-20-v09182-vendido-no-en-revision-design.md, §3.
-- NSYS vacía la caja al vender o dar de baja un móvil, y el ERP lo leía como «sin caja»: un «Pasa a En revisión» falso
-- en el rastro, un «Desde: En revisión» falso en el cambio de caja siguiente y la ubicación «En revisión» en la ficha.
-- Los nombres con tilde salen de Nsys_ubicacion, para no teclearlos por ssh. READ COMMITTED: lee sin bloquear a la
-- importación.
SET SESSION TRANSACTION ISOLATION LEVEL READ COMMITTED;
SET @sin_caja = (SELECT UBICACION FROM Nsys_ubicacion WHERE LOCATION = '');
SET @devolucion = (SELECT UBICACION FROM Nsys_ubicacion WHERE LOCATION = 'Funcionalidad pendiente');
SELECT @sin_caja AS sin_caja, HEX(@sin_caja) AS hex, @devolucion AS devolucion;
SELECT COUNT(*) AS eventos FROM Nsys_evento;
DROP TEMPORARY TABLE IF EXISTS tmp_candidatos, tmp_falsos, tmp_desde_cand, tmp_desde;

-- A) Eventos que dejan el lote «sin caja» (UBICACION o ENTRADA con la caja de NSYS vacía), con el último cambio de estado
--    con hora hasta ese momento y el primero después. El esqueleto (solo el día) no cuenta.
CREATE TEMPORARY TABLE tmp_candidatos (PRIMARY KEY (ID_EVENTO))
SELECT u.ID_EVENTO, u.TIPO, u.IMEI_SN, u.BATCH, u.FECHA,
       (SELECT s.ID_EVENTO FROM Nsys_evento s
         WHERE s.IMEI_SN = u.IMEI_SN AND s.BATCH = u.BATCH AND NOT s.SOLO_DIA
           AND s.TIPO IN ('VENTA', 'VENTA_DESHECHA', 'ESTADO') AND s.FECHA <= u.FECHA
         ORDER BY s.FECHA DESC, s.ID_EVENTO DESC LIMIT 1) AS ID_HASTA,
       (SELECT s.ID_EVENTO FROM Nsys_evento s
         WHERE s.IMEI_SN = u.IMEI_SN AND s.BATCH = u.BATCH AND NOT s.SOLO_DIA
           AND s.TIPO IN ('VENTA', 'VENTA_DESHECHA', 'ESTADO') AND s.FECHA > u.FECHA
         ORDER BY s.FECHA, s.ID_EVENTO LIMIT 1) AS ID_DESPUES
  FROM Nsys_evento u
 WHERE u.TIPO IN ('UBICACION', 'ENTRADA') AND u.A = @sin_caja AND COALESCE(u.A_NSYS, '') = '';

-- B) Los falsos: el lote estaba cerrado en ese momento. Su Status es el A del último cambio de estado hasta entonces; si
--    no lo hay, el DE del primero después; si tampoco, el Status de hoy.
CREATE TEMPORARY TABLE tmp_falsos (PRIMARY KEY (ID_EVENTO))
SELECT c.ID_EVENTO, c.TIPO, (h.FECHA <=> c.FECHA) AS MISMA_HORA
  FROM tmp_candidatos c
  LEFT JOIN Nsys_evento h ON h.ID_EVENTO = c.ID_HASTA
  LEFT JOIN Nsys_evento sg ON sg.ID_EVENTO = c.ID_DESPUES
  LEFT JOIN Nsys_movil m ON m.IMEI_SN = c.IMEI_SN AND m.BATCH = c.BATCH
 WHERE (h.ID_EVENTO IS NOT NULL OR sg.ID_EVENTO IS NOT NULL OR m.IMEI_SN IS NOT NULL)
   AND COALESCE(CASE WHEN h.ID_EVENTO IS NOT NULL THEN h.A
                     WHEN sg.ID_EVENTO IS NOT NULL THEN sg.DE
                     ELSE m.STATUS END, '') NOT IN ('Available', 'InProcess', 'Repair');

-- C) Cambios de caja con «Desde: En revisión» de un lote cerrado justo antes: una vuelta de vendido o de baja, o una baja
--    que cambia de caja (a «Desguace», por ejemplo). El Status de justo antes es el DE del cambio de estado de esa misma
--    exportación; si no lo hay, el A del último anterior; si no, el DE del primero después; si no, el de hoy.
CREATE TEMPORARY TABLE tmp_desde_cand (PRIMARY KEY (ID_EVENTO))
SELECT u.ID_EVENTO, u.IMEI_SN, u.BATCH,
       (SELECT s.ID_EVENTO FROM Nsys_evento s
         WHERE s.IMEI_SN = u.IMEI_SN AND s.BATCH = u.BATCH AND NOT s.SOLO_DIA
           AND s.TIPO IN ('VENTA', 'VENTA_DESHECHA', 'ESTADO') AND s.FECHA = u.FECHA
         ORDER BY s.ID_EVENTO LIMIT 1) AS ID_A_LA_VEZ,
       (SELECT s.ID_EVENTO FROM Nsys_evento s
         WHERE s.IMEI_SN = u.IMEI_SN AND s.BATCH = u.BATCH AND NOT s.SOLO_DIA
           AND s.TIPO IN ('VENTA', 'VENTA_DESHECHA', 'ESTADO') AND s.FECHA < u.FECHA
         ORDER BY s.FECHA DESC, s.ID_EVENTO DESC LIMIT 1) AS ID_ANTES,
       (SELECT s.ID_EVENTO FROM Nsys_evento s
         WHERE s.IMEI_SN = u.IMEI_SN AND s.BATCH = u.BATCH AND NOT s.SOLO_DIA
           AND s.TIPO IN ('VENTA', 'VENTA_DESHECHA', 'ESTADO') AND s.FECHA > u.FECHA
         ORDER BY s.FECHA, s.ID_EVENTO LIMIT 1) AS ID_DESPUES
  FROM Nsys_evento u
 WHERE u.TIPO = 'UBICACION' AND u.DE = @sin_caja;
CREATE TEMPORARY TABLE tmp_desde (PRIMARY KEY (ID_EVENTO))
SELECT c.ID_EVENTO, (v.ID_EVENTO IS NOT NULL) AS A_LA_VEZ
  FROM tmp_desde_cand c
  LEFT JOIN Nsys_evento v ON v.ID_EVENTO = c.ID_A_LA_VEZ
  LEFT JOIN Nsys_evento an ON an.ID_EVENTO = c.ID_ANTES
  LEFT JOIN Nsys_evento sg ON sg.ID_EVENTO = c.ID_DESPUES
  LEFT JOIN Nsys_movil m ON m.IMEI_SN = c.IMEI_SN AND m.BATCH = c.BATCH
 WHERE (v.ID_EVENTO IS NOT NULL OR an.ID_EVENTO IS NOT NULL OR sg.ID_EVENTO IS NOT NULL OR m.IMEI_SN IS NOT NULL)
   AND COALESCE(CASE WHEN v.ID_EVENTO IS NOT NULL THEN v.DE
                     WHEN an.ID_EVENTO IS NOT NULL THEN an.A
                     WHEN sg.ID_EVENTO IS NOT NULL THEN sg.DE
                     ELSE m.STATUS END, '') NOT IN ('Available', 'InProcess', 'Repair');

-- 1) Lo que dejan las selecciones.
SELECT (SELECT COUNT(*) FROM tmp_candidatos) AS a_sin_caja, (SELECT COUNT(*) FROM tmp_falsos) AS falsos;
SELECT TIPO, COUNT(*) AS falsos, COALESCE(SUM(MISMA_HORA), 0) AS con_el_cambio_de_estado,
       COALESCE(SUM(NOT MISMA_HORA), 0) AS sin_cambio_a_la_vez
  FROM tmp_falsos GROUP BY TIPO;
SELECT COUNT(*) AS desde_falsos, COALESCE(SUM(A_LA_VEZ), 0) AS con_cambio_de_estado_a_la_vez FROM tmp_desde;

-- 2) Comprobaciones: tienen que dar 0 o poco, y si no, se miran unos casos antes de limpiar.
--    Falsos de un lote vivo hoy sin ninguna vuelta después: no cuadraría.
SELECT COUNT(*) AS falsos_dudosos
  FROM tmp_falsos f
  JOIN Nsys_evento u ON u.ID_EVENTO = f.ID_EVENTO
  JOIN Nsys_movil m ON m.IMEI_SN = u.IMEI_SN AND m.BATCH = u.BATCH
 WHERE m.STATUS IN ('Available', 'InProcess', 'Repair')
   AND NOT EXISTS (SELECT 1 FROM Nsys_evento s
                    WHERE s.IMEI_SN = u.IMEI_SN AND s.BATCH = u.BATCH AND NOT s.SOLO_DIA
                      AND s.TIPO IN ('VENTA', 'VENTA_DESHECHA', 'ESTADO') AND s.FECHA > u.FECHA);
--    Móviles vivos con fecha de venta (para saber si el esqueleto les apuntó una venta).
SELECT COUNT(*) AS vivos_con_fecha_de_venta
  FROM Nsys_movil WHERE SOLD_DATE IS NOT NULL AND STATUS IN ('Available', 'InProcess', 'Repair');

-- 3) Vendidos y bajas sin caja en la foto.
SELECT COUNT(*) AS cerrados_sin_caja, COALESCE(SUM(UBICACION IS NOT NULL), 0) AS con_ubicacion,
       COALESCE(SUM(UBICACION_ULTIMA = @sin_caja), 0) AS ultima_sin_caja, COALESCE(SUM(UBICACION_ULTIMA IS NULL), 0) AS sin_ultima
  FROM Nsys_movil
 WHERE (STATUS IS NULL OR STATUS NOT IN ('Available', 'InProcess', 'Repair')) AND LOCATION = '';

-- 3b) De pedido con «En revisión» de última caja: los de verdad (vendidos desde «sin caja») y los de una venta deshecha
--     con el código viejo, que la limpieza saca del rastro.
SELECT COUNT(*) AS de_pedido_con_ultima_sin_caja
  FROM Nsys_movil
 WHERE ESTADO = 'DE_PEDIDO' AND UBICACION_ULTIMA = @sin_caja;

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
