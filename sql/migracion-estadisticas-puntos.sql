-- Migración estadísticas por puntos (spec 2026-09-01-estadisticas-puntos-design).
USE gestion_reparaciones;

-- Vista previa antes de aplicar:
--   SELECT COUNT(*) FROM information_schema.TABLES
--    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'Dificultad_puntos';  -- esperado: 0

CREATE TABLE Dificultad_puntos (
    CLAVE      VARCHAR(20)  NOT NULL,
    PUNTOS     DECIMAL(4,2) NOT NULL,
    UPDATED_AT TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (CLAVE)
);

INSERT INTO Dificultad_puntos (CLAVE, PUNTOS) VALUES
    ('bateria',  1.00),
    ('camara',   0.70),
    ('chasis',   2.00),
    ('marco',    0.50),
    ('pantalla', 1.00),
    ('glass',    0.50),
    ('otro',     0.50),
    ('pulido',   0.25);

-- Verificación post: SELECT CLAVE, PUNTOS FROM Dificultad_puntos ORDER BY CLAVE;  -- 8 filas
