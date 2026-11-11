package com.reparaciones.servidor.pedidos;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ClienteSheetsTest {

    private static final FormatoHoja FORMATO = new FormatoHoja(List.of("Fecha", "Plataforma", "SKU", "IMEI"), List.of("Pedido Amazon"));
    private static final String CABECERA = "{\"values\":[[\"Fecha\",\"Fecha_Traspaso\",\"Plataforma\",\"SKU\",\"Pendiente\",\"IMEI\"]]}";

    private final HttpClient http = mock(HttpClient.class);
    private final ClienteSheets sheets = new ClienteSheets(http, () -> "tok-1", Duration.ofSeconds(5));

    @SuppressWarnings("unchecked")
    private static CompletableFuture<HttpResponse<String>> respuesta(String cuerpo) {
        HttpResponse<String> r = mock(HttpResponse.class);
        when(r.statusCode()).thenReturn(200);
        when(r.body()).thenReturn(cuerpo);
        return CompletableFuture.completedFuture(r);
    }

    private static String filasDe(String pestana, int n) {
        return "{\"sheets\":[{\"properties\":{\"title\":\"otra\",\"gridProperties\":{\"rowCount\":9}}},"
                + "{\"properties\":{\"title\":\"" + pestana + "\",\"gridProperties\":{\"rowCount\":" + n + "}}}]}";
    }

    private List<String> rangos() {
        ArgumentCaptor<HttpRequest> c = ArgumentCaptor.forClass(HttpRequest.class);
        verify(http, atLeastOnce()).sendAsync(c.capture(), any());
        return c.getAllValues().stream().map(p -> URLDecoder.decode(p.uri().getRawPath(), StandardCharsets.UTF_8)).toList();
    }

    @Test void leeLaCabeceraLasFilasDeLaPestanaYSoloHastaLaUltimaColumnaQueHaceFalta() throws Exception {
        doReturn(respuesta(CABECERA), respuesta(filasDe("PEDIDOS 2026", 4)),
                respuesta("{\"values\":[[46000.5,\"\",\"AMZ\",\"c\",\"\",350000000000001],[],[46001,\"\",\"FNSK\",\"d\"]]}"))
                .when(http).sendAsync(any(HttpRequest.class), any());

        TablaHoja t = sheets.leerTodo("hoja-1", "PEDIDOS 2026", FORMATO);

        assertEquals(Map.of("Fecha", 0, "Fecha_Traspaso", 1, "Plataforma", 2, "SKU", 3, "Pendiente", 4, "IMEI", 5), t.columnas());
        assertEquals(List.of(2, 3, 4), t.filas().stream().map(TablaHoja.Fila::numero).toList());
        assertEquals(350000000000001L, t.celda(t.filas().get(0), "IMEI").asLong());
        assertNull(t.celda(t.filas().get(1), "IMEI"), "fila vacía");
        assertNull(t.celda(t.filas().get(2), "IMEI"), "fila más corta: celda vacía");
        List<String> r = rangos();
        assertTrue(r.get(0).endsWith("/hoja-1/values/'PEDIDOS 2026'!1:1"), r.get(0));
        assertTrue(r.get(1).endsWith("/hoja-1"), r.get(1));
        assertTrue(r.get(2).endsWith("/values/'PEDIDOS 2026'!A2:F4"), r.get(2));
        ArgumentCaptor<HttpRequest> c = ArgumentCaptor.forClass(HttpRequest.class);
        verify(http, times(3)).sendAsync(c.capture(), any());
        assertTrue(c.getAllValues().get(2).uri().getQuery().contains("valueRenderOption=UNFORMATTED_VALUE"));
        assertTrue(c.getAllValues().get(2).uri().getQuery().contains("dateTimeRenderOption=SERIAL_NUMBER"));
        assertEquals("Bearer tok-1", c.getAllValues().get(0).headers().firstValue("Authorization").orElseThrow());
    }

    @Test void laHojaEnteraSeLeeEnTrozosAunqueUnoVengaVacio() throws Exception {
        int trozo = ClienteSheets.FILAS_POR_PETICION;
        int total = trozo * 2 + 10; // filas 2..total: tres trozos
        doReturn(respuesta(CABECERA), respuesta(filasDe("P", total)),
                respuesta("{\"values\":[[1,\"\",\"AMZ\",\"a\"]]}"),
                respuesta("{}"), // un trozo entero sin datos en medio de la hoja
                respuesta("{\"values\":[[3,\"\",\"AMZ\",\"z\"]]}"))
                .when(http).sendAsync(any(HttpRequest.class), any());

        TablaHoja t = sheets.leerTodo("h", "P", FORMATO);

        assertEquals(List.of(2, 2 + 2 * trozo), t.filas().stream().map(TablaHoja.Fila::numero).toList());
        List<String> r = rangos();
        assertEquals(5, r.size());
        assertTrue(r.get(2).endsWith("!A2:F" + (trozo + 1)), r.get(2));
        assertTrue(r.get(3).endsWith("!A" + (trozo + 2) + ":F" + (2 * trozo + 1)), r.get(3));
        assertTrue(r.get(4).endsWith("!A" + (2 * trozo + 2) + ":F" + total), r.get(4));
    }

    @Test void unaColumnaOpcionalMasALaDerechaAmpliaElRango() throws Exception {
        doReturn(respuesta("{\"values\":[[\"Fecha\",\"Plataforma\",\"SKU\",\"IMEI\",\"Pendiente\",\"Pedido Amazon\"]]}"),
                respuesta(filasDe("P", 3)), respuesta("{\"values\":[[1,\"AMZ\"],[2,\"AMZ\"]]}"))
                .when(http).sendAsync(any(HttpRequest.class), any());

        TablaHoja t = sheets.leerTodo("h", "P", FORMATO);

        assertEquals(2, t.filas().size());
        assertTrue(rangos().get(2).endsWith("!A2:F3"));
    }

    @Test void sinLaColumnaOpcionalSeLeeHastaLaUltimaObligatoria() throws Exception {
        doReturn(respuesta("{\"values\":[[\"Fecha\",\"Plataforma\",\"SKU\",\"IMEI\"]]}"),
                respuesta(filasDe("P", 3)), respuesta("{\"values\":[[1,\"AMZ\"],[2,\"AMZ\"]]}"))
                .when(http).sendAsync(any(HttpRequest.class), any());

        sheets.leerTodo("h", "P", FORMATO);

        assertTrue(rangos().get(2).endsWith("!A2:D3"));
    }

    @Test void unaColumnaObligatoriaQueFaltaRechazaLaHojaSinLeerNadaMas() {
        doReturn(respuesta("{\"values\":[[\"Fecha\",\"SKU\"]]}")).when(http).sendAsync(any(HttpRequest.class), any());
        FormatoHojaException e = assertThrows(FormatoHojaException.class, () -> sheets.leerTodo("h", "PEDIDOS 2026", FORMATO));
        assertEquals("Falta la columna «Plataforma» en «PEDIDOS 2026»", e.getMessage());
        verify(http, times(1)).sendAsync(any(HttpRequest.class), any());
    }

    @Test void unaPestanaQueNoExisteRechazaLaHoja() {
        doReturn(respuesta(CABECERA), respuesta(filasDe("otra cosa", 5))).when(http).sendAsync(any(HttpRequest.class), any());
        assertThrows(FormatoHojaException.class, () -> sheets.leerTodo("h", "PEDIDOS 2026", FORMATO));
    }

    @Test void unaPestanaSoloConLaCabeceraNoTrae_filas() throws Exception {
        doReturn(respuesta(CABECERA), respuesta(filasDe("P", 1))).when(http).sendAsync(any(HttpRequest.class), any());
        assertTrue(sheets.leerTodo("h", "P", FORMATO).filas().isEmpty());
        verify(http, times(2)).sendAsync(any(HttpRequest.class), any());
    }

    @Test void unErrorHttpEsUnaIOExceptionSinElIdDeLaHojaEnElMensaje() {
        @SuppressWarnings("unchecked") HttpResponse<String> r = mock(HttpResponse.class);
        when(r.statusCode()).thenReturn(403);
        doReturn(CompletableFuture.completedFuture(r)).when(http).sendAsync(any(HttpRequest.class), any());
        java.io.IOException e = assertThrows(java.io.IOException.class, () -> sheets.leerTodo("hoja-secreta", "P", FORMATO));
        assertFalse(e.getMessage().contains("hoja-secreta"));
    }

    @Test void lasLetrasDeLasColumnas() {
        assertEquals(List.of("A", "Z", "AA", "AF"), List.of(ClienteSheets.letra(0), ClienteSheets.letra(25),
                ClienteSheets.letra(26), ClienteSheets.letra(31)));
    }
}
