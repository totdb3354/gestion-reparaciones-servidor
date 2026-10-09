-- 0.9.8: relleno de Reparacion.ID_CLI en los trabajos ya cerrados (spec 2026-10-09-v098-cliente-por-trabajo §6).
-- Regla: el ultimo apunte ASIGNAR_CLIENTE/CAMBIAR_CLIENTE/QUITAR_CLIENTE del IMEI con FECHA <= FECHA_FIN
-- (QUITAR_CLIENTE y 'ID_CLI: ' con raya significan sin cliente); si el IMEI tiene apuntes
-- pero todos son posteriores, sin cliente; si no tiene ninguno, el cliente actual del telefono; si el apunte nombra un
-- cliente que ya no existe, sin cliente.
--
-- ORDEN: despues de migracion-cliente-por-trabajo.sql y del arranque del servidor 0.9.8. Copia de la base antes.
-- COMO: en la consola de MariaDB, BLOQUE A BLOQUE y en la MISMA sesion (variables y tablas temporales de sesion).
--   Bloques 0-2 solo leen (cada sentencia bloquea un instante mientras corre y no queda nada bloqueado). El bloque 3
--   escribe en una transaccion, comprueba lo escrito y se termina A MANO con COMMIT; o ROLLBACK;. Hasta entonces las
--   filas cerradas que escribe quedan bloqueadas: leer 3.2/3.3 y decidir de inmediato; mientras, las escrituras
--   sobre esos trabajos esperan.
-- Solo ASCII fuera de los comentarios: no depende de como envie la terminal los caracteres.

-- == Bloque 0: parametros y comprobaciones ===================================
SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;
SET time_zone = '+00:00';   -- FECHA_FIN (DATETIME) y Log_Actividad.FECHA (TIMESTAMP) se comparan en UTC

-- 0.0 BASE = gestion_reparaciones (si no, PARAR)
SELECT DATABASE() AS BASE;
-- 0.0b Las 4 tablas deben salir con TABLE_COLLATION = utf8mb4_unicode_ci; si alguna sale con otra, PARAR
SELECT TABLE_NAME, TABLE_COLLATION FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME IN ('Cliente', 'Telefono', 'Reparacion', 'Log_Actividad');

-- StartedAt es el ultimo arranque del contenedor: si el backend se reinicio despues del despliegue de la 0.9.8,
-- usar la hora de inicio de ese despliegue (YA_CON_CLIENTE distinto de 0 lo delata).
-- Arranque del backend 0.9.8 tal como lo da: docker inspect -f '{{.State.StartedAt}}' reparaciones-backend-1
SET @corte := REPLACE(LEFT('PEGAR_STARTED_AT', 19), 'T', ' ');

-- 0.1 COLUMNA = 1; YA_CON_CLIENTE = 0 (nadie ha rellenado aun lo cerrado antes del corte). Si YA_CON_CLIENTE no es 0, PARAR
SELECT COUNT(*) AS COLUMNA FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'Reparacion' AND COLUMN_NAME = 'ID_CLI';
SELECT @corte AS CORTE, COUNT(*) AS CERRADAS_ANTES_DEL_CORTE, COALESCE(SUM(ID_CLI IS NOT NULL), 0) AS YA_CON_CLIENTE
FROM Reparacion WHERE FECHA_FIN IS NOT NULL AND FECHA_FIN < @corte;

-- == Bloque 1: apuntes de cliente del registro ===============================
DROP TEMPORARY TABLE IF EXISTS apunte_cliente, imei_con_apuntes, relleno;

CREATE TEMPORARY TABLE apunte_cliente (
    IMEI    VARCHAR(30) COLLATE utf8mb4_unicode_ci NOT NULL,
    FECHA   DATETIME    NOT NULL,
    ID_LOG  INT         NOT NULL,
    CLI_TXT VARCHAR(30) COLLATE utf8mb4_unicode_ci NOT NULL,
    ID_CLI  INT         NULL,
    KEY (IMEI, FECHA, ID_LOG)
);
INSERT INTO apunte_cliente (IMEI, FECHA, ID_LOG, CLI_TXT, ID_CLI)
SELECT a.IMEI, a.FECHA, a.ID_LOG, a.CLI_TXT, c.ID_CLI
FROM (SELECT TRIM(SUBSTRING_INDEX(SUBSTRING_INDEX(l.DETALLE, 'IMEI: ', -1), ',', 1)) AS IMEI,
             l.FECHA, l.ID_LOG,
             CASE WHEN l.ACCION = 'QUITAR_CLIENTE' THEN '-'
                  ELSE TRIM(SUBSTRING_INDEX(l.DETALLE, 'ID_CLI: ', -1)) END AS CLI_TXT
      FROM Log_Actividad l
      WHERE (l.ACCION IN ('ASIGNAR_CLIENTE', 'CAMBIAR_CLIENTE') AND l.DETALLE LIKE 'IMEI: %, ID_CLI: %')
         OR (l.ACCION = 'QUITAR_CLIENTE' AND l.DETALLE LIKE 'IMEI: %')) a
LEFT JOIN Cliente c ON c.ID_CLI = CASE WHEN a.CLI_TXT REGEXP '^[0-9]+$' THEN CAST(a.CLI_TXT AS UNSIGNED) END;

CREATE TEMPORARY TABLE imei_con_apuntes (IMEI VARCHAR(30) COLLATE utf8mb4_unicode_ci NOT NULL PRIMARY KEY)
SELECT DISTINCT IMEI FROM apunte_cliente;

-- 1.1 Filas del registro de cada accion (referencia)
SELECT ACCION, COUNT(*) AS FILAS FROM Log_Actividad
WHERE ACCION IN ('ASIGNAR_CLIENTE', 'CAMBIAR_CLIENTE', 'QUITAR_CLIENTE') GROUP BY ACCION;
-- Apuntes leidos. FORMATO_RARO = 0 (si no, PARAR: hay apuntes de cliente con otro formato)
SELECT COUNT(*) AS APUNTES, COUNT(DISTINCT IMEI) AS IMEIS,
       COALESCE(SUM(CLI_TXT NOT REGEXP '^[0-9]+$'), 0) AS DEJAN_SIN_CLIENTE,
       COALESCE(SUM(CLI_TXT REGEXP '^[0-9]+$' AND ID_CLI IS NULL), 0) AS CLIENTE_BORRADO,
       MIN(FECHA) AS PRIMERO, MAX(FECHA) AS ULTIMO
FROM apunte_cliente;
SELECT COUNT(*) AS FORMATO_RARO FROM Log_Actividad
WHERE (ACCION IN ('ASIGNAR_CLIENTE', 'CAMBIAR_CLIENTE') AND DETALLE NOT LIKE 'IMEI: %, ID_CLI: %')
   OR (ACCION = 'QUITAR_CLIENTE' AND DETALLE NOT LIKE 'IMEI: %')
   OR (ACCION IN ('ASIGNAR_CLIENTE', 'CAMBIAR_CLIENTE', 'QUITAR_CLIENTE') AND DETALLE IS NULL);

-- == Bloque 2: propuesta y analisis (solo lectura) ===========================
CREATE TEMPORARY TABLE relleno (
    ID_REP VARCHAR(30) COLLATE utf8mb4_unicode_ci NOT NULL PRIMARY KEY,
    ID_CLI INT         NULL,
    FUENTE VARCHAR(10) COLLATE utf8mb4_unicode_ci NOT NULL
);
INSERT INTO relleno (ID_REP, ID_CLI, FUENTE)
SELECT r.ID_REP,
       CASE WHEN i.IMEI IS NULL THEN t.ID_CLI
            ELSE (SELECT a.ID_CLI FROM apunte_cliente a
                  WHERE a.IMEI = r.IMEI AND a.FECHA <= r.FECHA_FIN
                  ORDER BY a.FECHA DESC, a.ID_LOG DESC LIMIT 1) END,
       CASE WHEN i.IMEI IS NULL THEN 'TELEFONO' ELSE 'REGISTRO' END
FROM Reparacion r
JOIN Telefono t ON t.IMEI = r.IMEI
LEFT JOIN imei_con_apuntes i ON i.IMEI = r.IMEI
WHERE r.FECHA_FIN IS NOT NULL AND r.FECHA_FIN < @corte AND r.ID_CLI IS NULL;

-- 2.1 Por fuente. TRABAJOS sumados = CERRADAS_ANTES_DEL_CORTE del 0.1
SELECT FUENTE, COUNT(*) AS TRABAJOS, SUM(ID_CLI IS NOT NULL) AS CON_CLIENTE, SUM(ID_CLI IS NULL) AS SIN_CLIENTE
FROM relleno GROUP BY FUENTE;

-- 2.2 Diferencias con lo que se ve hoy (cliente actual del telefono), por pareja de clientes
SELECT COALESCE(cg.NOMBRE, '(sin cliente)') AS GUARDADO, COALESCE(ct.NOMBRE, '(sin cliente)') AS ACTUAL,
       COUNT(*) AS TRABAJOS
FROM relleno x
JOIN Reparacion r ON r.ID_REP = x.ID_REP
JOIN Telefono t ON t.IMEI = r.IMEI
LEFT JOIN Cliente cg ON cg.ID_CLI = x.ID_CLI
LEFT JOIN Cliente ct ON ct.ID_CLI = t.ID_CLI
WHERE NOT (x.ID_CLI <=> t.ID_CLI)
GROUP BY GUARDADO, ACTUAL ORDER BY TRABAJOS DESC;

-- 2.3 Muestra: las 25 diferencias mas recientes
SELECT x.ID_REP, r.IMEI, r.FECHA_FIN, x.FUENTE,
       COALESCE(cg.NOMBRE, '(sin cliente)') AS GUARDADO, COALESCE(ct.NOMBRE, '(sin cliente)') AS ACTUAL
FROM relleno x
JOIN Reparacion r ON r.ID_REP = x.ID_REP
JOIN Telefono t ON t.IMEI = r.IMEI
LEFT JOIN Cliente cg ON cg.ID_CLI = x.ID_CLI
LEFT JOIN Cliente ct ON ct.ID_CLI = t.ID_CLI
WHERE NOT (x.ID_CLI <=> t.ID_CLI)
ORDER BY r.FECHA_FIN DESC LIMIT 25;

-- 2.4 Detalle de un IMEI de la muestra (cambiar el IMEI y repetir las veces que haga falta)
SET @imei := 'IMEI_DE_LA_MUESTRA';
SELECT a.FECHA, a.CLI_TXT, COALESCE(c.NOMBRE, '(sin cliente)') AS CLIENTE
FROM apunte_cliente a LEFT JOIN Cliente c ON c.ID_CLI = a.ID_CLI
WHERE a.IMEI = @imei ORDER BY a.FECHA;
SELECT r.ID_REP, r.FECHA_FIN, x.FUENTE, COALESCE(c.NOMBRE, '(sin cliente)') AS GUARDADO
FROM Reparacion r LEFT JOIN relleno x ON x.ID_REP = r.ID_REP LEFT JOIN Cliente c ON c.ID_CLI = x.ID_CLI
WHERE r.IMEI = @imei ORDER BY r.FECHA_FIN;

-- 2.5 Telefonos de los clientes de incidencias: con que cliente quedan sus trabajos cerrados
SELECT ct.NOMBRE AS CLIENTE_TELEFONO, COALESCE(cg.NOMBRE, '(sin cliente)') AS GUARDADO, COUNT(*) AS TRABAJOS
FROM relleno x
JOIN Reparacion r ON r.ID_REP = x.ID_REP
JOIN Telefono t ON t.IMEI = r.IMEI
JOIN Cliente ct ON ct.ID_CLI = t.ID_CLI
LEFT JOIN Cliente cg ON cg.ID_CLI = x.ID_CLI
WHERE ct.NOMBRE LIKE '%incidencia%'
GROUP BY CLIENTE_TELEFONO, GUARDADO ORDER BY CLIENTE_TELEFONO, TRABAJOS DESC;

-- == Bloque 3: escribir y comprobar (una transaccion) ========================
-- Pegar entero. 3.1 "Rows matched" = suma de TRABAJOS del 2.1 y "Changed" = suma de CON_CLIENTE del 2.1;
-- 3.2 = esa misma suma de CON_CLIENTE; 3.3 = el 2.5.
-- Terminar a mano con COMMIT; (cuadra) o ROLLBACK; (no cuadra o ha salido cualquier ERROR).
START TRANSACTION;

-- 3.1 UPDATED_AT se conserva: es un dato anadido, no una edicion
UPDATE relleno x STRAIGHT_JOIN Reparacion r ON r.ID_REP = x.ID_REP
SET r.ID_CLI = x.ID_CLI, r.UPDATED_AT = r.UPDATED_AT
WHERE r.ID_CLI IS NULL AND r.FECHA_FIN IS NOT NULL AND r.FECHA_FIN < @corte;

-- 3.2 Cerradas antes del corte que ya tienen cliente guardado
SELECT COUNT(*) AS CERRADAS_CON_CLIENTE FROM Reparacion
WHERE FECHA_FIN IS NOT NULL AND FECHA_FIN < @corte AND ID_CLI IS NOT NULL;

-- 3.3 Lo que mostrara el Historial para los telefonos de incidencias (leido ya de Reparacion)
SELECT ct.NOMBRE AS CLIENTE_TELEFONO, COALESCE(cg.NOMBRE, '(sin cliente)') AS GUARDADO, COUNT(*) AS TRABAJOS
FROM Reparacion r
JOIN Telefono t ON t.IMEI = r.IMEI
JOIN Cliente ct ON ct.ID_CLI = t.ID_CLI
LEFT JOIN Cliente cg ON cg.ID_CLI = r.ID_CLI
WHERE r.FECHA_FIN IS NOT NULL AND r.FECHA_FIN < @corte AND ct.NOMBRE LIKE '%incidencia%'
GROUP BY CLIENTE_TELEFONO, GUARDADO ORDER BY CLIENTE_TELEFONO, TRABAJOS DESC;

-- Si cuadra:  COMMIT;
-- Si no:      ROLLBACK;

-- == Bloque 4: despues del COMMIT ============================================
DROP TEMPORARY TABLE IF EXISTS apunte_cliente, imei_con_apuntes, relleno;
