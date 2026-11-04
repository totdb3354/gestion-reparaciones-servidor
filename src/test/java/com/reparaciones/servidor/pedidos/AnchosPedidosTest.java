package com.reparaciones.servidor.pedidos;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/** Los anchos de AnchosPedidos contra el DDL de la migración y de crear_bd.sql. */
class AnchosPedidosTest {

    private static final Pattern TABLA = Pattern.compile("^CREATE TABLE (Pedido\\w*)\\s*\\(");
    private static final Pattern COLUMNA = Pattern.compile("^\\s+(\\w+)\\s+VARCHAR\\((\\d+)\\)");

    /** «Tabla.COLUMNA» → ancho de cada VARCHAR de las tablas Pedido* del fichero. */
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

    private static void comprueba(Map<String, Integer> ddl) {
        assertEquals(AnchosPedidos.NUMERO, ddl.get("Pedido.NUMERO"));
        assertEquals(AnchosPedidos.CLIENTE, ddl.get("Pedido.CLIENTE"));
        assertEquals(AnchosPedidos.PAIS, ddl.get("Pedido.PAIS"));
        assertEquals(AnchosPedidos.SKU, ddl.get("Pedido_unidad.SKU"));
        assertEquals(AnchosPedidos.IMEI, ddl.get("Pedido_unidad.IMEI"));
        assertEquals(AnchosPedidos.OTRO_ORDER_ID, ddl.get("Pedido_unidad.OTRO_ORDER_ID"));
        assertEquals(AnchosPedidos.SKU_DESCONOCIDOS, ddl.get("Pedido_lectura.SKU_DESCONOCIDOS"));
        assertEquals(AnchosPedidos.MENSAJE, ddl.get("Pedido_lectura.MENSAJE"));
    }

    @Test void coincidenConElDdlDeLaMigracion() throws Exception {
        comprueba(anchos("migracion-pedidos.sql"));
    }

    @Test void coincidenConElDdlDeCrearBd() throws Exception {
        comprueba(anchos("crear_bd.sql"));
    }

    @Test void elOrderIdDeOtroPedidoCabeComoElNumero() {
        assertEquals(AnchosPedidos.NUMERO, AnchosPedidos.OTRO_ORDER_ID);
    }

    @Test void cortaSinPartirUnParSustituto() {
        assertEquals("abc", AnchosPedidos.corta("abcdef", 3));
        assertEquals("ab", AnchosPedidos.corta("ab" + Character.toString(0x1F600), 3));
        assertNull(AnchosPedidos.corta(null, 3));
    }
}
