package com.reparaciones.servidor.nsys;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Drive sin red: un HttpClient mockeado y un token fijo (patrón de TipoCambioDAOTest). */
class ClienteDriveTest {

    private final HttpClient http = mock(HttpClient.class);
    private final ClienteDrive drive = new ClienteDrive(http, () -> "tok-1");

    @SuppressWarnings("unchecked")
    private static <T> HttpResponse<T> respuesta(int estado, T cuerpo) {
        HttpResponse<T> r = mock(HttpResponse.class);
        when(r.statusCode()).thenReturn(estado);
        when(r.body()).thenReturn(cuerpo);
        return r;
    }

    private List<HttpRequest> peticiones(int n) throws Exception {
        ArgumentCaptor<HttpRequest> c = ArgumentCaptor.forClass(HttpRequest.class);
        verify(http, times(n)).send(c.capture(), any());
        return c.getAllValues();
    }

    @Test void listarItemsRecorreTodasLasPaginasConElToken() throws Exception {
        doReturn(respuesta(200, "{\"nextPageToken\":\"p2\",\"files\":[{\"id\":\"f1\",\"name\":\"Items_2026.10.16_09.01.xlsx\","
                        + "\"modifiedTime\":\"2026-10-16T07:01:30.123Z\"}]}"),
                respuesta(200, "{\"files\":[{\"id\":\"f2\",\"name\":\"Items_2026.10.16_09.31.xlsx\","
                        + "\"modifiedTime\":\"2026-10-16T07:31:30Z\"}]}"))
                .when(http).send(any(HttpRequest.class), any());

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
                .when(http).send(any(HttpRequest.class), any());

        assertEquals(new ClienteDrive.FicheroDrive("m1", "Maestro.xlsx", Instant.parse("2026-10-15T10:00:00Z")), drive.metadatos("m1"));
        assertArrayEquals(new byte[]{1, 2, 3}, drive.descargar("m1"));
        List<HttpRequest> pedidas = peticiones(2);
        assertTrue(pedidas.get(0).uri().toString().contains("/files/m1?fields=id%2Cname%2CmodifiedTime&supportsAllDrives=true"));
        assertTrue(pedidas.get(1).uri().toString().endsWith("/files/m1?alt=media&supportsAllDrives=true"));
    }

    @Test void unaRespuestaQueNoEs200EsUnFallo() throws Exception {
        doReturn(respuesta(404, "{}")).when(http).send(any(HttpRequest.class), any());
        IOException e = assertThrows(IOException.class, () -> drive.metadatos("m1"));
        assertEquals("Drive respondió 404 a https://www.googleapis.com/drive/v3/files/m1", e.getMessage());
    }

    @Test void sinCredencialesFallaAlPedirElTokenNoAlCrearse() {
        ClienteDrive.ProveedorToken sinClave = ClienteDrive.cuentaDeServicio("");
        IOException e = assertThrows(IOException.class, sinClave::token);
        assertEquals("Falta el ajuste nsys.drive.credenciales", e.getMessage());
        assertDoesNotThrow(() -> new ClienteDrive(""), "el contexto arranca sin el fichero de la clave");
    }
}
