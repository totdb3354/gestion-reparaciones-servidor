package com.reparaciones.servidor.nsys;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/** Las columnas de la 0.9.22 (spec «plantillas NSYS» §2.4): la migración, que es la que se aplica en la VM, y crear_bd.sql dicen
 *  lo mismo, y AnchosNsys corta el nombre del maestro a su ancho. */
class DdlNombresCosteTest {

    private static String fichero(String nombre) throws Exception {
        return Files.readString(Path.of("sql", nombre), StandardCharsets.UTF_8);
    }

    /** «VARCHAR(150) NULL COMMENT '…'» de la migración: lo que va entre «ADD COLUMN <columna>» y «AFTER». */
    private static String deLaMigracion(String columna) throws Exception {
        Matcher m = Pattern.compile("ADD COLUMN " + columna + " (.+?) AFTER ").matcher(fichero("migracion-nombres-y-coste-nsys.sql"));
        assertTrue(m.find(), columna + " en la migración");
        return m.group(1).strip();
    }

    /** Lo mismo en crear_bd.sql, dentro del CREATE TABLE de {@code tabla} (hay otras columnas NOMBRE en otras tablas): la línea
     *  de la columna sin el nombre ni la coma final. */
    private static String deCrearBd(String tabla, String columna) throws Exception {
        String sql = fichero("crear_bd.sql");
        int desde = sql.indexOf("CREATE TABLE " + tabla + " (");
        assertTrue(desde >= 0, tabla + " en crear_bd.sql");
        String bloque = sql.substring(desde, sql.indexOf(");", desde));
        Matcher m = Pattern.compile("(?m)^\\s+" + columna + "\\s+(.+?),?$").matcher(bloque);
        assertTrue(m.find(), columna + " en " + tabla);
        return m.group(1).strip();
    }

    @Test void laMigracionYCrearBdDicenLoMismo() throws Exception {
        assertEquals(deLaMigracion("NOMBRE"), deCrearBd("Nsys_producto", "NOMBRE"));
        assertEquals(deLaMigracion("COSTE"), deCrearBd("Nsys_movil", "COSTE"));
        assertTrue(deLaMigracion("COSTE").startsWith("DECIMAL(10,2) NULL"), deLaMigracion("COSTE"));
    }

    @Test void elNombreSeCortaAlAnchoDeSuColumna() throws Exception {
        assertEquals("VARCHAR(" + AnchosNsys.NOMBRE + ") NULL", deLaMigracion("NOMBRE").substring(0, "VARCHAR(150) NULL".length()));
    }

    @Test void laMigracionTieneVistaPreviaYComprobacion() throws Exception {
        String sql = fichero("migracion-nombres-y-coste-nsys.sql");
        assertTrue(sql.contains("AS YA_EXISTEN"), "vista previa");
        assertTrue(sql.contains("AS CREADAS"), "comprobación final");
        assertTrue(sql.contains("USE gestion_reparaciones;"));
    }
}
