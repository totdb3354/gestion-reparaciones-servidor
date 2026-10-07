-- 0.9.5: parámetros editables por el administrador. De momento, los pesos de la previsión de pedidos de Stock
-- (días 1-30, 31-60 y 61-90, en %, que suman 100). Aditivo: el servidor 0.9.4 sigue funcionando sobre este esquema,
-- y el 0.9.5 sin esta tabla usa 50/30/20 (solo fallaría el diálogo que los guarda).
--
-- ORDEN DE APLICACIÓN: antes de desplegar el servidor 0.9.5. Antes de aplicar, hacer una copia de la base.
-- REEJECUCIÓN: no es idempotente (el CREATE TABLE falla si la tabla ya existe). Una sola vez por base.
USE gestion_reparaciones;

CREATE TABLE Parametro (
    CLAVE      VARCHAR(50) NOT NULL,
    VALOR      INT         NOT NULL,
    UPDATED_AT TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (CLAVE)
);

INSERT INTO Parametro (CLAVE, VALOR) VALUES
    ('PREVISION_PESO_1', 50),
    ('PREVISION_PESO_2', 30),
    ('PREVISION_PESO_3', 20);
