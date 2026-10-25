package com.reparaciones.servidor.nsys;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/** La base de la 0.9.22 pieza 2 (spec «plantillas NSYS» §3.5 y §3.6): la migración, que es la que se aplica en la VM, y
 *  crear_bd.sql dicen lo mismo de Taller_hecho y de las columnas de «Revisado» de Taller_aviso. Se comparan con los espacios
 *  juntados (crear_bd.sql alinea las columnas). */
class DdlPlantillasTest {

    private static final String MIGRACION = "migracion-plantillas-nsys.sql";
    private static final String CLAVE_REVISADO =
            "fk_taller_aviso_revisado FOREIGN KEY (REVISADO_POR) REFERENCES Usuario (ID_USU) ON DELETE SET NULL";

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

    /** Lo que va entre «ADD COLUMN <columna>» y «AFTER» en la migración. */
    private static String deLaMigracion(String columna) throws Exception {
        Matcher m = Pattern.compile("ADD COLUMN " + columna + " (.+?) AFTER ").matcher(fichero(MIGRACION));
        assertTrue(m.find(), columna + " en la migración");
        return junto(m.group(1));
    }

    /** La línea de la columna en el CREATE TABLE de {@code tabla} de crear_bd.sql, sin el nombre ni la coma final. */
    private static String deCrearBd(String tabla, String columna) throws Exception {
        String sql = fichero("crear_bd.sql");
        int desde = sql.indexOf("CREATE TABLE " + tabla + " (");
        assertTrue(desde >= 0, tabla + " en crear_bd.sql");
        String bloque = sql.substring(desde, sql.indexOf(");", desde));
        Matcher m = Pattern.compile("(?m)^\\s+" + columna + "\\s+(.+?),?$").matcher(bloque);
        assertTrue(m.find(), columna + " en " + tabla);
        return junto(m.group(1));
    }

    @Test void laTablaDeLasMarcasEsLaMismaEnLosDos() throws Exception {
        String migracion = crear(fichero(MIGRACION), "Taller_hecho");
        assertEquals(migracion, crear(fichero("crear_bd.sql"), "Taller_hecho"));
        assertTrue(migracion.contains("TARJETA ENUM('COLOR','GRADO','ALTA') NOT NULL"), migracion);
        assertTrue(migracion.contains("PRIMARY KEY (IMEI_SN, TARJETA)"), migracion);
    }

    @Test void lasColumnasDeRevisadoSonLasMismasEnLosDos() throws Exception {
        assertEquals(deLaMigracion("REVISADO_POR"), deCrearBd("Taller_aviso", "REVISADO_POR"));
        assertEquals(deLaMigracion("REVISADO_EN"), deCrearBd("Taller_aviso", "REVISADO_EN"));
        assertTrue(deLaMigracion("REVISADO_POR").startsWith("INT NULL"), deLaMigracion("REVISADO_POR"));
        assertTrue(deLaMigracion("REVISADO_EN").startsWith("DATETIME NULL"), deLaMigracion("REVISADO_EN"));
        assertTrue(junto(fichero(MIGRACION)).contains("ADD CONSTRAINT " + CLAVE_REVISADO));
        assertTrue(junto(fichero("crear_bd.sql")).contains("CONSTRAINT " + CLAVE_REVISADO));
    }

    @Test void laMigracionTieneVistaPreviaYComprobacion() throws Exception {
        String sql = fichero(MIGRACION);
        assertTrue(sql.contains("AS YA_EXISTEN"), "vista previa");
        assertTrue(sql.contains("AS CREADAS"), "comprobación final");
        assertTrue(sql.contains("USE gestion_reparaciones;"));
    }

    @Test void elResetDeUatVaciaLasMarcasYCrearBdSePuedeRelanzar() throws Exception {
        assertTrue(fichero("reset-uat.sql").contains("DELETE FROM Taller_hecho;"));
        assertTrue(fichero("crear_bd.sql").contains("DROP TABLE IF EXISTS Taller_hecho;"));
    }
}
