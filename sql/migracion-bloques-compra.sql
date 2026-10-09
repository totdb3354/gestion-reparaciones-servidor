-- 0.9.9: pedidos por bloques (spec docs/superpowers/specs/2026-10-09-v099-bloques-pedidos-design.md §4).
-- Un bloque es la cabecera de un pedido a un proveedor; cada línea de Compra_componente pertenece a uno.
-- Aditivo: el servidor 0.9.8 sigue funcionando sobre este esquema (no nombra ni la tabla ni la columna).
--
-- ORDEN DE APLICACIÓN: antes de desplegar el servidor 0.9.9, y justo después reconstruir-bloques-compra.sql. Antes de
-- aplicar, hacer una copia de la base.
-- REEJECUCIÓN: no es idempotente (el CREATE TABLE falla si la tabla ya existe). Una sola vez por base.
USE gestion_reparaciones;

CREATE TABLE Bloque_compra (
    ID_BLOQUE    INT          NOT NULL AUTO_INCREMENT,
    ID_PROV      INT          NOT NULL,
    FECHA        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    NOTA         VARCHAR(200),
    RECONSTRUIDO BOOLEAN      NOT NULL DEFAULT FALSE,
    UPDATED_AT   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (ID_BLOQUE),
    CONSTRAINT fk_bloque_proveedor FOREIGN KEY (ID_PROV) REFERENCES Proveedor (ID_PROV)
);

ALTER TABLE Compra_componente
    ADD COLUMN ID_BLOQUE INT NULL AFTER ID_PROV,
    ADD CONSTRAINT fk_compra_bloque FOREIGN KEY (ID_BLOQUE) REFERENCES Bloque_compra (ID_BLOQUE),
    ADD INDEX idx_compra_bloque (ID_BLOQUE);

-- Verificación post: SELECT COUNT(*) FROM Bloque_compra;  -- 0 (los bloques los crea reconstruir-bloques-compra.sql)
