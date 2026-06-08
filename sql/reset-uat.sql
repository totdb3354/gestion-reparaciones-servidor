-- Reset de datos de prueba para UAT
-- Mantiene: Tecnico, Usuario, Componente, Proveedor, TipoCambio
-- Borra: todo lo operativo (reparaciones, pulidos, compras, teléfonos, logs)

USE gestion_reparaciones;

-- 1. Logs de actividad
DELETE FROM Log_Actividad;

-- 2. Solicitudes de stock
DELETE FROM Solicitud_Stock;

-- 3. Piezas de reparaciones
DELETE FROM Reparacion_componente;

-- 4. Pedidos de compra
DELETE FROM Compra_componente;

-- 5. Romper auto-referencia y borrar reparaciones/pulidos
UPDATE Reparacion SET ID_REP_ANTERIOR = NULL;
DELETE FROM Reparacion;

-- 6. Teléfonos (IMEIs)
DELETE FROM Telefono;

-- 7. Stock inicial para pruebas
UPDATE Componente SET STOCK = 10, STOCK_MINIMO = 2;
