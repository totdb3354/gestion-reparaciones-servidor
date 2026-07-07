-- Migración de datos (EJECUTADA en preproducción el 2026-07-06, one-off):
-- convertir a tipo Glass todas las reparaciones históricas de los dos técnicos
-- que solo hacen glass ('jhona' y 'javi'), anteriores a la separación Glass.
-- El tipo vive en el prefijo del ID_REP (PK): R→G (historial) y A→AG (asignaciones).
--
-- Resultado real: 1276 filas renombradas (948 R→G, 328 A→AG), 951 filas de
-- Reparacion_componente re-apuntadas, 0 borradores, 0 referencias ID_REP_ANTERIOR.
-- Hubo 2 colisiones de numeración diaria (A20260703_5/_6 → AG20260703_5/_6 ya
-- existían de otro técnico): se les asignó el siguiente número libre del día
-- (AG20260703_63 y _64) editando el mapeo antes de convertir.

-- ── Bloque 0: confirmar técnicos ─────────────────────────────────────────────
SELECT ID_TEC, NOMBRE FROM Tecnico WHERE NOMBRE IN ('jhona', 'javi');

-- ── Bloque 1: mapeo viejo→nuevo + verificación (no toca datos) ───────────────
CREATE TEMPORARY TABLE mapeo AS
SELECT ID_REP AS viejo,
       CONCAT(IF(ID_REP LIKE 'R%', 'G', 'AG'), SUBSTRING(ID_REP, 2)) AS nuevo
FROM Reparacion
WHERE ID_TEC IN (SELECT ID_TEC FROM Tecnico WHERE NOMBRE IN ('jhona', 'javi'))
  AND (ID_REP LIKE 'R%' OR (ID_REP LIKE 'A%' AND ID_REP NOT LIKE 'AG%' AND ID_REP NOT LIKE 'AP%'));

SELECT LEFT(viejo, 1) AS prefijo, COUNT(*) AS filas FROM mapeo GROUP BY LEFT(viejo, 1);
SELECT COUNT(*) AS colisiones FROM mapeo m JOIN Reparacion r ON r.ID_REP = m.nuevo;
-- Si colisiones > 0: listar con
--   SELECT m.viejo, m.nuevo FROM mapeo m JOIN Reparacion r ON r.ID_REP = m.nuevo;
-- y reasignarles el siguiente número libre del día con UPDATE mapeo SET nuevo = ...

-- ── Bloque 2: conversión (todo o nada; requiere colisiones = 0) ──────────────
SET FOREIGN_KEY_CHECKS = 0;
START TRANSACTION;
UPDATE Reparacion r            JOIN mapeo m ON r.ID_REP_ANTERIOR = m.viejo SET r.ID_REP_ANTERIOR = m.nuevo;
UPDATE Reparacion_componente c JOIN mapeo m ON c.ID_REP = m.viejo          SET c.ID_REP = m.nuevo;
UPDATE Reparacion_borrador b   JOIN mapeo m ON b.ID_REP = m.viejo          SET b.ID_REP = m.nuevo;
UPDATE Reparacion r            JOIN mapeo m ON r.ID_REP = m.viejo          SET r.ID_REP = m.nuevo;
COMMIT;
SET FOREIGN_KEY_CHECKS = 1;

-- ── Bloque 3: post-check (debe dar 0) ────────────────────────────────────────
SELECT COUNT(*) AS quedan_sin_convertir FROM Reparacion
WHERE ID_TEC IN (SELECT ID_TEC FROM Tecnico WHERE NOMBRE IN ('jhona', 'javi'))
  AND (ID_REP LIKE 'R%' OR (ID_REP LIKE 'A%' AND ID_REP NOT LIKE 'AG%' AND ID_REP NOT LIKE 'AP%'));
