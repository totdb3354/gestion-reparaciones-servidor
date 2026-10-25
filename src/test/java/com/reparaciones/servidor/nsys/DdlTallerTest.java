package com.reparaciones.servidor.nsys;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Las tablas del taller (0.9.21) en sql/: el CREATE TABLE de la migración, que es el que se aplica en la VM, y el de
 *  crear_bd.sql son el mismo, y las dos tablas tienen índice por ID_REP (nextId busca el correlativo del día en las dos en
 *  cada alta de R…, y TallerDAO.deReparacion las filtra por ID_REP en cada edición y borrado); el Taller_aviso de
 *  crear_bd.sql lleva además lo de la 0.9.22 —«Revisado»—, que va en su propia migración. */
class DdlTallerTest {

    /** Las líneas del CREATE TABLE de una tabla, del «CREATE TABLE» al «);». */
    private static List<String> crear(String fichero, String tabla) throws Exception {
        List<String> lineas = new ArrayList<>();
        boolean dentro = false;
        for (String linea : Files.readAllLines(Path.of("sql", fichero), StandardCharsets.UTF_8)) {
            if (linea.startsWith("CREATE TABLE " + tabla + " (")) dentro = true;
            if (dentro) lineas.add(linea.stripTrailing());
            if (dentro && linea.startsWith(");")) break;
        }
        assertFalse(lineas.isEmpty(), tabla + " en " + fichero);
        return lineas;
    }

    @Test void laMigracionYCrearBdDicenLoMismo() throws Exception {
        assertEquals(crear("migracion-sku-taller.sql", "Taller_cambio"), crear("crear_bd.sql", "Taller_cambio"));
        // 0.9.22: crear_bd.sql lleva también «Revisado» (REVISADO_POR, REVISADO_EN y su clave), de migracion-plantillas-nsys.sql.
        List<String> aviso = new ArrayList<>(crear("crear_bd.sql", "Taller_aviso"));
        aviso.removeIf(l -> l.contains("REVISADO_"));
        aviso.replaceAll(l -> l.startsWith("    CONSTRAINT fk_taller_aviso_usuario") && l.endsWith(",") ? l.substring(0, l.length() - 1) : l);
        assertEquals(crear("migracion-sku-taller.sql", "Taller_aviso"), aviso);
    }

    @Test void lasDosTablasTienenIndicePorReparacion() throws Exception {
        assertTrue(crear("migracion-sku-taller.sql", "Taller_cambio").contains("    KEY idx_taller_cambio_rep (ID_REP),"));
        assertTrue(crear("migracion-sku-taller.sql", "Taller_aviso").contains("    KEY idx_taller_aviso_rep (ID_REP),"));
    }
}
