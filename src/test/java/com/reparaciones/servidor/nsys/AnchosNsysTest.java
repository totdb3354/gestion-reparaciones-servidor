package com.reparaciones.servidor.nsys;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/** Los anchos de AnchosNsys contra el DDL de la migración, que es el que manda. */
class AnchosNsysTest {

    private static final Pattern TABLA = Pattern.compile("^CREATE TABLE (Nsys_\\w+)");
    private static final Pattern COLUMNA = Pattern.compile("^\\s+(\\w+)\\s+VARCHAR\\((\\d+)\\)");
    private static final Pattern ALTER = Pattern.compile("^ALTER TABLE (Nsys_\\w+)");
    private static final Pattern ANADIDA = Pattern.compile("^\\s+ADD COLUMN (\\w+)\\s+VARCHAR\\((\\d+)\\)");

    /** «Tabla.COLUMNA» → ancho de cada VARCHAR de las tablas Nsys_ del fichero. */
    private static Map<String, Integer> anchos(String fichero) throws Exception {
        Map<String, Integer> anchos = new TreeMap<>();
        String tabla = null;
        for (String linea : Files.readAllLines(Path.of("sql", fichero), StandardCharsets.UTF_8)) {
            Matcher t = TABLA.matcher(linea);
            if (t.find()) tabla = t.group(1);
            else if (linea.startsWith(")")) tabla = null;
            Matcher c = COLUMNA.matcher(linea);
            if (tabla != null && c.find()) anchos.put(tabla + "." + c.group(1), Integer.parseInt(c.group(2)));
        }
        return anchos;
    }

    /** «Tabla.COLUMNA» → ancho de cada VARCHAR que una migración posterior añade con «ALTER TABLE … ADD COLUMN» a una tabla Nsys_. */
    private static Map<String, Integer> anadidas(String fichero) throws Exception {
        Map<String, Integer> anadidas = new TreeMap<>();
        String tabla = null;
        for (String linea : Files.readAllLines(Path.of("sql", fichero), StandardCharsets.UTF_8)) {
            Matcher a = ALTER.matcher(linea);
            if (a.find()) tabla = a.group(1);
            Matcher c = ANADIDA.matcher(linea);
            if (tabla != null && c.find()) anadidas.put(tabla + "." + c.group(1), Integer.parseInt(c.group(2)));
            if (tabla != null && linea.contains(";")) tabla = null;
        }
        return anadidas;
    }

    @Test void coincidenConElDdlDeLaMigracion() throws Exception {
        Map<String, Integer> ddl = anchos("migracion-nsys-espejo.sql");
        assertEquals(AnchosNsys.IMEI_SN, ddl.get("Nsys_movil.IMEI_SN"));
        assertEquals(AnchosNsys.BATCH, ddl.get("Nsys_movil.BATCH"));
        assertEquals(AnchosNsys.GOODS, ddl.get("Nsys_movil.GOODS"));
        assertEquals(AnchosNsys.GRADE, ddl.get("Nsys_movil.GRADE"));
        assertEquals(AnchosNsys.STATUS, ddl.get("Nsys_movil.STATUS"));
        assertEquals(AnchosNsys.LOCATION, ddl.get("Nsys_movil.LOCATION"));
        assertEquals(AnchosNsys.SUPPLIER, ddl.get("Nsys_movil.SUPPLIER"));
        assertEquals(AnchosNsys.CLAVE, ddl.get("Nsys_producto.CLAVE"));
        assertEquals(AnchosNsys.MODELO_MAESTRO, ddl.get("Nsys_producto.MODELO_MAESTRO"));
        assertEquals(AnchosNsys.CAPACIDAD, ddl.get("Nsys_producto.CAPACIDAD"));
        assertEquals(AnchosNsys.COLOR, ddl.get("Nsys_producto.COLOR"));
        assertEquals(AnchosNsys.CALIDAD_DETALLE, ddl.get("Nsys_producto.CALIDAD_DETALLE"));
        assertEquals(AnchosNsys.SKU, ddl.get("Nsys_producto.SKU_WEB"));
        assertEquals(AnchosNsys.SKU, ddl.get("Nsys_producto.SKU_AMAZON"));
        assertEquals(AnchosNsys.SKU, ddl.get("Nsys_producto.SKU_SIN_GRADO"));
        assertEquals(AnchosNsys.FICHERO, ddl.get("Nsys_importacion.FICHERO"));
        assertEquals(AnchosNsys.MENSAJE, ddl.get("Nsys_importacion.MENSAJE"));
    }

    @Test void loQueSeCopiaOSeApuntaEnOtrasTablasCabe() throws Exception {
        Map<String, Integer> ddl = anchos("migracion-nsys-espejo.sql");
        // Nsys_movil copia del maestro
        assertEquals(ddl.get("Nsys_producto.SKU_WEB"), ddl.get("Nsys_movil.SKU"));
        assertEquals(ddl.get("Nsys_producto.MODELO"), ddl.get("Nsys_movil.MODELO"));
        assertEquals(ddl.get("Nsys_producto.MODELO_MAESTRO"), ddl.get("Nsys_movil.MODELO_MAESTRO"));
        assertEquals(ddl.get("Nsys_producto.CAPACIDAD"), ddl.get("Nsys_movil.CAPACIDAD"));
        assertEquals(ddl.get("Nsys_producto.COLOR"), ddl.get("Nsys_movil.COLOR"));
        assertEquals(ddl.get("Nsys_producto.CALIDAD_DETALLE"), ddl.get("Nsys_movil.GRADO_DETALLE"));
        // ... y de las cajas
        assertEquals(ddl.get("Nsys_ubicacion.LOCATION"), ddl.get("Nsys_movil.LOCATION"));
        assertEquals(ddl.get("Nsys_ubicacion.UBICACION"), ddl.get("Nsys_movil.UBICACION"));
        assertEquals(ddl.get("Nsys_ubicacion.UBICACION"), ddl.get("Nsys_movil.UBICACION_ULTIMA"));
        // Nsys_evento: la clave igual y los valores (grado, producto, estado, lote, caja o ubicación) caben
        assertEquals(ddl.get("Nsys_movil.IMEI_SN"), ddl.get("Nsys_evento.IMEI_SN"));
        assertEquals(ddl.get("Nsys_movil.BATCH"), ddl.get("Nsys_evento.BATCH"));
        for (String campo : new String[]{"GOODS", "GRADE", "STATUS", "BATCH", "LOCATION", "UBICACION"}) {
            assertTrue(ddl.get("Nsys_movil." + campo) <= ddl.get("Nsys_evento.DE"), campo);
            assertTrue(ddl.get("Nsys_movil." + campo) <= ddl.get("Nsys_evento.A"), campo);
        }
        assertEquals(ddl.get("Nsys_movil.LOCATION"), ddl.get("Nsys_evento.A_NSYS"));
    }

    // Las migraciones posteriores al espejo que añaden columnas a las tablas Nsys_ (0.9.22: Nsys_producto.NOMBRE).
    @Test void crearBdTieneLasMismasTablasDelEspejo() throws Exception {
        Map<String, Integer> migraciones = anchos("migracion-nsys-espejo.sql");
        migraciones.putAll(anadidas("migracion-nombres-y-coste-nsys.sql"));
        assertEquals(migraciones, anchos("crear_bd.sql"));
    }

    @Test void cortaSinPartirUnEmoji() {
        assertNull(AnchosNsys.corta(null, 5));
        assertEquals("abc", AnchosNsys.corta("abc", 5));
        assertEquals("abcde", AnchosNsys.corta("abcdefg", 5));
        assertEquals("abcd", AnchosNsys.corta("abcd📱fg", 5), "el móvil (emoji) no se parte");
    }
}
