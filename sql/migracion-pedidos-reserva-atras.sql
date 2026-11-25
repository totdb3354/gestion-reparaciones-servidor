-- Vuelta atras de migracion-pedidos-reserva.sql.
ALTER TABLE Pedido_fila
  DROP KEY idx_fila_reservado,
  DROP COLUMN IMEI_RESERVADO,
  DROP COLUMN RESERVADA_EN,
  DROP COLUMN SIN_STOCK_EN;
