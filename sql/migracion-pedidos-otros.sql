-- Migración: pedidos "Otros" (apuntes ajenos al stock). Aplicar sobre BD existente.
CREATE TABLE IF NOT EXISTS Compra_otro (
    ID_COMPRA_OTRO       INT           NOT NULL AUTO_INCREMENT,
    ID_PROV              INT           NOT NULL,
    CONCEPTO             VARCHAR(255)  NOT NULL,
    CANTIDAD             INT           NOT NULL,
    CANTIDAD_RECIBIDA    INT,
    ES_URGENTE           BOOLEAN       NOT NULL DEFAULT FALSE,
    FECHA_PEDIDO         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FECHA_LLEGADA        DATETIME,
    PRECIO_UNIDAD_PEDIDO DECIMAL(10,2) NOT NULL DEFAULT 0,
    DIVISA               VARCHAR(3)    NOT NULL DEFAULT 'EUR',
    PRECIO_EUR           DECIMAL(10,2) NOT NULL DEFAULT 0,
    ESTADO               ENUM('pendiente','en_camino','parcial','recibido','cancelado') NOT NULL DEFAULT 'pendiente',
    UPDATED_AT           TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (ID_COMPRA_OTRO),
    CONSTRAINT fk_compra_otro_proveedor FOREIGN KEY (ID_PROV) REFERENCES Proveedor (ID_PROV)
);
