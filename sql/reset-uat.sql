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

-- 4b. Borradores de reparación (también caería por CASCADE, pero explícito)
DELETE FROM Reparacion_borrador;

-- 5. Romper auto-referencia y borrar reparaciones/pulidos
UPDATE Reparacion SET ID_REP_ANTERIOR = NULL;
DELETE FROM Reparacion;

-- 5b. Espejo de NSYS (0.9.17): eventos antes que importaciones (clave ajena). Se conservan el maestro y las cajas.
DELETE FROM Nsys_evento;
DELETE FROM Nsys_movil;
DELETE FROM Nsys_importacion;

-- 6. Teléfonos (IMEIs)
DELETE FROM Telefono;

-- 7. Stock inicial para pruebas
UPDATE Componente SET STOCK = 10, STOCK_MINIMO = 2;
