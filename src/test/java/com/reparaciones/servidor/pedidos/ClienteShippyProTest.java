package com.reparaciones.servidor.pedidos;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** ShippyPro sin red: un HttpClient mockeado (sendAsync) y una clave fija (patrón de ClienteDriveTest). */
class ClienteShippyProTest {

    private final HttpClient http = mock(HttpClient.class);
    private final ClienteShippyPro cliente = new ClienteShippyPro(http, () -> "clave-1", Duration.ofSeconds(5));

    @SuppressWarnings("unchecked")
    private void responde(int estado, String cuerpo) {
        HttpResponse<String> r = mock(HttpResponse.class);
        when(r.statusCode()).thenReturn(estado);
        when(r.body()).thenReturn(cuerpo);
        doReturn(CompletableFuture.completedFuture(r)).when(http).sendAsync(any(HttpRequest.class), any());
    }

    @Test void pideLosPendientesConLaClaveYUnAgentePropioYLosTraduce() throws Exception {
        responde(200, """
                {"Result":"OK","Total":2,"Orders":[
                 {"transaction_id":"402-0000000-0000001","date":"1793629400","marketplace_id":"100","name":"Cliente A",
                  "country":"DE","merged":"0","items":[{"sku":"i13128AzulA","quantity":"2","title":"t","price":"1"},
                  {"sku":"","quantity":"1"}]},
                 {"transaction_id":"18832","date":"1793690392","marketplace_id":"300","name":"","country":"",
                  "items":[{"sku":"i14256AzulA","quantity":"1.0"}]}]}""");

        List<PedidoShippyPro> pedidos = cliente.pendientes().pedidos();

        assertEquals(new PedidoShippyPro("402-0000000-0000001", "100", LocalDateTime.of(2026, 11, 2, 15, 23, 20), "Cliente A",
                "DE", List.of(new PedidoShippyPro.Articulo("i13128AzulA", 2))), pedidos.get(0));
        assertEquals(new PedidoShippyPro("18832", "300", LocalDateTime.of(2026, 11, 3, 8, 19, 52), null, null,
                List.of(new PedidoShippyPro.Articulo("i14256AzulA", 1))), pedidos.get(1));
        ArgumentCaptor<HttpRequest> c = ArgumentCaptor.forClass(HttpRequest.class);
        verify(http).sendAsync(c.capture(), any());
        HttpRequest p = c.getValue();
        assertEquals("POST", p.method());
        assertEquals(ClienteShippyPro.API, p.uri().toString());
        assertEquals("Basic " + Base64.getEncoder().encodeToString("clave-1:".getBytes()), p.headers().firstValue("Authorization").orElseThrow());
        assertEquals(ClienteShippyPro.AGENTE, p.headers().firstValue("User-Agent").orElseThrow());
    }

    @Test void unaRespuestaQueNoEs200OUnResultQueNoEsOkEsUnFallo() {
        responde(403, "<html>");
        assertEquals("ShippyPro respondió 403", assertThrows(IOException.class, cliente::pendientes).getMessage());
        responde(200, "{\"Result\":\"ERROR\",\"Error\":\"Invalid API key\"}");
        assertEquals("ShippyPro: ERROR Invalid API key", assertThrows(IOException.class, cliente::pendientes).getMessage());
    }

    @Test void unPedidoSinFechaValidaSeSaltaYSeApuntaYLosDemasSeLeen() throws Exception {
        responde(200, """
                {"Result":"OK","Orders":[
                 {"transaction_id":"402-0000000-0000008","date":"abc","items":[{"sku":"i13128AzulA","quantity":"1"}]},
                 {"transaction_id":"","items":[{"sku":"i13128AzulA","quantity":"1"}]},
                 {"transaction_id":"402-0000000-0000009","date":"1793629400.0","marketplace_id":"100",
                  "items":[{"sku":"i13128AzulA","quantity":"1"}]}]}""");

        ClienteShippyPro.Leidos leidos = cliente.pendientes();

        assertEquals(1, leidos.pedidos().size());
        assertEquals(LocalDateTime.of(2026, 11, 2, 15, 23, 20), leidos.pedidos().get(0).fecha());
        assertEquals(List.of("402-0000000-0000008", "(sin número)"), leidos.descartados());
    }

    @Test void sinRutaDeLaClaveFallaAlPedirNoAlCrearse() {
        ClienteShippyPro sinClave = new ClienteShippyPro("");
        assertEquals("Falta el ajuste pedidos.shippypro.clave",
                assertThrows(IOException.class, sinClave::pendientes).getMessage());
    }
}
