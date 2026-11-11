package com.reparaciones.servidor.pedidos;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** La etiqueta sin red: ClienteShippyPro y HttpClient simulados. */
class EtiquetaShippyProTest {

    private static final String URL = "https://cdn.shippypro.com/labels/a.pdf";
    private static final byte[] PDF = "%PDF-1.4 datos de prueba".getBytes(StandardCharsets.US_ASCII);
    private final ClienteShippyPro shippy = mock(ClienteShippyPro.class);
    private final HttpClient http = mock(HttpClient.class);
    private final EtiquetaShippyPro etiquetas = new EtiquetaShippyPro(shippy, http, 1000, Duration.ofSeconds(5));

    // ---------------------------------------------------------------- validar

    @Test void soloSeAceptanUrlsHttpsDeShippyPro() {
        for (String ok : List.of("https://shippypro.com/x.pdf", "https://cdn.shippypro.com/x.pdf", "https://A.B.SHIPPYPRO.COM/x.pdf",
                "https://cdn.shippypro.com:443/x.pdf", " https://cdn.shippypro.com/x.pdf "))
            assertTrue(EtiquetaShippyPro.validar(ok).isPresent(), ok);
        for (String mal : List.of("https://shippypro.com.evil.tld/x.pdf", "https://evilshippypro.com/x.pdf",
                "http://cdn.shippypro.com/x.pdf", "https://a@cdn.shippypro.com@evil/x.pdf", "https://user@cdn.shippypro.com/x.pdf",
                "https://user:pw@cdn.shippypro.com/x.pdf", "https://cdn.shippypro.com:8443/x.pdf", "https://cdn.shippypro.com:80/x.pdf",
                "https://127.0.0.1/x.pdf", "https://[::1]/x.pdf", "https://10.0.0.1:443/x.pdf", "https://.shippypro.com/x.pdf",
                "https://cdn.shippypro.com.evil.tld/x.pdf", "ftp://cdn.shippypro.com/x.pdf", "//cdn.shippypro.com/x.pdf",
                "/x.pdf", "no es una url", "", "https://evil.tld/?h=cdn.shippypro.com", "https://evil.tld#@cdn.shippypro.com"))
            assertTrue(EtiquetaShippyPro.validar(mal).isEmpty(), mal);
        assertTrue(EtiquetaShippyPro.validar(null).isEmpty());
    }

    // ---------------------------------------------------------------- pdf

    @SuppressWarnings("unchecked")
    private HttpResponse<InputStream> descarga(int estado, byte[] cuerpo, Long contentLength) throws Exception {
        HttpResponse<InputStream> r = mock(HttpResponse.class);
        when(r.statusCode()).thenReturn(estado);
        when(r.body()).thenReturn(new ByteArrayInputStream(cuerpo));
        when(r.headers()).thenReturn(HttpHeaders.of(contentLength == null ? Map.of() : Map.of("Content-Length", List.of("" + contentLength)),
                (a, b) -> true));
        doReturn(r).when(http).send(any(HttpRequest.class), any());
        return r;
    }

    private static void es502(Runnable r) {
        ResponseStatusException e = assertThrows(ResponseStatusException.class, r::run);
        assertEquals(502, e.getStatusCode().value());
        assertEquals(EtiquetaShippyPro.MSG_FALLO, e.getReason());
    }

    @Test void descargaElPdfSinCredencialesNiRedireccionesYLoDevuelve() throws Exception {
        when(shippy.urlEtiqueta(eq(77L), any(Duration.class))).thenReturn(Optional.of(URL));
        descarga(200, PDF, (long) PDF.length);

        assertArrayEquals(PDF, etiquetas.pdf(5, 77));

        ArgumentCaptor<HttpRequest> c = ArgumentCaptor.forClass(HttpRequest.class);
        verify(http).send(c.capture(), any());
        assertEquals(URL, c.getValue().uri().toString());
        assertEquals("GET", c.getValue().method());
        assertTrue(c.getValue().headers().firstValue("Authorization").isEmpty(), "las credenciales no salen hacia el CDN");
        assertTrue(c.getValue().timeout().orElseThrow().compareTo(Duration.ofSeconds(15)) < 0);
    }

    @Test void elClienteDeDescargaNoSigueRedireccionesYTieneConexionCorta() {
        HttpClient real = HttpClient.newBuilder().connectTimeout(EtiquetaShippyPro.CONEXION)
                .followRedirects(HttpClient.Redirect.NEVER).build();
        assertEquals(HttpClient.Redirect.NEVER, real.followRedirects());
        assertTrue(EtiquetaShippyPro.CONEXION.plus(EtiquetaShippyPro.LECTURA).compareTo(Duration.ofSeconds(15)) < 0);
        assertEquals(8 * 1024 * 1024, EtiquetaShippyPro.TOPE);
        // El cliente por defecto del componente se construye con esos mismos ajustes.
        assertDoesNotThrow(() -> new EtiquetaShippyPro(shippy));
    }

    @Test void sinEtiquetaOUrlNoValidaEs502YNoSeDescargaNada() throws Exception {
        when(shippy.urlEtiqueta(eq(1L), any(Duration.class))).thenReturn(Optional.empty());
        es502(() -> etiquetas.pdf(5, 1));
        for (String mala : List.of("http://cdn.shippypro.com/a.pdf", "https://shippypro.com.evil.tld/a.pdf", "https://127.0.0.1/a.pdf")) {
            when(shippy.urlEtiqueta(eq(2L), any(Duration.class))).thenReturn(Optional.of(mala));
            es502(() -> etiquetas.pdf(5, 2));
        }
        verifyNoInteractions(http);
    }

    @Test void siShippyProFallaEs502() throws Exception {
        when(shippy.urlEtiqueta(eq(3L), any(Duration.class))).thenThrow(new IOException("ShippyPro: ERROR clave secreta"));
        es502(() -> etiquetas.pdf(5, 3));
        verifyNoInteractions(http);
    }

    @Test void siLaDescargaFallaOsNoEs200Es502() throws Exception {
        when(shippy.urlEtiqueta(eq(4L), any(Duration.class))).thenReturn(Optional.of(URL));
        doThrow(new IOException("conexion")).when(http).send(any(HttpRequest.class), any());
        es502(() -> etiquetas.pdf(5, 4));
        for (int estado : new int[]{302, 403, 404, 500}) {
            descarga(estado, PDF, null);
            es502(() -> etiquetas.pdf(5, 4));
        }
    }

    @Test void siNoEmpiezaPorPdfEs502() throws Exception {
        when(shippy.urlEtiqueta(eq(4L), any(Duration.class))).thenReturn(Optional.of(URL));
        descarga(200, "<html>hola</html>".getBytes(StandardCharsets.US_ASCII), null);
        es502(() -> etiquetas.pdf(5, 4));
        descarga(200, new byte[0], null);
        es502(() -> etiquetas.pdf(5, 4));
        descarga(200, "%PD".getBytes(StandardCharsets.US_ASCII), null);
        es502(() -> etiquetas.pdf(5, 4));
    }

    @Test void siSuperaElTopeEs502AunSinContentLength() throws Exception {
        when(shippy.urlEtiqueta(eq(4L), any(Duration.class))).thenReturn(Optional.of(URL));
        byte[] grande = new byte[1001];
        Arrays.fill(grande, (byte) 'a');
        System.arraycopy(PDF, 0, grande, 0, 4);
        descarga(200, grande, null);
        es502(() -> etiquetas.pdf(5, 4));
        descarga(200, PDF, 5000L);
        es502(() -> etiquetas.pdf(5, 4));
        // Justo en el tope pasa.
        byte[] justo = Arrays.copyOf(grande, 1000);
        descarga(200, justo, null);
        assertArrayEquals(justo, etiquetas.pdf(5, 4));
    }

    @Test void unaLecturaQueSeQuedaEsperandoSeCortaPorElTiempoTotal() throws Exception {
        EtiquetaShippyPro corta = new EtiquetaShippyPro(shippy, http, 1000, Duration.ofMillis(200));
        when(shippy.urlEtiqueta(eq(4L), any(Duration.class))).thenReturn(Optional.of(URL));
        InputStream colgado = new InputStream() {
            private volatile boolean cerrado;

            @Override public int read() throws IOException {
                while (!cerrado) {
                    try {
                        Thread.sleep(10);
                    } catch (InterruptedException e) {
                        throw new IOException(e);
                    }
                }
                throw new IOException("cerrado");
            }

            @Override public void close() {
                cerrado = true;
            }
        };
        @SuppressWarnings("unchecked") HttpResponse<InputStream> r = mock(HttpResponse.class);
        when(r.statusCode()).thenReturn(200);
        when(r.body()).thenReturn(colgado);
        when(r.headers()).thenReturn(HttpHeaders.of(Map.of(), (a, b) -> true));
        doReturn(r).when(http).send(any(HttpRequest.class), any());
        es502(() -> corta.pdf(5, 4));
    }

    @Test void getLabelUrlTieneSuTopeCortoYLaPeticionUnTopeTotal() throws Exception {
        when(shippy.urlEtiqueta(eq(4L), any(Duration.class))).thenReturn(Optional.empty());
        es502(() -> etiquetas.pdf(5, 4));
        ArgumentCaptor<Duration> d = ArgumentCaptor.forClass(Duration.class);
        verify(shippy).urlEtiqueta(eq(4L), d.capture());
        assertEquals(Duration.ofSeconds(6), d.getValue());
        assertTrue(EtiquetaShippyPro.TOTAL.compareTo(Duration.ofSeconds(12)) <= 0);

        // GetLabelUrl tarda mas que el total: no se descarga y es 502.
        EtiquetaShippyPro corta = new EtiquetaShippyPro(shippy, http, 1000, Duration.ofSeconds(5), Duration.ofSeconds(5), Duration.ofMillis(100));
        when(shippy.urlEtiqueta(eq(6L), any(Duration.class))).thenAnswer(i -> {
            Thread.sleep(250);
            return Optional.of(URL);
        });
        es502(() -> corta.pdf(5, 6));
        verifyNoInteractions(http);
    }

    @Test void unaDescargaTruncadaQueDevuelveMenosBytesDeLosDeclaradosEs502() throws Exception {
        when(shippy.urlEtiqueta(eq(4L), any(Duration.class))).thenReturn(Optional.of(URL));
        descarga(200, PDF, PDF.length + 100L);
        es502(() -> etiquetas.pdf(5, 4));
        descarga(200, PDF, PDF.length - 3L);
        es502(() -> etiquetas.pdf(5, 4));
    }

    @Test void siElVigilanteCierraYElReadDevuelveMenosUnoEs502() throws Exception {
        EtiquetaShippyPro corta = new EtiquetaShippyPro(shippy, http, 1000, Duration.ofMillis(100));
        when(shippy.urlEtiqueta(eq(4L), any(Duration.class))).thenReturn(Optional.of(URL));
        InputStream silencioso = new InputStream() {
            private volatile boolean cerrado;
            private boolean primero = true;

            @Override public int read() { return -1; }

            @Override public int read(byte[] b, int off, int len) throws IOException {
                if (primero) {
                    primero = false;
                    System.arraycopy(PDF, 0, b, off, PDF.length);
                    return PDF.length;
                }
                while (!cerrado) {
                    try {
                        Thread.sleep(10);
                    } catch (InterruptedException e) {
                        throw new IOException(e);
                    }
                }
                return -1; // cierra con fin de datos en vez de lanzar: medio fichero que empieza por %PDF
            }

            @Override public void close() { cerrado = true; }
        };
        @SuppressWarnings("unchecked") HttpResponse<InputStream> r = mock(HttpResponse.class);
        when(r.statusCode()).thenReturn(200);
        when(r.body()).thenReturn(silencioso);
        when(r.headers()).thenReturn(HttpHeaders.of(Map.of(), (a, b) -> true));
        doReturn(r).when(http).send(any(HttpRequest.class), any());
        es502(() -> corta.pdf(5, 4));
    }

    @Test void unContentLengthNoNumericoSeTrataComoAusente() throws Exception {
        when(shippy.urlEtiqueta(eq(4L), any(Duration.class))).thenReturn(Optional.of(URL));
        @SuppressWarnings("unchecked") HttpResponse<InputStream> r = mock(HttpResponse.class);
        when(r.statusCode()).thenReturn(200);
        when(r.body()).thenReturn(new ByteArrayInputStream(PDF));
        when(r.headers()).thenReturn(HttpHeaders.of(Map.of("Content-Length", List.of("abc")), (a, b) -> true));
        doReturn(r).when(http).send(any(HttpRequest.class), any());
        assertArrayEquals(PDF, etiquetas.pdf(5, 4));
    }
}
