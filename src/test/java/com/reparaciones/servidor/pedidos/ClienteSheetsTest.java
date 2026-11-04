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

    private final HttpClient http = mock(HttpClient.class);
    private final ClienteSheets sheets = new ClienteSheets(http, () -> "tok-1", Duration.ofSeconds(5));

    @SuppressWarnings("unchecked")
    private static CompletableFuture<HttpResponse<String>> respuesta(String cuerpo) {
        HttpResponse<String> r = mock(HttpResponse.class);
        when(r.statusCode()).thenReturn(200);
        when(r.body()).thenReturn(cuerpo);
        return CompletableFuture.completedFuture(r);
    }

    @Test void leeLaCabeceraCuentaLasFilasPorLaColumnaClaveYTraeSoloLasUltimas() throws Exception {
        doReturn(respuesta("{\"values\":[[\"Fecha\",\"Fecha_Traspaso\",\"Plataforma\",\"SKU\",\"Pendiente\",\"IMEI\"]]}"),
                respuesta("{\"values\":[[\"SKU\"],[\"a\"],[\"b\"],[\"c\"],[\"d\"]]}"),
                respuesta("{\"values\":[[46000.5,\"\",\"Amazon\",\"c\",\"\",\"350000000000001\"],[46001,\"\",\"Amazon\",\"d\"]]}"))
                .when(http).sendAsync(any(HttpRequest.class), any());

        TablaHoja t = sheets.leer("hoja-1", "PEDIDOS 2026", List.of("Fecha", "Plataforma", "SKU", "IMEI"), "SKU", 2);

        assertEquals(4, t.total());
        assertEquals(Map.of("Fecha", 0, "Fecha_Traspaso", 1, "Plataforma", 2, "SKU", 3, "Pendiente", 4, "IMEI", 5), t.columnas());
        assertEquals(List.of(4, 5), t.filas().stream().map(TablaHoja.Fila::numero).toList());
        assertEquals("350000000000001", t.celda(t.filas().get(0), "IMEI").asText());
        assertNull(t.celda(t.filas().get(1), "IMEI"), "fila más corta: celda vacía");
        ArgumentCaptor<HttpRequest> c = ArgumentCaptor.forClass(HttpRequest.class);
        verify(http, times(3)).sendAsync(c.capture(), any());
        List<String> rangos = c.getAllValues().stream()
                .map(p -> URLDecoder.decode(p.uri().getRawPath(), StandardCharsets.UTF_8)).toList();
        assertTrue(rangos.get(0).endsWith("/hoja-1/values/'PEDIDOS 2026'!1:1"), rangos.get(0));
        assertTrue(rangos.get(1).endsWith("/values/'PEDIDOS 2026'!D:D"), rangos.get(1));
        assertTrue(rangos.get(2).endsWith("/values/'PEDIDOS 2026'!A4:F5"), rangos.get(2));
        assertTrue(c.getAllValues().get(2).uri().getQuery().contains("valueRenderOption=UNFORMATTED_VALUE"));
        assertEquals("Bearer tok-1", c.getAllValues().get(0).headers().firstValue("Authorization").orElseThrow());
    }

    @Test void unaColumnaQueFaltaRechazaLaHoja() {
        doReturn(respuesta("{\"values\":[[\"Fecha\",\"SKU\"]]}")).when(http).sendAsync(any(HttpRequest.class), any());
        FormatoHojaException e = assertThrows(FormatoHojaException.class,
                () -> sheets.leer("h", "PEDIDOS 2026", List.of("Fecha", "Plataforma", "SKU", "IMEI"), "SKU", 10));
        assertEquals("Falta la columna «Plataforma» en «PEDIDOS 2026»", e.getMessage());
    }

    @Test void lasLetrasDeLasColumnas() {
        assertEquals(List.of("A", "Z", "AA", "AF"), List.of(ClienteSheets.letra(0), ClienteSheets.letra(25),
                ClienteSheets.letra(26), ClienteSheets.letra(31)));
    }
}
