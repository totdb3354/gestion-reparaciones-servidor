-- Reserva de IMEI en Pedidos: tres columnas nuevas en Pedido_fila (ALTER aditivos, sin transaccion).
ALTER TABLE Pedido_fila
  ADD COLUMN IMEI_RESERVADO VARCHAR(20) NULL COMMENT 'IMEI que el ERP aparta para la fila (reserva); NULL = sin reserva. Cuenta como sin IMEI',
  ADD COLUMN RESERVADA_EN DATETIME NULL COMMENT 'Cuando se reservo',
  ADD COLUMN SIN_STOCK_EN DATETIME NULL COMMENT 'Cuando se apunto «sin stock» (una vez hasta que reserve)',
  ADD KEY idx_fila_reservado (IMEI_RESERVADO);
