-- ══════════════════════════════════════════════════════════════════════════════
-- prueba-otros-en-bloques-comprobar.sql — comprobaciones de la base de prueba (0.9.15) tras migrar y reconstruir.
-- Ver prueba-otros-en-bloques.sql. Todas las filas tienen que salir con ok = 1. Al final borra la base de prueba.
-- ══════════════════════════════════════════════════════════════════════════════
USE prueba_otros_bloques;

SELECT 'columna ID_BLOQUE creada' AS caso,
       (SELECT COUNT(*) FROM information_schema.COLUMNS
         WHERE TABLE_SCHEMA = 'prueba_otros_bloques' AND TABLE_NAME = 'Compra_otro' AND COLUMN_NAME = 'ID_BLOQUE') = 1 AS ok
UNION ALL SELECT 'otros sin bloque = 0',
       (SELECT COUNT(*) FROM Compra_otro WHERE ID_BLOQUE IS NULL) = 0
UNION ALL SELECT 'bloques vacios = 0',
       (SELECT COUNT(*) FROM Bloque_compra b
         WHERE NOT EXISTS (SELECT 1 FROM Compra_componente cc WHERE cc.ID_BLOQUE = b.ID_BLOQUE)
           AND NOT EXISTS (SELECT 1 FROM Compra_otro co WHERE co.ID_BLOQUE = b.ID_BLOQUE)) = 0
UNION ALL SELECT 'otros de otro proveedor = 0',
       (SELECT COUNT(*) FROM Compra_otro co JOIN Bloque_compra b ON b.ID_BLOQUE = co.ID_BLOQUE
         WHERE b.ID_PROV <> co.ID_PROV) = 0
UNION ALL SELECT 'reconstruidos = 4 (el 1 y tres nuevos)',
       (SELECT COUNT(*) FROM Bloque_compra WHERE RECONSTRUIDO) = 4
UNION ALL SELECT 'MAX(UPDATED_AT) de los otros sin cambiar',
       (SELECT MAX(UPDATED_AT) FROM Compra_otro) = '2026-10-14 11:30:00'
UNION ALL SELECT 'UPDATED_AT de cada otro sin cambiar',
       (SELECT COUNT(*) FROM Compra_otro WHERE (ID_COMPRA_OTRO, UPDATED_AT) IN (
            (10, '2026-07-02 10:00:00'), (11, '2026-07-02 10:05:00'), (12, '2026-07-04 09:00:00'),
            (13, '2026-08-06 10:00:00'), (14, '2026-10-14 11:30:00'))) = 5
UNION ALL SELECT 'otros 10 y 11 en el bloque antiguo 1',
       (SELECT COUNT(*) FROM Compra_otro WHERE ID_COMPRA_OTRO IN (10, 11) AND ID_BLOQUE = 1) = 2
UNION ALL SELECT 'bloque actual 2 sin otros, con su pieza y sin tocar',
       (SELECT COUNT(*) FROM Compra_otro WHERE ID_BLOQUE = 2) = 0
       AND (SELECT COUNT(*) FROM Compra_componente WHERE ID_BLOQUE = 2) = 1
       AND (SELECT RECONSTRUIDO FROM Bloque_compra WHERE ID_BLOQUE = 2) = 0
UNION ALL SELECT 'otro 12 en un antiguo nuevo de B del 01/07',
       (SELECT COUNT(*) FROM Compra_otro co JOIN Bloque_compra b ON b.ID_BLOQUE = co.ID_BLOQUE
         WHERE co.ID_COMPRA_OTRO = 12 AND b.RECONSTRUIDO AND b.ID_PROV = 2 AND b.FECHA = '2026-07-01 12:00:00') = 1
UNION ALL SELECT 'otro 13 en un antiguo nuevo de A del 05/08',
       (SELECT COUNT(*) FROM Compra_otro co JOIN Bloque_compra b ON b.ID_BLOQUE = co.ID_BLOQUE
         WHERE co.ID_COMPRA_OTRO = 13 AND b.RECONSTRUIDO AND b.ID_PROV = 1 AND b.FECHA = '2026-08-05 10:00:00') = 1
UNION ALL SELECT 'otro 14 en un antiguo nuevo de A del 14/10',
       (SELECT COUNT(*) FROM Compra_otro co JOIN Bloque_compra b ON b.ID_BLOQUE = co.ID_BLOQUE
         WHERE co.ID_COMPRA_OTRO = 14 AND b.RECONSTRUIDO AND b.ID_PROV = 1 AND b.FECHA = '2026-10-14 11:00:00') = 1
UNION ALL SELECT 'bloques nuevos numerados por fecha',
       (SELECT ID_BLOQUE FROM Compra_otro WHERE ID_COMPRA_OTRO = 12) < (SELECT ID_BLOQUE FROM Compra_otro WHERE ID_COMPRA_OTRO = 13)
       AND (SELECT ID_BLOQUE FROM Compra_otro WHERE ID_COMPRA_OTRO = 13) < (SELECT ID_BLOQUE FROM Compra_otro WHERE ID_COMPRA_OTRO = 14);

DROP DATABASE prueba_otros_bloques;
