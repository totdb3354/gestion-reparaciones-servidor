-- ══════════════════════════════════════════════════════════════════════════════
-- migracion-otros-en-bloques.sql — los otros pedidos dentro del bloque de su proveedor (0.9.15)
-- Spec docs/superpowers/specs/2026-10-14-v0915-anadir-otro-design.md §3.1.
-- Columna ID_BLOQUE en Compra_otro (nula, con clave ajena e índice), igual que Compra_componente.ID_BLOQUE (0.9.9).
-- Solo añade: el servidor 0.9.14 sigue funcionando (no nombra la columna).
-- ORDEN: copia de la base → recuento-otros-en-bloques.sql → esta migración → reconstruir-bloques-otros.sql →
-- servidor 0.9.15 → otra vez reconstruir-bloques-otros.sql (recoge los otros que el 0.9.14 creó en el hueco).
-- El ALTER NO es idempotente (relanzar = error de columna existente): una sola vez por base.
-- 1) Vista previa: EXISTE_COLUMNA = 0.
-- 2) Ejecutar la migración.
-- 3) Comprobación: EXISTE_COLUMNA = 1 y OTROS_CON_BLOQUE = 0 (los reparte reconstruir-bloques-otros.sql).
-- ══════════════════════════════════════════════════════════════════════════════
USE gestion_reparaciones;

-- 1) Vista previa (no modifica nada) ──────────────────────────────────────────
SELECT COUNT(*) AS EXISTE_COLUMNA
  FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA = 'gestion_reparaciones'
   AND TABLE_NAME   = 'Compra_otro'
   AND COLUMN_NAME  = 'ID_BLOQUE';

-- 2) Migración ────────────────────────────────────────────────────────────────
ALTER TABLE Compra_otro
    ADD COLUMN ID_BLOQUE INT NULL AFTER ID_PROV,
    ADD CONSTRAINT fk_compra_otro_bloque FOREIGN KEY (ID_BLOQUE) REFERENCES Bloque_compra (ID_BLOQUE),
    ADD INDEX idx_compra_otro_bloque (ID_BLOQUE);

-- 3) Comprobación ─────────────────────────────────────────────────────────────
SELECT COUNT(*) AS EXISTE_COLUMNA
  FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA = 'gestion_reparaciones'
   AND TABLE_NAME   = 'Compra_otro'
   AND COLUMN_NAME  = 'ID_BLOQUE';
SELECT COUNT(*) AS OTROS_CON_BLOQUE FROM Compra_otro WHERE ID_BLOQUE IS NOT NULL;
