package com.reparaciones.servidor.nsys;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** La base de la 0.9.22 pieza 3 (spec «descartar» §3): la migración, que es la que se aplica en la VM, y crear_bd.sql dicen lo
 *  mismo de Taller_descarte. Se comparan con los espacios juntados (crear_bd.sql alinea las columnas). */
class DdlDescarteTest {

    private static final String MIGRACION = "migracion-descartar-nsys.sql";

    private static String fichero(String nombre) throws Exception {
        return Files.readString(Path.of("sql", nombre), StandardCharsets.UTF_8);
    }

    private static String junto(String texto) {
        return texto.replaceAll("\\s+", " ").strip();
    }

    /** El CREATE TABLE de {@code tabla}, de «CREATE TABLE» a «);». */
    private static String crear(String sql, String tabla) {
        int desde = sql.indexOf("CREATE TABLE " + tabla + " (");
        assertTrue(desde >= 0, tabla);
        return junto(sql.substring(desde, sql.indexOf(");", desde) + 2));
    }

    @Test void laTablaDeLosDescartesEsLaMismaEnLosDos() throws Exception {
        String migracion = crear(fichero(MIGRACION), "Taller_descarte");
        assertEquals(migracion, crear(fichero("crear_bd.sql"), "Taller_descarte"));
        assertTrue(migracion.contains("MOTIVO VARCHAR(500) NOT NULL"), migracion);
        assertTrue(migracion.contains("PRIMARY KEY (IMEI_SN)"), migracion);
        assertTrue(migracion.contains("FOREIGN KEY (ID_USU) REFERENCES Usuario (ID_USU) ON DELETE SET NULL"), migracion);
    }

    @Test void laMigracionTieneVistaPreviaYComprobacion() throws Exception {
        String sql = fichero(MIGRACION);
        assertTrue(sql.contains("AS YA_EXISTEN"), "vista previa");
        assertTrue(sql.contains("AS CREADAS"), "comprobación final");
        assertTrue(sql.contains("USE gestion_reparaciones;"));
    }

    @Test void elResetDeUatVaciaLosDescartesYCrearBdSePuedeRelanzar() throws Exception {
        assertTrue(fichero("reset-uat.sql").contains("DELETE FROM Taller_descarte;"));
        assertTrue(fichero("crear_bd.sql").contains("DROP TABLE IF EXISTS Taller_descarte;"));
    }
}
