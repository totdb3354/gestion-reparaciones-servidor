-- ══════════════════════════════════════════════════════════════════════════════
-- datos-tapa-trasera.sql — tapa trasera como pieza propia (spec 2026-10-09 v0.9.7 §6)
-- La aplica el usuario a mano DESPUES de desplegar servidor y web 0.9.7 (con el codigo
-- viejo la fila saldria como "tapa", al final y puntuando como "otro"). Idempotente.
-- Las tapas se generan desde los chasis activos sin eSIM de la base donde se ejecuta:
-- mismo nombre con 'tapa' en vez de 'cha' (chai16black → tapai16black).
-- Modelos: 14, 14 Plus, series 15, 16 (con 16e) y 17 (con Air). Sin 14 Pro / 14 Pro Max.
-- ══════════════════════════════════════════════════════════════════════════════

USE gestion_reparaciones;

-- Vista previa (no modifica nada): chasis de los que saldra una tapa. Con el catalogo del 2026-10-09: 65.
SELECT COUNT(*) AS chasis_origen FROM Componente
 WHERE TIPO LIKE 'chai%' AND TIPO NOT LIKE '%esim' AND ACTIVO = 1
   AND TIPO REGEXP '^chai(14|15|16|17|air)' AND TIPO NOT REGEXP '^chai14pro';

-- Puntos de la tapa (antes que las tapas: sin esta fila puntuarian 0)
INSERT IGNORE INTO Dificultad_puntos (CLAVE, PUNTOS) VALUES ('tapa', 1.00);

-- Una tapa por chasis: stock 9999 (como los chasis: su stock no se cuenta), minimo 2, activa, sin grupo compartido
INSERT INTO Componente (TIPO, STOCK, STOCK_MINIMO, ACTIVO)
SELECT CONCAT('tapa', SUBSTRING(c.TIPO, 4)), 9999, 2, 1
  FROM Componente c
 WHERE c.TIPO LIKE 'chai%' AND c.TIPO NOT LIKE '%esim' AND c.ACTIVO = 1
   AND c.TIPO REGEXP '^chai(14|15|16|17|air)' AND c.TIPO NOT REGEXP '^chai14pro'
   AND NOT EXISTS (SELECT 1 FROM Componente t WHERE t.TIPO = CONCAT('tapa', SUBSTRING(c.TIPO, 4)));

-- Comprobacion: puntos_tapa 1.00; tapas = chasis_origen, stock 9999 y 9999 (recien creadas), minimos 2 y 2, todas activas
SELECT PUNTOS AS puntos_tapa FROM Dificultad_puntos WHERE CLAVE = 'tapa';
SELECT COUNT(*) AS tapas, MIN(STOCK) AS min_stock, MAX(STOCK) AS max_stock, MIN(STOCK_MINIMO) AS min_minimo, MAX(STOCK_MINIMO) AS max_minimo,
       SUM(ACTIVO) AS activas
  FROM Componente WHERE TIPO LIKE 'tapai%';
