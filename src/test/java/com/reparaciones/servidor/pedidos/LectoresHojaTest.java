package com.reparaciones.servidor.pedidos;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class LectoresHojaTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    /** Una tabla con esta cabecera y estas filas (en JSON, como las da Sheets), numeradas desde la 2. */
    static TablaHoja tabla(List<String> cabecera, String... filas) throws Exception {
        Map<String, Integer> columnas = new HashMap<>();
        for (int i = 0; i < cabecera.size(); i++) columnas.put(cabecera.get(i), i);
        List<TablaHoja.Fila> lista = new ArrayList<>();
        for (int i = 0; i < filas.length; i++) {
            List<JsonNode> celdas = new ArrayList<>();
            JSON.readTree(filas[i]).forEach(celdas::add);
            lista.add(new TablaHoja.Fila(i + 2, celdas));
        }
        return new TablaHoja(filas.length, columnas, lista);
    }

    @Test void pedidos2026SoloTraeLasVentasConImeiYFecha() throws Exception {
        TablaHoja t = tabla(List.of("Fecha", "Plataforma", "SKU", "IMEI"),
                "[46329.5, \"Amazon\", \"i13128AzulA\", \"350000000000001\"]",
                "[46329, \"NP\", \"i13128AzulA\", \"350000000000002\"]",
                "[46329, \"Tienda Norte SL\", \"i14256AzulA\", 350000000000003]",
                "[46329, \"INCIDENCIA AMZ\", \"i13128AzulA\", \"350000000000004\"]",
                "[46329, \"Amazon\", \"i13128AzulA\", \"cancelado\"]",
                "[\"\", \"Amazon\", \"i13128AzulA\", \"350000000000006\"]",
                "[46329, \"Amazon\", \"\", \"350000000000007\"]");

        assertEquals(List.of(
                new FilaPedidos2026(2, LocalDateTime.of(2026, 11, 3, 12, 0), "Amazon", "i13128AzulA", "350000000000001"),
                new FilaPedidos2026(4, LocalDateTime.of(2026, 11, 3, 0, 0), "Tienda Norte SL", "i14256AzulA", "350000000000003")),
                LectorPedidos2026.filas(t));
    }

    @Test void simuladorTraeCadaLineaConSuImeiSiLoTiene() throws Exception {
        TablaHoja t = tabla(List.of("Order id", "Linea facturación", "Fecha", "Cliente", "SKU", "Cantidad", "IMEI"),
                "[6488, \"X\", 46328, \"Móviles Levante SL\", \"i13128AzulA\", 1, \"350000000000001\"]",
                "[6488, \"x\", 46328, \"Móviles Levante SL\", \"i14128NegroA\", 1, \"\"]",
                "[6490, \"BB\", 46330, \"TecnoSur\", \"i13128AzulA\", -1, \"350000000000001\"]",
                "[\"\", \"X\", 46330, \"TecnoSur\", \"i13128AzulA\", 1, \"\"]");

        assertEquals(List.of(
                new FilaSimulador(2, "6488", "X", LocalDate.of(2026, 11, 2), "Móviles Levante SL", "i13128AzulA", 1, "350000000000001"),
                new FilaSimulador(3, "6488", "X", LocalDate.of(2026, 11, 2), "Móviles Levante SL", "i14128NegroA", 1, null),
                new FilaSimulador(4, "6490", "BB", LocalDate.of(2026, 11, 4), "TecnoSur", "i13128AzulA", -1, "350000000000001")),
                LectorSimulador.filas(t));
    }

    @Test void lasFechasLleganComoNumeroDeSerieOComoTexto() throws Exception {
        assertEquals(LocalDateTime.of(2026, 11, 3, 6, 0), TablaHoja.fecha(JSON.readTree("46329.25")));
        assertEquals(LocalDateTime.of(2026, 11, 3, 0, 0), TablaHoja.fecha(JSON.readTree("\"03/11/2026\"")));
        assertEquals(LocalDateTime.of(2026, 11, 3, 9, 30), TablaHoja.fecha(JSON.readTree("\"03/11/2026 9:30:00\"")));
        assertNull(TablaHoja.fecha(JSON.readTree("\"\"")));
        assertNull(TablaHoja.fecha(null));
    }

    @Test void pedidoAmazonSeLeeSiEstaYEsNullSiLaColumnaNoExiste() throws Exception {
        TablaHoja con = tabla(List.of("Fecha", "Plataforma", "SKU", "IMEI", "Pedido Amazon"),
                "[46329, \"AMZ\", \"i13128AzulA\", \"350000000000001\", \" 402-0000000-0000001 \"]",
                "[46329, \"AMZ\", \"i13128AzulA\", \"350000000000002\", \"\"]");
        List<FilaPedidos2026> filas = LectorPedidos2026.filas(con);
        assertEquals("402-0000000-0000001", filas.get(0).pedidoAmazon());
        assertNull(filas.get(1).pedidoAmazon());

        TablaHoja sin = tabla(List.of("Fecha", "Plataforma", "SKU", "IMEI"),
                "[46329, \"AMZ\", \"i13128AzulA\", \"350000000000001\"]");
        assertNull(LectorPedidos2026.filas(sin).get(0).pedidoAmazon());
    }
}
