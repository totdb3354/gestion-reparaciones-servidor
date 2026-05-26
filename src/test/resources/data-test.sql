-- Datos base para tests de integración (sin usuarios — se insertan desde Java con BCrypt)
-- Se ejecuta una vez al arrancar el contexto de test (spring.sql.init.mode=always)

INSERT INTO Componente (ID_COM, TIPO, STOCK, STOCK_MINIMO, ACTIVO) VALUES
    (1, 'Pantalla Test', 10, 2, TRUE);

INSERT INTO Telefono (IMEI, MODELO) VALUES
    ('999000000000000', 'iPhone Test');

INSERT INTO Proveedor (ID_PROV, NOMBRE, ACTIVO, DIVISA) VALUES
    (1, 'Proveedor Test', TRUE, 'EUR');
