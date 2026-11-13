package com.reparaciones.servidor.pedidos;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class LectorHistoricoTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    /** Serie de 2026-11-02: 46328 (el 30/12/1899 es el día 0). */
    private static final double D2 = 46328;
    // Cabecera A..F: Fecha, Plataforma, SKU, IMEI, Pedido Amazon, Fecha compra
    private static final Map<String, Integer> COLUMNAS = Map.of("Fecha", 0, "Plataforma", 1, "SKU", 2, "IMEI", 3,
            "Pedido Amazon", 4, "Fecha compra", 5);

    private static JsonNode celda(Object o) {
        return JSON.valueToTree(o);
    }

    private static TablaHoja tabla(List<Object>... filas) {
        List<TablaHoja.Fila> lista = new ArrayList<>();
        int n = 2;
        for (List<Object> f : filas) lista.add(new TablaHoja.Fila(n++, f.stream().map(LectorHistoricoTest::celda).toList()));
        return new TablaHoja(COLUMNAS, lista);
    }

    @SafeVarargs private static List<Object> fila(Object... celdas) {
        return java.util.Arrays.asList(celdas);
    }

    @Test void unaFilaNormalSeLeePorNombreDeCabecera() {
        var l = LectorHistorico.leer(tabla(fila(D2, "AMZ", "i13128AzulA", 350000000000001L, "402-0000000-0000001", D2 - 3)));
        assertEquals(1, l.filas().size());
        var f = l.filas().get(0);
        assertEquals(new LectorHistorico.FilaHoja(2, LocalDate.of(2026, 11, 2), "AMZ", "i13128AzulA", "350000000000001",
                "402-0000000-0000001", LocalDate.of(2026, 10, 30)), f);
        assertTrue(f.imeiValido());
        assertEquals(0, l.imeiNoImei());
    }

    @Test void elImeiNumericoSeEscribeConTodasSusCifrasSinPuntoCeroNiNotacionCientifica() {
        for (Object n : List.of(350000000000001L, 350000000000001.0, new java.math.BigDecimal("3.50000000000001E14"))) {
            var l = LectorHistorico.leer(tabla(fila(D2, "AMZ", "i13128AzulA", n)));
            assertEquals("350000000000001", l.filas().get(0).imei(), String.valueOf(n));
        }
    }

    @Test void unImeiTextoQueNoEsImeiSeGuardaTalCualYSeCuenta() {
        var l = LectorHistorico.leer(tabla(fila(D2, "AMZ", "i13128AzulA", "  CANCELADO "), fila(D2, "AMZ", "i13128AzulA", "NO HAY"),
                fila(D2, "AMZ", "i13128AzulA", "350000000000002"), fila(D2, "AMZ", "i13128AzulA", 12345)));
        assertEquals(List.of("CANCELADO", "NO HAY", "350000000000002", "12345"), l.filas().stream().map(LectorHistorico.FilaHoja::imei).toList());
        assertEquals(3, l.imeiNoImei());
        assertFalse(l.filas().get(0).imeiValido());
        assertTrue(l.filas().get(2).imeiValido());
    }

    @Test void unErrorDeCeldaOUnNumeroImposibleSeGuardaVacioYSeCuenta() {
        var l = LectorHistorico.leer(tabla(fila(D2, "AMZ", "i13128AzulA", "#N/A"), fila(D2, "AMZ", "i13128AzulA", 1e30),
                fila(D2, "AMZ", "i13128AzulA", -5), fila(D2, "AMZ", "i13128AzulA", 3.5), fila(D2, "AMZ", "i13128AzulA", "")));
        assertEquals(5, l.filas().size());
        assertTrue(l.filas().stream().allMatch(f -> f.imei() == null));
        assertEquals(4, l.imeiNoImei(), "la celda vacía no cuenta");
    }

    @Test void unaFilaSinPlataformaSkuNiImeiSeDescartaComoVacia() {
        var l = LectorHistorico.leer(tabla(fila(D2, "", "", ""), fila(), fila(D2, "AMZ"), fila(null, null, null, "350000000000001")));
        assertEquals(4, l.leidas());
        assertEquals(2, l.descartadasVacias());
        assertEquals(2, l.filas().size());
    }

    @Test void entraLaFilaQueSoloTieneSkuOSoloImei() {
        var l = LectorHistorico.leer(tabla(fila(D2, "", "i13128AzulA"), fila(D2, "", "", 350000000000001L)));
        assertEquals(2, l.filas().size());
        assertEquals("", l.filas().get(0).plataforma(), "la plataforma vacía se guarda como cadena vacía");
    }

    @Test void sinFechaHeredaLaDeLaFilaAnteriorConFechaYSinNingunaAnteriorSeDescarta() {
        var l = LectorHistorico.leer(tabla(fila(null, "AMZ", "a"), fila(D2, "AMZ", "b"), fila(null, "AMZ", "c"),
                fila("basura", "AMZ", "d"), fila(D2 + 1, "AMZ", "e"), fila(1e12, "AMZ", "f")));
        assertEquals(List.of("b", "c", "d", "e", "f"), l.filas().stream().map(LectorHistorico.FilaHoja::sku).toList());
        assertEquals(LocalDate.of(2026, 11, 2), l.filas().get(1).fecha());
        assertEquals(LocalDate.of(2026, 11, 2), l.filas().get(2).fecha());
        assertEquals(LocalDate.of(2026, 11, 3), l.filas().get(4).fecha(), "un número imposible no es fecha: hereda");
        assertEquals(1, l.sinFechaDescartada());
        assertEquals(3, l.sinFechaHeredada());
    }

    @Test void laFechaPuedeSerTextoYLaFechaDeCompraSoloSiEsFecha() {
        var l = LectorHistorico.leer(tabla(fila("2/11/2026", "AMZ", "a", null, "402-0000000-0000001", "no es fecha"),
                fila("02/11/2026 10:30", "AMZ", "b", null, "", "1/11/2026")));
        assertEquals(LocalDate.of(2026, 11, 2), l.filas().get(0).fecha());
        assertNull(l.filas().get(0).fechaCompra());
        assertEquals(LocalDate.of(2026, 11, 2), l.filas().get(1).fecha());
        assertEquals(LocalDate.of(2026, 11, 1), l.filas().get(1).fechaCompra());
        assertNull(l.filas().get(1).pedido(), "pedido en blanco = sin pedido");
    }

    @Test void lasFilasSeQuedanEnElOrdenDeLaHojaConSuNumeroYSeCuentanLasPlataformas() {
        var l = LectorHistorico.leer(tabla(fila(D2, "AMZ", "a"), fila(D2, "INCIDENCIA AMZ 1", "b"), fila(D2, "AMZ", "c"), fila(D2, "", "d")));
        assertEquals(List.of(2, 4, 5), l.filas().stream().map(LectorHistorico.FilaHoja::numero).toList());
        assertEquals(Map.of("AMZ", 2), l.plataformas());
        assertEquals(1, l.incidenciasDescartadas());
        assertEquals(4, l.leidas());
    }

    @Test void soloEsIncidenciaSiLaPlataformaEmpiezaPorLaPalabraSinEspaciosNiMayusculas() {
        var l = LectorHistorico.leer(tabla(fila(D2, " incidencia amz stock", "a"), fila(D2, "INCIDENCIA", "b"), fila(D2, "Incidencia AMZ DE", "c"),
                fila(D2, "Taller Incidencias SL", "d"), fila(D2, "NP", "e"), fila(D2, "Sin incidencia", "f")));
        assertEquals(3, l.incidenciasDescartadas());
        assertEquals(List.of("Taller Incidencias SL", "NP", "Sin incidencia"), l.filas().stream().map(LectorHistorico.FilaHoja::plataforma).toList());
    }

    @Test void unaIncidenciaNoCuentaComoFechaHeredadaNiComoSinFechaNiComoImeiRaro() {
        var l = LectorHistorico.leer(tabla(fila(null, "INCIDENCIA 1", "a", "CANCELADO"), fila(D2, "AMZ", "b"), fila(null, "incidencia 2", "c", "DEVUELTO")));
        assertEquals(2, l.incidenciasDescartadas());
        assertEquals(0, l.sinFechaDescartada());
        assertEquals(0, l.sinFechaHeredada());
        assertEquals(0, l.imeiNoImei());
        assertEquals(1, l.filas().size());
    }

    @Test void unaTablaSinColumnasOpcionalesSeLeeIgual() {
        var t = new TablaHoja(Map.of("Fecha", 0, "Plataforma", 1, "SKU", 2, "IMEI", 3),
                List.of(new TablaHoja.Fila(2, List.of(celda(D2), celda("AMZ"), celda("a"), celda(350000000000001L)))));
        var f = LectorHistorico.leer(t).filas().get(0);
        assertNull(f.pedido());
        assertNull(f.fechaCompra());
    }
}
