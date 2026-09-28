-- SP7b: el registro de actividad sobrevive al borrado del usuario, y el administrador puede entregar una
-- contraseña temporal. Aditivo: un servidor anterior sigue funcionando sobre este esquema.
--
-- ORDEN DE APLICACIÓN: en el traspaso a producción, este script se aplica DESPUÉS de cargar el volcado
-- de la base de preproducción, nunca antes (un volcado cargado encima del esquema nuevo restauraría el
-- esquema viejo y se llevaría la migración por delante). Como es aditiva, no hay prisa entre aplicar
-- este SQL y desplegar el servidor de la entrega 3 que lo usa.
--
-- REEJECUCIÓN: este script NO es idempotente. El ALTER que renombra/reajusta la clave ajena falla si se
-- repite (la restricción ya no se llama como antes tras el paso 3, o ya no existe si se repite dos veces).
-- Ejecutar UNA sola vez por base de datos. Antes de aplicar, hacer una copia de la base.
--
-- ATENCIÓN — nombre de la clave ajena: el script usa `fk_log_usuario`, que es el nombre que trae el
-- esquema de referencia de este repositorio (sql/crear_bd.sql). Antes de aplicar este script contra una
-- base viva, confirmar que ese es el nombre real con esta consulta y sustituirlo si difiere:
--
--   SELECT CONSTRAINT_NAME, COLUMN_NAME FROM information_schema.KEY_COLUMN_USAGE
--    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'Log_Actividad'
--      AND REFERENCED_TABLE_NAME IS NOT NULL;
--
USE gestion_reparaciones;

-- 1) El nombre viaja con la línea del registro, para que siga siendo legible cuando el usuario ya no exista.
ALTER TABLE Log_Actividad
    ADD COLUMN NOMBRE_USUARIO VARCHAR(50) NULL AFTER ID_USU;

-- 2) Rellenar el nombre de todo lo que ya hay.
UPDATE Log_Actividad l
  JOIN Usuario u ON l.ID_USU = u.ID_USU
   SET l.NOMBRE_USUARIO = u.NOMBRE_USUARIO;

-- 3) La clave del usuario pasa a admitir nulo y a quedarse a nulo cuando el usuario se borre.
--    Sustituir fk_log_usuario por el CONSTRAINT_NAME real si la consulta de arriba da un nombre distinto.
ALTER TABLE Log_Actividad
    DROP FOREIGN KEY fk_log_usuario;

ALTER TABLE Log_Actividad
    MODIFY COLUMN ID_USU INT NULL;

ALTER TABLE Log_Actividad
    ADD CONSTRAINT fk_log_usuario FOREIGN KEY (ID_USU) REFERENCES Usuario (ID_USU) ON DELETE SET NULL;

-- 4) Marca de contraseña entregada por el administrador: obliga a cambiarla al entrar.
ALTER TABLE Usuario
    ADD COLUMN PASSWORD_TEMPORAL TINYINT(1) NOT NULL DEFAULT 0;

-- Comprobación (debe devolver: NOMBRE_USUARIO YES, ID_USU YES, PASSWORD_TEMPORAL NO, y 0 filas sin nombre)
SELECT COLUMN_NAME, IS_NULLABLE FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'Log_Actividad' AND COLUMN_NAME IN ('ID_USU','NOMBRE_USUARIO');
SELECT COLUMN_NAME, IS_NULLABLE FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'Usuario' AND COLUMN_NAME = 'PASSWORD_TEMPORAL';
SELECT COUNT(*) AS lineas_sin_nombre FROM Log_Actividad WHERE NOMBRE_USUARIO IS NULL;
SELECT DELETE_RULE FROM information_schema.REFERENTIAL_CONSTRAINTS
 WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'Log_Actividad';
