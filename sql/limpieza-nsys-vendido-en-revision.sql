-- 0.9.18.2: limpieza de lo que dejó guardado el fallo de la caja vacía (spec en el repo raíz:
-- docs/superpowers/specs/2026-10-20-v09182-vendido-no-en-revision-design.md, §3).
-- Se pasa DESPUÉS de desplegar el backend 0.9.18.2: el importador viejo volvería a dejar «En revisión» en Nsys_movil.
-- Todo en una transacción. Ensayo sin escribir: cambiar el COMMIT del final por ROLLBACK (el guion lo hace con sed).
-- Nsys_evento solo se añade; esto es la excepción, para quitar eventos que nunca pasaron.
-- Antes y después: recuento-nsys-vendido-en-revision.sql.
SET @sin_caja = (SELECT UBICACION FROM Nsys_ubicacion WHERE LOCATION = '');
SELECT @sin_caja AS sin_caja, HEX(@sin_caja) AS hex;

START TRANSACTION;

-- 1) Se borran los «→ En revisión» de una caja vaciada con el lote cerrado (misma selección que el recuento).
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
DELETE e FROM Nsys_evento e JOIN tmp_falsos f ON f.ID_EVENTO = e.ID_EVENTO;
SELECT ROW_COUNT() AS eventos_borrados;

-- 2) Las vueltas a una caja se quedan sin el «Desde: En revisión» falso.
CREATE TEMPORARY TABLE tmp_vueltas (ID_EVENTO BIGINT NOT NULL PRIMARY KEY)
SELECT u.ID_EVENTO
  FROM Nsys_evento u
 WHERE u.TIPO = 'UBICACION' AND u.DE = @sin_caja
   AND EXISTS (SELECT 1 FROM Nsys_evento v
                WHERE v.IMEI_SN = u.IMEI_SN AND v.BATCH = u.BATCH AND v.FECHA = u.FECHA AND NOT v.SOLO_DIA
                  AND (v.TIPO = 'VENTA_DESHECHA'
                       OR (v.TIPO = 'ESTADO' AND (v.DE IS NULL OR v.DE NOT IN ('Available', 'InProcess', 'Repair'))
                           AND v.A IN ('Available', 'InProcess', 'Repair'))));
UPDATE Nsys_evento e JOIN tmp_vueltas t ON t.ID_EVENTO = e.ID_EVENTO SET e.DE = NULL;
SELECT ROW_COUNT() AS vueltas_sin_desde;

-- 3) Vendidos y bajas sin caja: sin ubicación y, de última, la última ubicación del rastro (ya sin los falsos), o nada.
--    Es lo que calcula el importador nuevo, así no vuelve a guardar estas filas.
UPDATE Nsys_movil m
   SET m.UBICACION = NULL,
       m.UBICACION_ULTIMA = (SELECT e.A FROM Nsys_evento e
                              WHERE e.IMEI_SN = m.IMEI_SN AND e.BATCH = m.BATCH
                                AND e.TIPO IN ('UBICACION', 'ENTRADA') AND e.A IS NOT NULL
                              ORDER BY e.FECHA DESC, e.ID_EVENTO DESC
                              LIMIT 1)
 WHERE (m.STATUS IS NULL OR m.STATUS NOT IN ('Available', 'InProcess', 'Repair')) AND m.LOCATION = '';
SELECT ROW_COUNT() AS moviles_cambiados;

-- 3b) De pedido tras una venta deshecha con el código viejo: la última caja era la «En revisión» falsa. Se saca del
--     rastro, igual que en 3); a uno vendido de verdad desde «sin caja» le sale la misma, y sin rastro se deja como está.
UPDATE Nsys_movil m
   SET m.UBICACION_ULTIMA = COALESCE((SELECT e.A FROM Nsys_evento e
                                       WHERE e.IMEI_SN = m.IMEI_SN AND e.BATCH = m.BATCH
                                         AND e.TIPO IN ('UBICACION', 'ENTRADA') AND e.A IS NOT NULL
                                       ORDER BY e.FECHA DESC, e.ID_EVENTO DESC
                                       LIMIT 1), m.UBICACION_ULTIMA)
 WHERE m.ESTADO = 'DE_PEDIDO' AND m.UBICACION_ULTIMA <=> @sin_caja;
SELECT ROW_COUNT() AS de_pedido_cambiados;

COMMIT;
