package com.reparaciones.servidor.pedidos;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/** El PDF de una etiqueta que ShippyPro ya genero (solo lectura, sin guardar nada): pide la URL con GetLabelUrl y descarga el PDF
 *  del CDN de ShippyPro. La URL se valida (https, host de shippypro.com, sin usuario ni puerto), no se siguen redirecciones, la
 *  descarga tiene un tope de tamano y de tiempo y las credenciales de ShippyPro no salen hacia el CDN. Nunca se registra la URL, el
 *  seguimiento ni datos del cliente; al cliente solo le llega un mensaje fijo. */
@Component
public class EtiquetaShippyPro {

    private static final Logger log = LoggerFactory.getLogger(EtiquetaShippyPro.class);
    static final String MSG_FALLO = "No se pudo obtener la etiqueta de ShippyPro. Inténtalo de nuevo.";
    static final int TOPE = 8 * 1024 * 1024;
    static final Duration CONEXION = Duration.ofSeconds(5);
    /** Tiempo total de la descarga (cabeceras y cuerpo), muy por debajo de los 15 s por peticion. */
    static final Duration LECTURA = Duration.ofSeconds(8);
    private static final String SUFIJO = ".shippypro.com";
    private static final String DOMINIO = "shippypro.com";
    private static final byte[] CABECERA_PDF = {'%', 'P', 'D', 'F'};

    private static final ScheduledExecutorService VIGILANTE = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "etiqueta-vigilante");
        t.setDaemon(true);
        return t;
    });

    private final ClienteShippyPro shippy;
    private final HttpClient http;
    private final int tope;
    private final Duration lectura;

    @Autowired
    public EtiquetaShippyPro(ClienteShippyPro shippy) {
        this(shippy, HttpClient.newBuilder().connectTimeout(CONEXION).followRedirects(HttpClient.Redirect.NEVER).build(), TOPE, LECTURA);
    }

    EtiquetaShippyPro(ClienteShippyPro shippy, HttpClient http, int tope, Duration lectura) {
        this.shippy = shippy;
        this.http = http;
        this.tope = tope;
        this.lectura = lectura;
    }

    /** La URL si es https, sin usuario ni puerto, y su host es shippypro.com o un subdominio (sin distinguir mayusculas). */
    static Optional<URI> validar(String url) {
        if (url == null) return Optional.empty();
        URI uri;
        try {
            uri = new URI(url.strip());
        } catch (URISyntaxException e) {
            return Optional.empty();
        }
        String host = uri.getHost();
        if (!"https".equalsIgnoreCase(uri.getScheme()) || host == null || uri.getRawUserInfo() != null
                || (uri.getPort() != -1 && uri.getPort() != 443)) return Optional.empty();
        String h = host.toLowerCase(Locale.ROOT);
        if (!h.equals(DOMINIO) && !(h.endsWith(SUFIJO) && h.length() > SUFIJO.length())) return Optional.empty();
        return Optional.of(uri);
    }

    /** El PDF de la etiqueta del envio. Cualquier fallo es un 502 con el mensaje fijo; en el registro solo van la fila y un motivo corto. */
    public byte[] pdf(int filaId, long envioId) {
        try {
            Optional<String> url = shippy.urlEtiqueta(envioId);
            if (url.isEmpty()) throw fallo(filaId, "ShippyPro no devuelve etiqueta");
            URI uri = validar(url.get()).orElseThrow(() -> fallo(filaId, "la URL de la etiqueta no es de ShippyPro"));
            return descargar(filaId, uri);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw fallo(filaId, "interrumpido");
        } catch (IOException e) {
            throw fallo(filaId, "fallo de red o de ShippyPro");
        }
    }

    private byte[] descargar(int filaId, URI uri) throws IOException, InterruptedException {
        long limite = System.nanoTime() + lectura.toNanos();
        // Sin credenciales: el CDN no las necesita. Las redirecciones estan desactivadas en el cliente.
        HttpRequest peticion = HttpRequest.newBuilder(uri).timeout(lectura).header("User-Agent", ClienteShippyPro.AGENTE).GET().build();
        HttpResponse<InputStream> respuesta = http.send(peticion, HttpResponse.BodyHandlers.ofInputStream());
        InputStream cuerpo = respuesta.body();
        // Cierra la conexion al agotarse el tiempo total aunque la lectura se quede esperando.
        ScheduledFuture<?> alarma = VIGILANTE.schedule(() -> cerrar(cuerpo), Math.max(1, limite - System.nanoTime()), TimeUnit.NANOSECONDS);
        try (cuerpo) {
            if (respuesta.statusCode() != 200) throw fallo(filaId, "la descarga respondio " + respuesta.statusCode());
            long declarado = respuesta.headers().firstValueAsLong("Content-Length").orElse(-1);
            if (declarado > tope) throw fallo(filaId, "etiqueta demasiado grande");
            ByteArrayOutputStream salida = new ByteArrayOutputStream(declarado > 0 ? (int) declarado : 64 * 1024);
            byte[] buffer = new byte[16 * 1024];
            int n;
            while ((n = cuerpo.read(buffer)) != -1) {
                if (salida.size() + n > tope) throw fallo(filaId, "etiqueta demasiado grande");
                salida.write(buffer, 0, n);
            }
            byte[] pdf = salida.toByteArray();
            if (!empiezaPorPdf(pdf)) throw fallo(filaId, "la descarga no es un PDF");
            return pdf;
        } finally {
            alarma.cancel(false);
        }
    }

    private static boolean empiezaPorPdf(byte[] b) {
        if (b.length < CABECERA_PDF.length) return false;
        for (int i = 0; i < CABECERA_PDF.length; i++) if (b[i] != CABECERA_PDF[i]) return false;
        return true;
    }

    private static void cerrar(InputStream in) {
        try {
            in.close();
        } catch (IOException | RuntimeException ignorada) {
            // ya cerrado
        }
    }

    private static ResponseStatusException fallo(int filaId, String motivo) {
        log.warn("Etiqueta de la fila {}: {}", filaId, motivo);
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY, MSG_FALLO);
    }
}
