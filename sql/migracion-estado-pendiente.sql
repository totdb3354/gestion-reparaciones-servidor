-- Migración Item 4: añadir 'pendiente' al enum de Compra_componente.ESTADO.
-- Aplicar en la BD viva de la VM. Los valores existentes (en_camino/parcial/recibido/cancelado) se conservan.
USE gestion_reparaciones;

ALTER TABLE Compra_componente
    MODIFY COLUMN ESTADO ENUM('pendiente','en_camino','parcial','recibido','cancelado')
    NOT NULL DEFAULT 'pendiente';
