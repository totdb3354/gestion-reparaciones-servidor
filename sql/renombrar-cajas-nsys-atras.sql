-- Vuelta atrás de renombrar-cajas-nsys.sql: «Caja · …» vuelve a «Taller · …» y «Listos». Solo datos.
-- Ejecutar con cliente utf8mb4 (el «·» y la tilde). Hay que volver también a la web anterior.
SET NAMES utf8mb4;
START TRANSACTION;

UPDATE Nsys_ubicacion SET UBICACION = CASE UBICACION
    WHEN 'Caja · Pulir' THEN 'Taller · Pulir'
    WHEN 'Caja · Glass' THEN 'Taller · Glass'
    WHEN 'Caja · Reparación' THEN 'Taller · Reparación'
    WHEN 'Caja · Pantalla rota' THEN 'Taller · Pantalla rota'
    WHEN 'Caja · Listos' THEN 'Listos' END
 WHERE UBICACION IN ('Caja · Pulir', 'Caja · Glass', 'Caja · Reparación', 'Caja · Pantalla rota', 'Caja · Listos');

UPDATE Nsys_movil SET UBICACION = CASE UBICACION
    WHEN 'Caja · Pulir' THEN 'Taller · Pulir'
    WHEN 'Caja · Glass' THEN 'Taller · Glass'
    WHEN 'Caja · Reparación' THEN 'Taller · Reparación'
    WHEN 'Caja · Pantalla rota' THEN 'Taller · Pantalla rota'
    WHEN 'Caja · Listos' THEN 'Listos' END
 WHERE UBICACION IN ('Caja · Pulir', 'Caja · Glass', 'Caja · Reparación', 'Caja · Pantalla rota', 'Caja · Listos');

UPDATE Nsys_movil SET UBICACION_ULTIMA = CASE UBICACION_ULTIMA
    WHEN 'Caja · Pulir' THEN 'Taller · Pulir'
    WHEN 'Caja · Glass' THEN 'Taller · Glass'
    WHEN 'Caja · Reparación' THEN 'Taller · Reparación'
    WHEN 'Caja · Pantalla rota' THEN 'Taller · Pantalla rota'
    WHEN 'Caja · Listos' THEN 'Listos' END
 WHERE UBICACION_ULTIMA IN ('Caja · Pulir', 'Caja · Glass', 'Caja · Reparación', 'Caja · Pantalla rota', 'Caja · Listos');

-- En Nsys_evento, DE y A solo son ubicaciones en UBICACION (y A también en ENTRADA); en los demás tipos son grados o productos.
UPDATE Nsys_evento SET DE = CASE DE
    WHEN 'Caja · Pulir' THEN 'Taller · Pulir'
    WHEN 'Caja · Glass' THEN 'Taller · Glass'
    WHEN 'Caja · Reparación' THEN 'Taller · Reparación'
    WHEN 'Caja · Pantalla rota' THEN 'Taller · Pantalla rota'
    WHEN 'Caja · Listos' THEN 'Listos' END
 WHERE TIPO = 'UBICACION'
   AND DE IN ('Caja · Pulir', 'Caja · Glass', 'Caja · Reparación', 'Caja · Pantalla rota', 'Caja · Listos');

UPDATE Nsys_evento SET A = CASE A
    WHEN 'Caja · Pulir' THEN 'Taller · Pulir'
    WHEN 'Caja · Glass' THEN 'Taller · Glass'
    WHEN 'Caja · Reparación' THEN 'Taller · Reparación'
    WHEN 'Caja · Pantalla rota' THEN 'Taller · Pantalla rota'
    WHEN 'Caja · Listos' THEN 'Listos' END
 WHERE TIPO IN ('UBICACION', 'ENTRADA')
   AND A IN ('Caja · Pulir', 'Caja · Glass', 'Caja · Reparación', 'Caja · Pantalla rota', 'Caja · Listos');

COMMIT;

-- Comprobación (no modifica nada): debe dar 6. Bytes UTF-8 de los seis nombres con tilde o «·» (Listos no lleva ninguno).
SELECT COUNT(*) AS ubicaciones_utf8_ok
  FROM Nsys_ubicacion
 WHERE HEX(UBICACION) IN (
       '456E20726576697369C3B36E',                          -- En revisión
       '4465766F6C756369C3B36E',                            -- Devolución
       '54616C6C657220C2B72050756C6972',                    -- Taller · Pulir
       '54616C6C657220C2B720476C617373',                    -- Taller · Glass
       '54616C6C657220C2B7205265706172616369C3B36E',        -- Taller · Reparación
       '54616C6C657220C2B72050616E74616C6C6120726F7461');   -- Taller · Pantalla rota

-- Quedan nuevos (debe dar 0 en las tres).
SELECT COUNT(*) AS moviles_nuevos FROM Nsys_movil
 WHERE UBICACION IN ('Caja · Pulir', 'Caja · Glass', 'Caja · Reparación', 'Caja · Pantalla rota', 'Caja · Listos')
    OR UBICACION_ULTIMA IN ('Caja · Pulir', 'Caja · Glass', 'Caja · Reparación', 'Caja · Pantalla rota', 'Caja · Listos');
SELECT COUNT(*) AS eventos_nuevos FROM Nsys_evento
 WHERE TIPO IN ('UBICACION', 'ENTRADA')
   AND (DE IN ('Caja · Pulir', 'Caja · Glass', 'Caja · Reparación', 'Caja · Pantalla rota', 'Caja · Listos')
        OR A IN ('Caja · Pulir', 'Caja · Glass', 'Caja · Reparación', 'Caja · Pantalla rota', 'Caja · Listos'));
SELECT COUNT(*) AS ubicaciones_nuevas FROM Nsys_ubicacion
 WHERE UBICACION IN ('Caja · Pulir', 'Caja · Glass', 'Caja · Reparación', 'Caja · Pantalla rota', 'Caja · Listos');
