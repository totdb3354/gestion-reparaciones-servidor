-- ══════════════════════════════════════════════════════════════════════════════
-- migracion-suppliers.sql — suppliers de teléfonos: TIPO en Proveedor (spec 2026-07-17)
-- 1) Ejecutar los SELECT de vista previa y revisar la clasificación propuesta.
-- 2) Si (b) sale vacío y (a)/(c) cuadran, ejecutar el ALTER y el UPDATE. NO idempotente.
-- ══════════════════════════════════════════════════════════════════════════════
USE gestion_reparaciones;

-- Vista previa (no modifica nada) ─────────────────────────────────────────────
-- (a) Proveedores que pasarán a TELEFONOS (referenciados por algún lote)
SELECT p.ID_PROV, p.NOMBRE, COUNT(l.ID_LOTE) AS LOTES
  FROM Proveedor p
  JOIN Lote l ON l.ID_PROV = p.ID_PROV
 GROUP BY p.ID_PROV, p.NOMBRE;

-- (b) CONFLICTOS: usados en lotes Y en compras — DEBE SALIR VACÍO; si no, PARAR
--     y decidir a mano fila a fila con el usuario antes de ejecutar el UPDATE
SELECT p.ID_PROV, p.NOMBRE,
       (SELECT COUNT(*) FROM Lote l  WHERE l.ID_PROV  = p.ID_PROV) AS LOTES,
       (SELECT COUNT(*) FROM Compra_componente cc WHERE cc.ID_PROV = p.ID_PROV) AS COMPRAS_COMPONENTE,
       (SELECT COUNT(*) FROM Compra_otro co       WHERE co.ID_PROV = p.ID_PROV) AS COMPRAS_OTRO
  FROM Proveedor p
 WHERE EXISTS (SELECT 1 FROM Lote l WHERE l.ID_PROV = p.ID_PROV)
   AND (EXISTS (SELECT 1 FROM Compra_componente cc WHERE cc.ID_PROV = p.ID_PROV)
     OR EXISTS (SELECT 1 FROM Compra_otro co WHERE co.ID_PROV = p.ID_PROV));

-- (c) Recuento resultante propuesto
SELECT CASE WHEN EXISTS (SELECT 1 FROM Lote l WHERE l.ID_PROV = p.ID_PROV)
            THEN 'TELEFONOS' ELSE 'COMPONENTES' END AS TIPO_PROPUESTO,
       COUNT(*) AS N
  FROM Proveedor p
 GROUP BY TIPO_PROPUESTO;

-- ALTER + migración (ejecutar tras validar la vista previa) ───────────────────
ALTER TABLE Proveedor
    ADD COLUMN TIPO ENUM('COMPONENTES','TELEFONOS') NOT NULL DEFAULT 'COMPONENTES';

UPDATE Proveedor p
   SET p.TIPO = 'TELEFONOS'
 WHERE EXISTS (SELECT 1 FROM Lote l WHERE l.ID_PROV = p.ID_PROV);

-- Verificación: debe cuadrar con la vista previa (c) ──────────────────────────
SELECT TIPO, COUNT(*) AS N FROM Proveedor GROUP BY TIPO;
