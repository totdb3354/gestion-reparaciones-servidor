-- 0.9.9 (ajuste): tipos de pieza que vende cada proveedor (spec docs/superpowers/specs/2026-10-12-v099-piezas-por-tipo-design.md §3).
-- Sustituye la tabla Proveedor_componente de la ampliación del 2026-10-11, que nunca llegó a producción (en
-- preproducción se borra a mano antes: DROP TABLE Proveedor_componente;).
-- Aditivo: el servidor anterior sigue funcionando sobre este esquema (no nombra las tablas).
--
-- ORDEN DE APLICACIÓN: antes de desplegar el servidor, y justo después rellenar-catalogo-proveedores.sql. Antes de
-- aplicar, hacer una copia de la base.
-- REEJECUCIÓN: no es idempotente (el CREATE TABLE falla si la tabla ya existe). Una sola vez por base.
USE gestion_reparaciones;

-- Una fila: el proveedor vende ese tipo de pieza (prefijo de la referencia: bat, cha, tapa, g, mc, lcd, cam…).
CREATE TABLE Proveedor_tipo (
    ID_PROV INT         NOT NULL,
    PREFIJO VARCHAR(10) NOT NULL,
    PRIMARY KEY (ID_PROV, PREFIJO),
    CONSTRAINT fk_provtipo_proveedor FOREIGN KEY (ID_PROV) REFERENCES Proveedor (ID_PROV) ON DELETE CASCADE
);

-- Modelos de ese tipo que el proveedor NO vende (sin filas = todos los modelos, también los que salgan más adelante).
CREATE TABLE Proveedor_tipo_excluido (
    ID_PROV INT         NOT NULL,
    PREFIJO VARCHAR(10) NOT NULL,
    MODELO  VARCHAR(20) NOT NULL,
    PRIMARY KEY (ID_PROV, PREFIJO, MODELO),
    CONSTRAINT fk_provexcl_tipo FOREIGN KEY (ID_PROV, PREFIJO) REFERENCES Proveedor_tipo (ID_PROV, PREFIJO) ON DELETE CASCADE
);

-- Verificación post: SELECT COUNT(*) FROM Proveedor_tipo;  -- 0 (la rellena rellenar-catalogo-proveedores.sql)
