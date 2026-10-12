-- 0.9.9 (ampliación): piezas que vende cada proveedor (spec docs/superpowers/specs/2026-10-11-v099-catalogo-proveedores-design.md §3).
-- Aditivo: el servidor anterior sigue funcionando sobre este esquema (no nombra la tabla).
--
-- ORDEN DE APLICACIÓN: antes de desplegar el servidor, y justo después rellenar-catalogo-proveedores.sql. Antes de
-- aplicar, hacer una copia de la base.
-- REEJECUCIÓN: no es idempotente (el CREATE TABLE falla si la tabla ya existe). Una sola vez por base.
USE gestion_reparaciones;

CREATE TABLE Proveedor_componente (
    ID_PROV INT NOT NULL,
    ID_COM  INT NOT NULL,
    PRIMARY KEY (ID_PROV, ID_COM),
    CONSTRAINT fk_provcom_proveedor  FOREIGN KEY (ID_PROV) REFERENCES Proveedor  (ID_PROV) ON DELETE CASCADE,
    CONSTRAINT fk_provcom_componente FOREIGN KEY (ID_COM)  REFERENCES Componente (ID_COM)  ON DELETE CASCADE,
    INDEX idx_provcom_componente (ID_COM)
);

-- Verificación post: SELECT COUNT(*) FROM Proveedor_componente;  -- 0 (la rellena rellenar-catalogo-proveedores.sql)
