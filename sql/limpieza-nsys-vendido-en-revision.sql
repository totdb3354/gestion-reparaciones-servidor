-- 0.9.18.2: limpieza de lo que dejó guardado el fallo de la caja vacía (spec en el repo raíz:
-- docs/superpowers/specs/2026-10-20-v09182-vendido-no-en-revision-design.md, §3).
-- Se pasa DESPUÉS de desplegar el backend 0.9.18.2 (el importador viejo volvería a dejar «En revisión» en Nsys_movil) y
-- mejor justo después de una importación, sin ninguna en marcha.
-- Todo en una transacción. Ensayo sin escribir: cambiar el COMMIT del final por ROLLBACK (el guion lo hace con sed).
-- Nsys_evento solo se añade; esto es la excepción, para quitar o corregir eventos que nunca pasaron.
-- Antes y después: recuento-nsys-vendido-en-revision.sql (las selecciones A, B y C son las mismas).
SET SESSION TRANSACTION ISOLATION LEVEL READ COMMITTED;
SET @sin_caja = (SELECT UBICACION FROM Nsys_ubicacion WHERE LOCATION = '');
SELECT @sin_caja AS sin_caja, HEX(@sin_caja) AS hex;
DROP TEMPORARY TABLE IF EXISTS tmp_candidatos, tmp_falsos, tmp_desde_cand, tmp_desde;

START TRANSACTION;

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

-- 1) Los cambios de caja de un lote cerrado se quedan sin el «Desde» falso.
UPDATE Nsys_evento e JOIN tmp_desde t ON t.ID_EVENTO = e.ID_EVENTO SET e.DE = NULL;
SELECT ROW_COUNT() AS desde_quitados;

-- 2) Los falsos se quedan sin ubicación de destino: así el paso 3 no los toma por la última caja, pero sí su DE (la caja
--    de antes de cerrarse). Las ENTRADA se quedan así; los UBICACION se borran en el paso 4.
UPDATE Nsys_evento e JOIN tmp_falsos f ON f.ID_EVENTO = e.ID_EVENTO SET e.A = NULL;
SELECT ROW_COUNT() AS falsos_sin_destino;

-- 3) Vendidos y bajas sin caja: sin ubicación y, de última, la última ubicación del ERP del rastro (el A de un cambio de
--    caja o de una entrada o, si no lo tiene, su DE cuando es una ubicación del ERP), o nada. El código nuevo la deja
--    tal cual, así la importación siguiente no vuelve a guardar estas filas.
UPDATE Nsys_movil m
   SET m.UBICACION = NULL,
       m.UBICACION_ULTIMA = (SELECT COALESCE(e.A, e.DE) FROM Nsys_evento e
                              WHERE e.IMEI_SN = m.IMEI_SN AND e.BATCH = m.BATCH
                                AND e.TIPO IN ('UBICACION', 'ENTRADA')
                                AND (e.A IS NOT NULL
                                     OR e.DE IN (SELECT n.UBICACION FROM Nsys_ubicacion n WHERE n.UBICACION IS NOT NULL))
                              ORDER BY e.FECHA DESC, e.ID_EVENTO DESC
                              LIMIT 1)
 WHERE (m.STATUS IS NULL OR m.STATUS NOT IN ('Available', 'InProcess', 'Repair')) AND m.LOCATION = '';
SELECT ROW_COUNT() AS cerrados_cambiados;

-- 3b) De pedido tras una venta deshecha con el código viejo: la última caja era la «En revisión» falsa. Se saca del
--     rastro igual que en 3); a uno vendido de verdad desde «sin caja» le sale la misma, y sin rastro se deja como está.
UPDATE Nsys_movil m
   SET m.UBICACION_ULTIMA = COALESCE((SELECT COALESCE(e.A, e.DE) FROM Nsys_evento e
                                       WHERE e.IMEI_SN = m.IMEI_SN AND e.BATCH = m.BATCH
                                         AND e.TIPO IN ('UBICACION', 'ENTRADA')
                                         AND (e.A IS NOT NULL
                                              OR e.DE IN (SELECT n.UBICACION FROM Nsys_ubicacion n
                                                           WHERE n.UBICACION IS NOT NULL))
                                       ORDER BY e.FECHA DESC, e.ID_EVENTO DESC
                                       LIMIT 1), m.UBICACION_ULTIMA)
 WHERE m.ESTADO = 'DE_PEDIDO' AND m.UBICACION_ULTIMA = @sin_caja;
SELECT ROW_COUNT() AS de_pedido_cambiados;

-- 4) Se borran los «Pasa a En revisión» falsos.
DELETE e FROM Nsys_evento e JOIN tmp_falsos f ON f.ID_EVENTO = e.ID_EVENTO WHERE f.TIPO = 'UBICACION';
SELECT ROW_COUNT() AS eventos_borrados;

COMMIT;
