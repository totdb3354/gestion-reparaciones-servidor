package com.reparaciones.servidor.nsys;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** La base de la 0.9.27 (spec «ignorar» §3): la migración, que es la que se aplica en la VM, y crear_bd.sql dicen lo mismo de
 *  Taller_vuelta_ignorada. Se comparan con los espacios juntados (crear_bd.sql alinea las columnas). */
class DdlIgnoradoTest {

    private static final String MIGRACION = "migracion-ignorar-vuelta-nsys.sql";

    private static String fichero(String nombre) throws Exception {
        return Files.readString(Path.of("sql", nombre), StandardCharsets.UTF_8);
    }

    private static String junto(String texto) {
        return texto.replaceAll("\\s+", " ").strip();
    }

    private static String crear(String sql, String tabla) {
        int desde = sql.indexOf("CREATE TABLE " + tabla + " (");
        assertTrue(desde >= 0, tabla);
        return junto(sql.substring(desde, sql.indexOf(");", desde) + 2));
    }

    @Test void laTablaDeLosIgnoradosEsLaMismaEnLosDos() throws Exception {
        String migracion = crear(fichero(MIGRACION), "Taller_vuelta_ignorada");
        assertEquals(migracion, crear(fichero("crear_bd.sql"), "Taller_vuelta_ignorada"));
        assertTrue(migracion.contains("IMEI_SN VARCHAR(40) NOT NULL"), migracion);
        assertTrue(migracion.contains("FECHA DATETIME NOT NULL"), migracion);
        assertTrue(migracion.contains("PRIMARY KEY (IMEI_SN)"), migracion);
        assertTrue(migracion.contains("FOREIGN KEY (ID_USU) REFERENCES Usuario (ID_USU) ON DELETE SET NULL"), migracion);
    }

    @Test void laMigracionTieneVistaPreviaYComprobacion() throws Exception {
        String sql = fichero(MIGRACION);
        assertTrue(sql.contains("AS YA_EXISTEN"), "vista previa");
        assertTrue(sql.contains("AS CREADAS"), "comprobación final");
        assertTrue(sql.contains("USE gestion_reparaciones;"));
    }

    @Test void elResetDeUatVaciaLosIgnoradosYCrearBdSePuedeRelanzar() throws Exception {
        assertTrue(fichero("reset-uat.sql").contains("DELETE FROM Taller_vuelta_ignorada;"));
        assertTrue(fichero("crear_bd.sql").contains("DROP TABLE IF EXISTS Taller_vuelta_ignorada;"));
    }
}
