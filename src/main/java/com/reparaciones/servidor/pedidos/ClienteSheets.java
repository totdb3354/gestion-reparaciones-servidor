package com.reparaciones.servidor.pedidos;

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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;

/** Lee una pestaña entera de una hoja de Google con la API de Sheets, solo lectura, con la cuenta de servicio de la pieza 1
 *  (`nsys.drive.credenciales`). Una lectura son: la cabecera, las dimensiones de la pestaña y la pestaña en trozos de
 *  {@link #FILAS_POR_PETICION} filas, de la columna A a la última que hace falta (no se piden las demás columnas). Se recorren todas
 *  las filas de la pestaña, también las vacías de en medio. Los mensajes de error no llevan el id de la hoja. */
@Component
public class ClienteSheets {

    static final String API = "https://sheets.googleapis.com/v4/spreadsheets/";
    static final String ALCANCE = "https://www.googleapis.com/auth/spreadsheets.readonly";
    /** Un trozo de 5000 filas por unas 8 columnas son unas 40000 celdas: una respuesta de pocos MB. */
    static final int FILAS_POR_PETICION = 5000;
    static final Duration ESPERA = Duration.ofMinutes(2);
    private static final Duration ESPERA_CABECERAS = Duration.ofSeconds(60);
    private static final String OPCIONES = "valueRenderOption=UNFORMATTED_VALUE&dateTimeRenderOption=SERIAL_NUMBER&majorDimension=ROWS";

    interface ProveedorToken {
        String token() throws IOException;
    }

    private final HttpClient http;
    private final ProveedorToken tokens;
    private final Duration espera;
    private final ObjectMapper json = new ObjectMapper();

    @Autowired
    public ClienteSheets(@Value("${nsys.drive.credenciales:}") String credenciales) {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NORMAL).build(),
                cuentaDeServicio(credenciales), ESPERA);
    }

    ClienteSheets(HttpClient http, ProveedorToken tokens, Duration espera) {
        this.http = http;
        this.tokens = tokens;
        this.espera = espera;
    }

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

    /** Todas las filas de datos de la pestaña (la 1 es la cabecera). Lanza {@link FormatoHojaException} si falta una columna
     *  obligatoria o la pestaña. */
    public TablaHoja leerTodo(String hoja, String pestana, FormatoHoja formato) throws IOException, InterruptedException {
        String p = "'" + pestana.replace("'", "''") + "'!";
        List<JsonNode> cabecera = filas(pedir(hoja, "/values/" + codificar(p + "1:1") + "?" + OPCIONES));
        Map<String, Integer> columnas = new HashMap<>();
        if (!cabecera.isEmpty()) {
            JsonNode c = cabecera.get(0);
            for (int i = 0; i < c.size(); i++) {
                String nombre = TablaHoja.texto(c.get(i));
                if (nombre != null) columnas.putIfAbsent(nombre, i);
            }
        }
        for (String o : formato.obligatorias())
            if (!columnas.containsKey(o)) throw new FormatoHojaException("Falta la columna «" + o + "» en «" + pestana + "»");
        int total = filasDeLaPestana(hoja, pestana);
        int ancho = Stream.concat(formato.obligatorias().stream(), formato.opcionales().stream())
                .filter(columnas::containsKey).mapToInt(columnas::get).max().orElse(0);
        String ultima = letra(ancho);
        List<TablaHoja.Fila> lista = new ArrayList<>();
        for (int desde = 2; desde <= total; desde += FILAS_POR_PETICION) {
            int hasta = Math.min(total, desde + FILAS_POR_PETICION - 1);
            List<JsonNode> trozo = filas(pedir(hoja, "/values/" + codificar(p + "A" + desde + ":" + ultima + hasta) + "?" + OPCIONES));
            for (int i = 0; i < trozo.size(); i++) {
                List<JsonNode> celdas = new ArrayList<>();
                trozo.get(i).forEach(celdas::add);
                lista.add(new TablaHoja.Fila(desde + i, celdas));
            }
        }
        return new TablaHoja(columnas, lista);
    }

    /** Cuántas filas tiene la pestaña en la hoja (con las vacías del final); lanza {@link FormatoHojaException} si no existe. */
    private int filasDeLaPestana(String hoja, String pestana) throws IOException, InterruptedException {
        JsonNode meta = pedir(hoja, "?fields=sheets.properties(title,gridProperties.rowCount)");
        for (JsonNode s : meta.path("sheets")) {
            JsonNode prop = s.path("properties");
            if (pestana.equals(prop.path("title").asText())) return prop.path("gridProperties").path("rowCount").asInt(0);
        }
        throw new FormatoHojaException("No existe la pestaña «" + pestana + "»");
    }

    private static List<JsonNode> filas(JsonNode respuesta) {
        List<JsonNode> filas = new ArrayList<>();
        respuesta.path("values").forEach(filas::add);
        return filas;
    }

    /** 0 → A, 25 → Z, 26 → AA. */
    static String letra(int indice) {
        StringBuilder s = new StringBuilder();
        for (int n = indice + 1; n > 0; n = (n - 1) / 26) s.insert(0, (char) ('A' + (n - 1) % 26));
        return s.toString();
    }

    private static String codificar(String rango) {
        return URLEncoder.encode(rango, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private JsonNode pedir(String hoja, String ruta) throws IOException, InterruptedException {
        String url = API + URLEncoder.encode(hoja, StandardCharsets.UTF_8) + ruta;
        HttpRequest peticion = HttpRequest.newBuilder(URI.create(url)).timeout(ESPERA_CABECERAS)
                .header("Authorization", "Bearer " + tokens.token()).GET().build();
        CompletableFuture<HttpResponse<String>> futura = http.sendAsync(peticion, HttpResponse.BodyHandlers.ofString());
        HttpResponse<String> respuesta;
        try {
            respuesta = futura.get(espera.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            futura.cancel(true);
            throw new IOException("Google Sheets no respondió a tiempo");
        } catch (ExecutionException e) {
            if (e.getCause() instanceof IOException io) throw new IOException("Fallo al pedir a Google Sheets (" + io.getClass().getSimpleName() + ")");
            throw new IOException("Fallo al pedir a Google Sheets");
        }
        if (respuesta.statusCode() != 200) throw new IOException("Google Sheets respondió " + respuesta.statusCode());
        return json.readTree(respuesta.body());
    }
}
