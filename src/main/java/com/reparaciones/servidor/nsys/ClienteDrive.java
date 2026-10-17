package com.reparaciones.servidor.nsys;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.auth.oauth2.GoogleCredentials;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Lee de Drive, en solo lectura, con la cuenta de servicio propia del ERP (spec pieza 1 §2 y §5.1): listar la carpeta de
 *  los Items, leer los metadatos de un fichero y descargarlo. Mismo patrón que el bot de WhatsApp de las automatizaciones. */
@Component
public class ClienteDrive {

    static final String API = "https://www.googleapis.com/drive/v3/files";
    static final String ALCANCE = "https://www.googleapis.com/auth/drive.readonly";
    /** Hasta las cabeceras de la respuesta (HttpRequest.timeout). */
    private static final Duration ESPERA_CABECERAS = Duration.ofSeconds(60);
    /** Plazo de toda la petición, cuerpo incluido: descargar un fichero, y listar la carpeta o leer metadatos. */
    static final Duration ESPERA_DESCARGA = Duration.ofMinutes(2);
    static final Duration ESPERA_CONSULTA = Duration.ofMinutes(1);

    public record FicheroDrive(String id, String nombre, Instant modificado) {}

    /** Da el token de acceso de Google; los tests lo sustituyen por uno fijo. */
    interface ProveedorToken {
        String token() throws IOException;
    }

    private final HttpClient http;
    private final ProveedorToken tokens;
    private final Duration esperaDescarga;
    private final Duration esperaConsulta;
    private final ObjectMapper json = new ObjectMapper();

    @Autowired
    public ClienteDrive(@Value("${nsys.drive.credenciales:}") String credenciales) {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NORMAL).build(),
                cuentaDeServicio(credenciales));
    }

    /** Paquete: para que los tests sustituyan la red y el token. */
    ClienteDrive(HttpClient http, ProveedorToken tokens) {
        this(http, tokens, ESPERA_DESCARGA, ESPERA_CONSULTA);
    }

    /** Paquete: los tests acortan los plazos. */
    ClienteDrive(HttpClient http, ProveedorToken tokens, Duration esperaDescarga, Duration esperaConsulta) {
        this.http = http;
        this.tokens = tokens;
        this.esperaDescarga = esperaDescarga;
        this.esperaConsulta = esperaConsulta;
    }

    /** Credenciales perezosas: se leen la primera vez que hacen falta, así el contexto arranca sin el fichero (tests y
     *  entornos con la importación apagada). */
    static ProveedorToken cuentaDeServicio(String ruta) {
        return new ProveedorToken() {
            private GoogleCredentials credenciales;

            @Override public synchronized String token() throws IOException {
                if (credenciales == null) {
                    if (ruta == null || ruta.isBlank()) throw new IOException("Falta el ajuste nsys.drive.credenciales");
                    try (InputStream in = Files.newInputStream(Path.of(ruta))) {
                        credenciales = GoogleCredentials.fromStream(in).createScoped(List.of(ALCANCE));
                    }
                }
                credenciales.refreshIfExpired();
                return credenciales.getAccessToken().getTokenValue();
            }
        };
    }

    /** Los ficheros «Items_*.xlsx» de la carpeta, recorriendo todas las páginas. */
    public List<FicheroDrive> listarItems(String carpeta) throws IOException, InterruptedException {
        String consulta = "'" + carpeta + "' in parents and name contains 'Items_' and trashed = false";
        List<FicheroDrive> ficheros = new ArrayList<>();
        String pagina = null;
        do {
            String url = API + "?q=" + codificar(consulta) + "&fields=" + codificar("nextPageToken,files(id,name,modifiedTime)")
                    + "&pageSize=1000&supportsAllDrives=true&includeItemsFromAllDrives=true&corpora=allDrives"
                    + (pagina == null ? "" : "&pageToken=" + codificar(pagina));
            JsonNode cuerpo = json.readTree(pedir(url, HttpResponse.BodyHandlers.ofString(), esperaConsulta));
            for (JsonNode f : cuerpo.path("files")) ficheros.add(fichero(f));
            pagina = cuerpo.path("nextPageToken").asText(null);
        } while (pagina != null);
        return ficheros;
    }

    public FicheroDrive metadatos(String id) throws IOException, InterruptedException {
        String url = API + "/" + codificar(id) + "?fields=" + codificar("id,name,modifiedTime") + "&supportsAllDrives=true";
        return fichero(json.readTree(pedir(url, HttpResponse.BodyHandlers.ofString(), esperaConsulta)));
    }

    public byte[] descargar(String id) throws IOException, InterruptedException {
        return pedir(API + "/" + codificar(id) + "?alt=media&supportsAllDrives=true", HttpResponse.BodyHandlers.ofByteArray(),
                esperaDescarga);
    }

    /** Una petición con plazo para todo, cuerpo incluido. HttpRequest.timeout solo cubre la espera de las cabeceras, y la
     *  importación, el histórico y la tarea de medianoche comparten el único hilo del planificador de Spring: un cuerpo
     *  que no acaba los congelaría a todos. */
    private <T> T pedir(String url, HttpResponse.BodyHandler<T> cuerpo, Duration espera) throws IOException, InterruptedException {
        String sinConsulta = url.substring(0, url.indexOf('?'));
        Duration cabeceras = espera.compareTo(ESPERA_CABECERAS) < 0 ? espera : ESPERA_CABECERAS;
        HttpRequest peticion = HttpRequest.newBuilder(URI.create(url)).timeout(cabeceras)
                .header("Authorization", "Bearer " + tokens.token()).GET().build();
        CompletableFuture<HttpResponse<T>> futura = http.sendAsync(peticion, cuerpo);
        HttpResponse<T> respuesta;
        try {
            respuesta = futura.get(espera.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            futura.cancel(true);
            throw new IOException("Drive no respondió a tiempo a " + sinConsulta);
        } catch (InterruptedException e) {
            futura.cancel(true);
            throw e;
        } catch (ExecutionException e) {
            Throwable causa = e.getCause();
            if (causa instanceof IOException io) throw io;
            if (causa instanceof RuntimeException r) throw r;
            if (causa instanceof Error er) throw er;
            throw new IOException("Fallo al pedir a Drive " + sinConsulta, causa);
        }
        if (respuesta.statusCode() != 200)
            throw new IOException("Drive respondió " + respuesta.statusCode() + " a " + sinConsulta);
        return respuesta.body();
    }

    private static FicheroDrive fichero(JsonNode f) {
        return new FicheroDrive(f.path("id").asText(), f.path("name").asText(), Instant.parse(f.path("modifiedTime").asText()));
    }

    private static String codificar(String texto) {
        return URLEncoder.encode(texto, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
