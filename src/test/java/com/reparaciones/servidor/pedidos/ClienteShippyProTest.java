package com.reparaciones.servidor.pedidos;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
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

    @Test void unaRespuestaSinListaDePedidosEsUnFalloNoCeroPendientes() {
        responde(200, "{\"Result\":\"OK\",\"Total\":0}");
        assertEquals("ShippyPro: la respuesta no trae la lista de pedidos", assertThrows(IOException.class, cliente::pendientes).getMessage());
        responde(200, "{\"Result\":\"OK\",\"Total\":0,\"Orders\":\"nada\"}");
        assertEquals("ShippyPro: la respuesta no trae la lista de pedidos", assertThrows(IOException.class, cliente::pendientes).getMessage());
        responde(200, "{\"Result\":\"OK\",\"Total\":0,\"Orders\":{}}");
        assertEquals("ShippyPro: la respuesta no trae la lista de pedidos", assertThrows(IOException.class, cliente::pendientes).getMessage());
    }

    @Test void siElTotalNoCuadraConLosPedidosQueTraeEsUnFallo() throws Exception {
        responde(200, """
                {"Result":"OK","Total":3,"Orders":[
                 {"transaction_id":"402-0000000-0000001","date":"1793629400","items":[{"sku":"i13128AzulA","quantity":"1"}]}]}""");
        assertEquals("ShippyPro dice Total=3 pero trae 1 pedidos", assertThrows(IOException.class, cliente::pendientes).getMessage());
        responde(200, """
                {"Result":"OK","Total":1,"Orders":[
                 {"transaction_id":"402-0000000-0000001","date":"1793629400","items":[{"sku":"i13128AzulA","quantity":"1"}]}]}""");
        assertEquals(1, cliente.pendientes().pedidos().size());
    }

    @Test void unaFechaImposibleSaltaSoloEsePedido() throws Exception {
        responde(200, """
                {"Result":"OK","Total":2,"Orders":[
                 {"transaction_id":"402-0000000-0000008","date":"1e18","items":[{"sku":"i13128AzulA","quantity":"1"}]},
                 {"transaction_id":"402-0000000-0000009","date":"1793629400","items":[{"sku":"i13128AzulA","quantity":"1"}]}]}""");

        ClienteShippyPro.Leidos leidos = cliente.pendientes();

        assertEquals(1, leidos.pedidos().size());
        assertEquals(List.of("402-0000000-0000008"), leidos.descartados());
    }

    @Test void pideLosEnviadosPorFechasYSoloLeeLoQueNecesita() throws Exception {
        responde(200, """
                {"Result":"OK","Total":4,"Orders":[
                 {"transaction_id":"402-0000000-0000011","marketplace_id":"100","tracking_code":"1Z0000000000000001",
                  "carrier_name":"UPSv2","created_at":"1793629400","printed_at":"1793629500",
                  "to_name":"Cliente Web A","to_address":"Calle Falsa 1","to_email":"a@example.com"},
                 {"transaction_id":"","marketplace_id":"100","created_at":"1793629400"},
                 {"transaction_id":"402-0000000-0000012","marketplace_id":"0","tracking_code":"","carrier_name":"","created_at":"1793690392.0"},
                 {"transaction_id":"402-0000000-0000013","marketplace_id":"100","created_at":"abc"}]}""");

        List<ClienteShippyPro.EnvioShippyPro> envios =
                cliente.enviados(Instant.ofEpochSecond(1793400000L), Instant.ofEpochSecond(1793700000L));

        assertEquals(List.of(
                new ClienteShippyPro.EnvioShippyPro("402-0000000-0000011", "100", LocalDateTime.of(2026, 11, 2, 15, 23, 20),
                        "UPSv2", "1Z0000000000000001"),
                new ClienteShippyPro.EnvioShippyPro("402-0000000-0000012", "0", LocalDateTime.of(2026, 11, 3, 8, 19, 52),
                        null, null)), envios);
        ArgumentCaptor<HttpRequest> c = ArgumentCaptor.forClass(HttpRequest.class);
        verify(http).sendAsync(c.capture(), any());
        HttpRequest p = c.getValue();
        assertEquals("POST", p.method());
        assertEquals("Basic " + Base64.getEncoder().encodeToString("clave-1:".getBytes()), p.headers().firstValue("Authorization").orElseThrow());
        assertEquals(ClienteShippyPro.AGENTE, p.headers().firstValue("User-Agent").orElseThrow());
        assertEquals("{\"Method\":\"GetShippedOrders\",\"Params\":{\"DateMin\":1793400000,\"DateMax\":1793700000}}",
                cuerpoDe(p));
    }

    @Test void losEnviadosTraenElIdDelEnvioYCuandoSeImprimioLaEtiqueta() throws Exception {
        responde(200, """
                {"Result":"OK","Total":8,"Orders":[
                 {"transaction_id":"402-0000000-0000021","marketplace_id":"100","created_at":"1793629400",
                  "ordine_id":"5001","is_printed":"1","printed_at":"1793629500"},
                 {"transaction_id":"402-0000000-0000022","marketplace_id":"100","created_at":"1793629400",
                  "ordine_id":5002,"is_printed":1,"printed_at":"1793629500.0"},
                 {"transaction_id":"402-0000000-0000023","marketplace_id":"100","created_at":"1793629400",
                  "ordine_id":"5003","is_printed":"0","printed_at":"1793629500"},
                 {"transaction_id":"402-0000000-0000024","marketplace_id":"100","created_at":"1793629400",
                  "ordine_id":"5004","is_printed":"1","printed_at":""},
                 {"transaction_id":"402-0000000-0000025","marketplace_id":"100","created_at":"1793629400",
                  "ordine_id":"5005","is_printed":"1","printed_at":"0"},
                 {"transaction_id":"402-0000000-0000026","marketplace_id":"100","created_at":"1793629400",
                  "ordine_id":"5006","is_printed":"1","printed_at":"abc"},
                 {"transaction_id":"402-0000000-0000027","marketplace_id":"100","created_at":"1793629400",
                  "ordine_id":"","is_printed":"","printed_at":""},
                 {"transaction_id":"402-0000000-0000028","marketplace_id":"100","created_at":"1793629400",
                  "ordine_id":"abc","is_printed":"abc"}]}""");

        List<ClienteShippyPro.EnvioShippyPro> e =
                cliente.enviados(Instant.ofEpochSecond(1793400000L), Instant.ofEpochSecond(1793700000L));

        LocalDateTime envio = LocalDateTime.of(2026, 11, 2, 15, 23, 20);
        LocalDateTime impresa = LocalDateTime.of(2026, 11, 2, 15, 25, 0);
        assertEquals(8, e.size());
        assertEquals(5001L, e.get(0).idEnvio());
        assertEquals(impresa, e.get(0).impresaEn());
        assertEquals(5002L, e.get(1).idEnvio(), "el id como numero");
        assertEquals(impresa, e.get(1).impresaEn(), "is_printed como numero y printed_at con decimales");
        assertEquals(5003L, e.get(2).idEnvio());
        assertNull(e.get(2).impresaEn(), "is_printed 0: sin imprimir aunque venga printed_at");
        assertEquals(envio, e.get(3).impresaEn(), "impresa sin printed_at: la fecha del envio");
        assertEquals(envio, e.get(4).impresaEn(), "printed_at 0: la fecha del envio");
        assertEquals(envio, e.get(5).impresaEn(), "printed_at no valido: la fecha del envio");
        assertNull(e.get(6).idEnvio());
        assertNull(e.get(6).impresaEn());
        assertNull(e.get(7).idEnvio(), "ordine_id no numerico");
        assertNull(e.get(7).impresaEn(), "is_printed no valido: sin imprimir");
    }

    @Test void losEnviadosFallanComoLosPendientes() {
        Instant a = Instant.ofEpochSecond(1), b = Instant.ofEpochSecond(2);
        responde(500, "x");
        assertEquals("ShippyPro respondió 500", assertThrows(IOException.class, () -> cliente.enviados(a, b)).getMessage());
        responde(200, "{\"Result\":\"ERROR\",\"Error\":\"Invalid API key\"}");
        assertEquals("ShippyPro: ERROR Invalid API key", assertThrows(IOException.class, () -> cliente.enviados(a, b)).getMessage());
        responde(200, "{\"Result\":\"OK\",\"Total\":0}");
        assertEquals("ShippyPro: la respuesta no trae la lista de pedidos",
                assertThrows(IOException.class, () -> cliente.enviados(a, b)).getMessage());
    }

    private static String cuerpoDe(HttpRequest p) {
        StringBuilder sb = new StringBuilder();
        p.bodyPublisher().orElseThrow().subscribe(new java.util.concurrent.Flow.Subscriber<>() {
            @Override public void onSubscribe(java.util.concurrent.Flow.Subscription s) { s.request(Long.MAX_VALUE); }
            @Override public void onNext(java.nio.ByteBuffer b) { sb.append(java.nio.charset.StandardCharsets.UTF_8.decode(b)); }
            @Override public void onError(Throwable t) { throw new IllegalStateException(t); }
            @Override public void onComplete() {}
        });
        return sb.toString();
    }

    @Test void sinRutaDeLaClaveFallaAlPedirNoAlCrearse() {
        ClienteShippyPro sinClave = new ClienteShippyPro("");
        assertEquals("Falta el ajuste pedidos.shippypro.clave",
                assertThrows(IOException.class, sinClave::pendientes).getMessage());
    }

    @Test void urlEtiquetaPideGetLabelUrlConElIdEnteroYTomaLaUrlComoTextoOComoLista() throws Exception {
        responde(200, "{\"Result\":\"OK\",\"LabelURL\":\"https://cdn.shippypro.com/a.pdf\"}");
        assertEquals("https://cdn.shippypro.com/a.pdf", cliente.urlEtiqueta(123456).orElseThrow());
        ArgumentCaptor<HttpRequest> c = ArgumentCaptor.forClass(HttpRequest.class);
        verify(http).sendAsync(c.capture(), any());
        assertEquals(ClienteShippyPro.API, c.getValue().uri().toString());
        assertEquals("Basic " + Base64.getEncoder().encodeToString("clave-1:".getBytes()),
                c.getValue().headers().firstValue("Authorization").orElseThrow());
        assertTrue(c.getValue().bodyPublisher().orElseThrow().contentLength() > 0);

        responde(200, "{\"Result\":\"OK\",\"LabelURL\":[\"https://cdn.shippypro.com/b.pdf\",\"https://cdn.shippypro.com/c.pdf\"]}");
        assertEquals("https://cdn.shippypro.com/b.pdf", cliente.urlEtiqueta(1).orElseThrow());
    }

    @Test void urlEtiquetaSinEtiquetaEsVacioYUnFalloDeShippyProEsUnaExcepcion() throws Exception {
        for (String cuerpo : List.of("{\"Result\":\"OK\"}", "{\"Result\":\"OK\",\"LabelURL\":\"\"}",
                "{\"Result\":\"OK\",\"LabelURL\":[]}", "{\"Result\":\"OK\",\"LabelURL\":5}")) {
            responde(200, cuerpo);
            assertTrue(cliente.urlEtiqueta(1).isEmpty(), cuerpo);
        }
        responde(200, "{\"Result\":\"ERROR\",\"Error\":\"x\"}");
        assertThrows(IOException.class, () -> cliente.urlEtiqueta(1));
        responde(500, "");
        assertThrows(IOException.class, () -> cliente.urlEtiqueta(1));
    }

    @Test void urlEtiquetaConTopeCortoNoEsperaAUnaShippyProQueNoRespondeMas() {
        doReturn(new CompletableFuture<HttpResponse<String>>()).when(http).sendAsync(any(HttpRequest.class), any());
        long ini = System.nanoTime();
        assertThrows(IOException.class, () -> cliente.urlEtiqueta(1, Duration.ofMillis(150)));
        assertTrue(System.nanoTime() - ini < 3_000_000_000L);
        ArgumentCaptor<HttpRequest> c = ArgumentCaptor.forClass(HttpRequest.class);
        verify(http).sendAsync(c.capture(), any());
        assertEquals(Duration.ofMillis(150), c.getValue().timeout().orElseThrow());
    }
}
