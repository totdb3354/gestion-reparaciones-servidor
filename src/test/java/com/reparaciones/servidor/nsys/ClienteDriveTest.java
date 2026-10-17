package com.reparaciones.servidor.nsys;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Drive sin red: un HttpClient mockeado (sendAsync) y un token fijo (patrón de TipoCambioDAOTest). */
class ClienteDriveTest {

    private final HttpClient http = mock(HttpClient.class);
    private final ClienteDrive drive = new ClienteDrive(http, () -> "tok-1");

    @SuppressWarnings("unchecked")
    private static <T> CompletableFuture<HttpResponse<T>> respuesta(int estado, T cuerpo) {
        HttpResponse<T> r = mock(HttpResponse.class);
        when(r.statusCode()).thenReturn(estado);
        when(r.body()).thenReturn(cuerpo);
        return CompletableFuture.completedFuture(r);
    }

    private List<HttpRequest> peticiones(int n) throws Exception {
        ArgumentCaptor<HttpRequest> c = ArgumentCaptor.forClass(HttpRequest.class);
        verify(http, times(n)).sendAsync(c.capture(), any());
        return c.getAllValues();
    }

    @Test void listarItemsRecorreTodasLasPaginasConElToken() throws Exception {
        doReturn(respuesta(200, "{\"nextPageToken\":\"p2\",\"files\":[{\"id\":\"f1\",\"name\":\"Items_2026.10.16_09.01.xlsx\","
                        + "\"modifiedTime\":\"2026-10-16T07:01:30.123Z\"}]}"),
                respuesta(200, "{\"files\":[{\"id\":\"f2\",\"name\":\"Items_2026.10.16_09.31.xlsx\","
                        + "\"modifiedTime\":\"2026-10-16T07:31:30Z\"}]}"))
                .when(http).sendAsync(any(HttpRequest.class), any());

        List<ClienteDrive.FicheroDrive> ficheros = drive.listarItems("carpeta-1");

        assertEquals(List.of(
                new ClienteDrive.FicheroDrive("f1", "Items_2026.10.16_09.01.xlsx", Instant.parse("2026-10-16T07:01:30.123Z")),
                new ClienteDrive.FicheroDrive("f2", "Items_2026.10.16_09.31.xlsx", Instant.parse("2026-10-16T07:31:30Z"))),
                ficheros);
        List<HttpRequest> pedidas = peticiones(2);
        String primera = pedidas.get(0).uri().toString();
        assertTrue(primera.contains("q=%27carpeta-1%27%20in%20parents%20and%20name%20contains%20%27Items_%27"), primera);
        assertTrue(primera.contains("supportsAllDrives=true&includeItemsFromAllDrives=true"), primera);
        assertFalse(primera.contains("pageToken"));
        assertTrue(pedidas.get(1).uri().toString().endsWith("&pageToken=p2"));
        assertEquals("Bearer tok-1", pedidas.get(0).headers().firstValue("Authorization").orElseThrow());
    }

    @Test void metadatosYDescarga() throws Exception {
        doReturn(respuesta(200, "{\"id\":\"m1\",\"name\":\"Maestro.xlsx\",\"modifiedTime\":\"2026-10-15T10:00:00Z\"}"),
                respuesta(200, new byte[]{1, 2, 3}))
                .when(http).sendAsync(any(HttpRequest.class), any());

        assertEquals(new ClienteDrive.FicheroDrive("m1", "Maestro.xlsx", Instant.parse("2026-10-15T10:00:00Z")), drive.metadatos("m1"));
        assertArrayEquals(new byte[]{1, 2, 3}, drive.descargar("m1"));
        List<HttpRequest> pedidas = peticiones(2);
        assertTrue(pedidas.get(0).uri().toString().contains("/files/m1?fields=id%2Cname%2CmodifiedTime&supportsAllDrives=true"));
        assertTrue(pedidas.get(1).uri().toString().endsWith("/files/m1?alt=media&supportsAllDrives=true"));
    }

    @Test void unaRespuestaQueNoEs200EsUnFallo() throws Exception {
        doReturn(respuesta(404, "{}")).when(http).sendAsync(any(HttpRequest.class), any());
        IOException e = assertThrows(IOException.class, () -> drive.metadatos("m1"));
        assertEquals("Drive respondió 404 a https://www.googleapis.com/drive/v3/files/m1", e.getMessage());
    }

    @Test void unFalloDeRedLlegaComoElIOExceptionDeSiempre() throws Exception {
        doReturn(CompletableFuture.failedFuture(new java.net.ConnectException("sin red")))
                .when(http).sendAsync(any(HttpRequest.class), any());
        IOException e = assertThrows(IOException.class, () -> drive.descargar("m1"));
        assertEquals("sin red", e.getMessage());
    }

    @Test void siDriveNoTerminaATiempoSeCortaConUnIOExceptionSinLaConsulta() throws Exception {
        // HttpRequest.timeout solo cubre la espera de las cabeceras: un cuerpo que no acaba colgaría la tarea programada.
        List<CompletableFuture<HttpResponse<Object>>> colgadas = new java.util.ArrayList<>();
        doAnswer(inv -> {
            CompletableFuture<HttpResponse<Object>> nunca = new CompletableFuture<>();   // nunca se completa
            colgadas.add(nunca);
            return nunca;
        }).when(http).sendAsync(any(HttpRequest.class), any());
        ClienteDrive conPrisa = new ClienteDrive(http, () -> "tok-1", Duration.ofMillis(50), Duration.ofMillis(50));

        IOException descarga = assertTimeoutPreemptively(Duration.ofSeconds(5),
                () -> assertThrows(IOException.class, () -> conPrisa.descargar("m1")));
        assertEquals("Drive no respondió a tiempo a https://www.googleapis.com/drive/v3/files/m1", descarga.getMessage());
        assertTrue(colgadas.get(0).isCancelled(), "la petición colgada se cancela");

        IOException lista = assertTimeoutPreemptively(Duration.ofSeconds(5),
                () -> assertThrows(IOException.class, () -> conPrisa.listarItems("carpeta-1")));
        assertFalse(lista.getMessage().contains("carpeta-1"), "sin la consulta: " + lista.getMessage());
        assertFalse(lista.getMessage().contains("?"), lista.getMessage());
        assertTrue(colgadas.get(1).isCancelled());
    }

    @Test void siSeInterrumpeLaEsperaSeCancelaYSigueInterrumpido() throws Exception {
        CompletableFuture<HttpResponse<Object>> colgada = new CompletableFuture<>();
        doReturn(colgada).when(http).sendAsync(any(HttpRequest.class), any());
        Thread.currentThread().interrupt();
        try {
            assertThrows(InterruptedException.class, () -> drive.descargar("m1"));
        } finally {
            Thread.interrupted();                                       // no dejar el hilo de JUnit interrumpido
        }
        assertTrue(colgada.isCancelled());
    }

    @Test void sinCredencialesFallaAlPedirElTokenNoAlCrearse() {
        ClienteDrive.ProveedorToken sinClave = ClienteDrive.cuentaDeServicio("");
        IOException e = assertThrows(IOException.class, sinClave::token);
        assertEquals("Falta el ajuste nsys.drive.credenciales", e.getMessage());
        assertDoesNotThrow(() -> new ClienteDrive(""), "el contexto arranca sin el fichero de la clave");
    }
}
