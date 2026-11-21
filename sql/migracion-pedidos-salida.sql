-- ══════════════════════════════════════════════════════════════════════════════
-- migracion-pedidos-salida.sql: calendario laboral, fecha de salida y pais de cada fila de «Pedidos»
-- (spec docs/superpowers/specs/2026-11-18-pedidos-buscar-y-salida-design.md). PRODUCCION Y PREPROD: el mismo fichero.
-- Crea la tabla Calendario_festivo (festivos de la Comunidad de Madrid, 15 filas) y anade a Pedido_fila las columnas
-- FECHA_SALIDA (ultimo dia para enviar el pedido) y PAIS (pais de destino, ISO de ShippyPro).
-- La aplica el usuario a mano en la VM, con vista previa, ANTES de desplegar el servidor: el servidor nuevo lee FECHA_SALIDA y PAIS
-- en la consulta de las filas y sin ellas falla.
-- Aditiva e idempotente (CREATE TABLE IF NOT EXISTS, INSERT IGNORE, ADD COLUMN IF NOT EXISTS): se puede relanzar entero. El servidor
-- anterior no lee la tabla ni las columnas nuevas: funciona con todo puesto. No lanzarla mientras corre la copia nocturna.
-- 1) Vista previa: TABLA_YA debe dar 0 (primera vez) o 1 (ya aplicado) y COLUMNAS_YA, 0 o 2.
-- 2) Crea la tabla, carga los festivos y anade las columnas que falten.
-- 3) Comprobacion final: FESTIVOS debe dar 15 y COLUMNAS_NUEVAS, 2; si no, NO desplegar el servidor.
-- Vuelta atras: el servidor anterior funciona con todo puesto; no hace falta quitar nada (o DROP TABLE Calendario_festivo y
-- ALTER TABLE Pedido_fila DROP COLUMN FECHA_SALIDA, DROP COLUMN PAIS).
-- ══════════════════════════════════════════════════════════════════════════════
USE gestion_reparaciones;
SET NAMES utf8mb4;

-- Vista previa (no modifica nada)
SELECT COUNT(*) AS TABLA_YA FROM information_schema.TABLES
 WHERE TABLE_SCHEMA = 'gestion_reparaciones' AND TABLE_NAME = 'Calendario_festivo';
SELECT COUNT(*) AS COLUMNAS_YA FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA = 'gestion_reparaciones' AND TABLE_NAME = 'Pedido_fila' AND COLUMN_NAME IN ('FECHA_SALIDA', 'PAIS');

-- Si algo tiene en uso Pedido_fila, no esperar mas de 10 s por el bloqueo de metadatos: falla y se relanza.
SET SESSION lock_wait_timeout = 10;

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
-- Comprobacion final (no modifica nada): FESTIVOS = 15 y COLUMNAS_NUEVAS = 2; si no, NO desplegar el servidor.
SELECT COUNT(*) AS FESTIVOS FROM Calendario_festivo;
SELECT COUNT(*) AS COLUMNAS_NUEVAS FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA = 'gestion_reparaciones' AND TABLE_NAME = 'Pedido_fila' AND COLUMN_NAME IN ('FECHA_SALIDA', 'PAIS');
