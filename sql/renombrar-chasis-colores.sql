-- ══════════════════════════════════════════════════════════════════════════════
-- renombrar-chasis-colores.sql — TIPOs de chasis al vocabulario oficial (spec SKU)
-- 1) Ejecutar el SELECT y revisar el antes/después propuesto.
-- 2) Si todo cuadra, ejecutar los UPDATEs. Los añadidos que falten se hacen a mano.
-- Las referencias van por ID_COM: renombrar TIPO no rompe nada.
-- El predicado TIPO LIKE 'cha%' sigue la convención del catálogo (prefijo "cha").
-- ══════════════════════════════════════════════════════════════════════════════

USE gestion_reparaciones;

-- Vista previa (no modifica nada)
SELECT ID_COM, TIPO,
       REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(
           TIPO, 'negro', 'Black'), 'blanco', 'White'), 'rojo', '(PRODUCT)RED'),
           'azul', 'Blue'), 'verde', 'Green'), 'amarillo', 'Yellow'),
           'rosa', 'Pink'), 'morado', 'Purple') AS PROPUESTO
  FROM Componente
 WHERE TIPO LIKE 'cha%';

-- UPDATEs (mismos reemplazos; ejecutar tras validar la vista previa)
UPDATE Componente SET TIPO = REPLACE(TIPO, 'negro',    'Black')        WHERE TIPO LIKE 'cha%' AND TIPO LIKE '%negro%';
UPDATE Componente SET TIPO = REPLACE(TIPO, 'blanco',   'White')        WHERE TIPO LIKE 'cha%' AND TIPO LIKE '%blanco%';
UPDATE Componente SET TIPO = REPLACE(TIPO, 'rojo',     '(PRODUCT)RED') WHERE TIPO LIKE 'cha%' AND TIPO LIKE '%rojo%';
UPDATE Componente SET TIPO = REPLACE(TIPO, 'azul',     'Blue')         WHERE TIPO LIKE 'cha%' AND TIPO LIKE '%azul%';
UPDATE Componente SET TIPO = REPLACE(TIPO, 'verde',    'Green')        WHERE TIPO LIKE 'cha%' AND TIPO LIKE '%verde%';
UPDATE Componente SET TIPO = REPLACE(TIPO, 'amarillo', 'Yellow')       WHERE TIPO LIKE 'cha%' AND TIPO LIKE '%amarillo%';
UPDATE Componente SET TIPO = REPLACE(TIPO, 'rosa',     'Pink')         WHERE TIPO LIKE 'cha%' AND TIPO LIKE '%rosa%';
UPDATE Componente SET TIPO = REPLACE(TIPO, 'morado',   'Purple')       WHERE TIPO LIKE 'cha%' AND TIPO LIKE '%morado%';

-- Verificación: no debe quedar ningún chasis con color en castellano
SELECT ID_COM, TIPO FROM Componente
 WHERE TIPO LIKE 'cha%'
   AND (TIPO LIKE '%negro%' OR TIPO LIKE '%blanco%' OR TIPO LIKE '%rojo%' OR TIPO LIKE '%azul%'
        OR TIPO LIKE '%verde%' OR TIPO LIKE '%amarillo%' OR TIPO LIKE '%rosa%' OR TIPO LIKE '%morado%');

-- NOTA: los colores multi-palabra oficiales (Sierra Blue, Titanium…) probablemente no
-- existan en castellano en los TIPO actuales; los casos que la vista previa no cubra
-- se corrigen a mano en la UI de SKUs. El usuario decide en la vista previa.
