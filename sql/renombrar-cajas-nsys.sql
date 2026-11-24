-- Spec 2026-11-24 §1: las cajas de NSYS se llaman «Caja · …» (antes «Taller · …» y «Listos»). Solo datos; la web nueva sale a la vez.
-- Ejecutar con cliente utf8mb4 (el «·» y la tilde). Vuelta atrás: renombrar-cajas-nsys-atras.sql.
SET NAMES utf8mb4;
START TRANSACTION;

UPDATE Nsys_ubicacion SET UBICACION = CASE UBICACION
    WHEN 'Taller · Pulir' THEN 'Caja · Pulir'
    WHEN 'Taller · Glass' THEN 'Caja · Glass'
    WHEN 'Taller · Reparación' THEN 'Caja · Reparación'
    WHEN 'Taller · Pantalla rota' THEN 'Caja · Pantalla rota'
    WHEN 'Listos' THEN 'Caja · Listos' END
 WHERE UBICACION IN ('Taller · Pulir', 'Taller · Glass', 'Taller · Reparación', 'Taller · Pantalla rota', 'Listos');

UPDATE Nsys_movil SET UBICACION = CASE UBICACION
    WHEN 'Taller · Pulir' THEN 'Caja · Pulir'
    WHEN 'Taller · Glass' THEN 'Caja · Glass'
    WHEN 'Taller · Reparación' THEN 'Caja · Reparación'
    WHEN 'Taller · Pantalla rota' THEN 'Caja · Pantalla rota'
    WHEN 'Listos' THEN 'Caja · Listos' END
 WHERE UBICACION IN ('Taller · Pulir', 'Taller · Glass', 'Taller · Reparación', 'Taller · Pantalla rota', 'Listos');

UPDATE Nsys_movil SET UBICACION_ULTIMA = CASE UBICACION_ULTIMA
    WHEN 'Taller · Pulir' THEN 'Caja · Pulir'
    WHEN 'Taller · Glass' THEN 'Caja · Glass'
    WHEN 'Taller · Reparación' THEN 'Caja · Reparación'
    WHEN 'Taller · Pantalla rota' THEN 'Caja · Pantalla rota'
    WHEN 'Listos' THEN 'Caja · Listos' END
 WHERE UBICACION_ULTIMA IN ('Taller · Pulir', 'Taller · Glass', 'Taller · Reparación', 'Taller · Pantalla rota', 'Listos');

-- En Nsys_evento, DE y A solo son ubicaciones en UBICACION (y A también en ENTRADA); en los demás tipos son grados o productos.
UPDATE Nsys_evento SET DE = CASE DE
    WHEN 'Taller · Pulir' THEN 'Caja · Pulir'
    WHEN 'Taller · Glass' THEN 'Caja · Glass'
    WHEN 'Taller · Reparación' THEN 'Caja · Reparación'
    WHEN 'Taller · Pantalla rota' THEN 'Caja · Pantalla rota'
    WHEN 'Listos' THEN 'Caja · Listos' END
 WHERE TIPO = 'UBICACION'
   AND DE IN ('Taller · Pulir', 'Taller · Glass', 'Taller · Reparación', 'Taller · Pantalla rota', 'Listos');

UPDATE Nsys_evento SET A = CASE A
    WHEN 'Taller · Pulir' THEN 'Caja · Pulir'
    WHEN 'Taller · Glass' THEN 'Caja · Glass'
    WHEN 'Taller · Reparación' THEN 'Caja · Reparación'
    WHEN 'Taller · Pantalla rota' THEN 'Caja · Pantalla rota'
    WHEN 'Listos' THEN 'Caja · Listos' END
 WHERE TIPO IN ('UBICACION', 'ENTRADA')
   AND A IN ('Taller · Pulir', 'Taller · Glass', 'Taller · Reparación', 'Taller · Pantalla rota', 'Listos');

COMMIT;

-- Comprobación (no modifica nada): debe dar 7. Bytes UTF-8 de los siete nombres con tilde o «·».
SELECT COUNT(*) AS ubicaciones_utf8_ok
  FROM Nsys_ubicacion
 WHERE HEX(UBICACION) IN (
       '456E20726576697369C3B36E',                          -- En revisión
       '4465766F6C756369C3B36E',                            -- Devolución
       '43616A6120C2B72050756C6972',                        -- Caja · Pulir
       '43616A6120C2B720476C617373',                        -- Caja · Glass
       '43616A6120C2B7205265706172616369C3B36E',            -- Caja · Reparación
       '43616A6120C2B72050616E74616C6C6120726F7461',        -- Caja · Pantalla rota
       '43616A6120C2B7204C6973746F73');                     -- Caja · Listos

-- Quedan viejos (debe dar 0 en las tres).
SELECT COUNT(*) AS moviles_viejos FROM Nsys_movil
 WHERE UBICACION IN ('Taller · Pulir', 'Taller · Glass', 'Taller · Reparación', 'Taller · Pantalla rota', 'Listos')
    OR UBICACION_ULTIMA IN ('Taller · Pulir', 'Taller · Glass', 'Taller · Reparación', 'Taller · Pantalla rota', 'Listos');
SELECT COUNT(*) AS eventos_viejos FROM Nsys_evento
 WHERE TIPO IN ('UBICACION', 'ENTRADA')
   AND (DE IN ('Taller · Pulir', 'Taller · Glass', 'Taller · Reparación', 'Taller · Pantalla rota', 'Listos')
        OR A IN ('Taller · Pulir', 'Taller · Glass', 'Taller · Reparación', 'Taller · Pantalla rota', 'Listos'));
SELECT COUNT(*) AS ubicaciones_viejas FROM Nsys_ubicacion
 WHERE UBICACION IN ('Taller · Pulir', 'Taller · Glass', 'Taller · Reparación', 'Taller · Pantalla rota', 'Listos');
