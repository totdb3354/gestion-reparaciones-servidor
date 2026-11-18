-- Pedidos: calendario laboral (festivos de la Comunidad de Madrid), fecha de salida y pais de cada fila.
-- Aditiva y repetible. Spec 2026-11-18-pedidos-buscar-y-salida-design.md.
CREATE TABLE IF NOT EXISTS Calendario_festivo (
    FECHA  DATE         NOT NULL,
    NOMBRE VARCHAR(100) NOT NULL,
    AMBITO VARCHAR(20)  NOT NULL COMMENT 'COMUNIDAD (festivos de la Comunidad de Madrid); los locales no se cargan',
    PRIMARY KEY (FECHA)
);
INSERT IGNORE INTO Calendario_festivo (FECHA, NOMBRE, AMBITO) VALUES
 ('2026-12-07', 'Traslado del Dia de la Constitucion', 'COMUNIDAD'),
 ('2026-12-08', 'Inmaculada Concepcion', 'COMUNIDAD'),
 ('2026-12-25', 'Natividad del Senor', 'COMUNIDAD'),
 ('2027-01-01', 'Ano Nuevo', 'COMUNIDAD'),
 ('2027-01-06', 'Epifania del Senor', 'COMUNIDAD'),
 ('2027-03-19', 'San Jose', 'COMUNIDAD'),
 ('2027-03-25', 'Jueves Santo', 'COMUNIDAD'),
 ('2027-03-26', 'Viernes Santo', 'COMUNIDAD'),
 ('2027-05-01', 'Fiesta del Trabajo', 'COMUNIDAD'),
 ('2027-08-16', 'Traslado de la Asuncion de la Virgen', 'COMUNIDAD'),
 ('2027-10-12', 'Fiesta Nacional de Espana', 'COMUNIDAD'),
 ('2027-11-01', 'Todos los Santos', 'COMUNIDAD'),
 ('2027-12-06', 'Dia de la Constitucion Espanola', 'COMUNIDAD'),
 ('2027-12-08', 'Inmaculada Concepcion', 'COMUNIDAD'),
 ('2027-12-25', 'Natividad del Senor', 'COMUNIDAD');
ALTER TABLE Pedido_fila ADD COLUMN IF NOT EXISTS FECHA_SALIDA DATE NULL
    COMMENT 'Ultimo dia para enviar el pedido (Amazon: Latest ship de ShippyPro; webs: entrada + 2 o 3 dias laborables)';
ALTER TABLE Pedido_fila ADD COLUMN IF NOT EXISTS PAIS VARCHAR(2) NULL COMMENT 'Pais de destino (ISO de ShippyPro)';
SELECT COUNT(*) AS FESTIVOS FROM Calendario_festivo;
SELECT COUNT(*) AS COLUMNAS_NUEVAS FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'Pedido_fila' AND COLUMN_NAME IN ('FECHA_SALIDA', 'PAIS');
