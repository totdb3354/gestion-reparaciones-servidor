-- Calibración del peso 'otro' (spec 2026-09-01 §2): compara los puntos medios de las
-- reparaciones formalizadas con el volumen de reparaciones sin piezas, para decidir
-- si 0,50 es un peso justo. Ejecutar en preprod con: mysql -u ... reparaciones < consulta-calibracion-otro.sql

-- 1) Puntos medios por reparación formalizada (R/G con piezas), con la tabla vigente
SELECT COUNT(DISTINCT r.ID_REP)                                        AS reps_formalizadas,
       ROUND(SUM(dp.PUNTOS * rc.CANTIDAD) / COUNT(DISTINCT r.ID_REP), 2) AS puntos_medios
FROM Reparacion r
JOIN Reparacion_componente rc ON rc.ID_REP = r.ID_REP AND rc.ES_SOLICITUD = 0
LEFT JOIN Componente c ON rc.ID_COM = c.ID_COM
JOIN Dificultad_puntos dp ON dp.CLAVE = CASE
    WHEN c.TIPO IS NULL              THEN 'otro'
    WHEN LOWER(c.TIPO) LIKE 'bat%'   THEN 'bateria'
    WHEN LOWER(c.TIPO) LIKE 'cha%'   THEN 'chasis'
    WHEN LOWER(c.TIPO) LIKE 'cam%'   THEN 'camara'
    WHEN LOWER(c.TIPO) LIKE 'lcd%'   THEN 'pantalla'
    WHEN LOWER(c.TIPO) LIKE 'mc%'    THEN 'marco'
    WHEN LOWER(c.TIPO) LIKE 'g%'     THEN 'glass'
    ELSE 'otro' END
WHERE (r.ID_REP LIKE 'R%' OR r.ID_REP LIKE 'G%') AND r.FECHA_FIN IS NOT NULL;

-- 2) Cuántas reparaciones cerradas NO tienen ninguna pieza (hoy puntuarían 'otro' = 0,50)
SELECT COUNT(*) AS reps_sin_piezas
FROM Reparacion r
WHERE (r.ID_REP LIKE 'R%' OR r.ID_REP LIKE 'G%') AND r.FECHA_FIN IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM Reparacion_componente rc
                   WHERE rc.ID_REP = r.ID_REP AND rc.ES_SOLICITUD = 0);

-- 3) Distribución por nº de piezas (contexto)
SELECT piezas, COUNT(*) AS reps FROM (
    SELECT r.ID_REP, COUNT(rc.ID_RC) AS piezas
    FROM Reparacion r
    LEFT JOIN Reparacion_componente rc ON rc.ID_REP = r.ID_REP AND rc.ES_SOLICITUD = 0
    WHERE (r.ID_REP LIKE 'R%' OR r.ID_REP LIKE 'G%') AND r.FECHA_FIN IS NOT NULL
    GROUP BY r.ID_REP) t
GROUP BY piezas ORDER BY piezas;
