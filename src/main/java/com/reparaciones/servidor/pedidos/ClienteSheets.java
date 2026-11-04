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

/** Lee pestañas de hojas de Google con la API de Sheets, solo lectura (spec pieza 2 §4.2-4.3), con la cuenta de servicio de la
 *  pieza 1 (`nsys.drive.credenciales`). Cada lectura son tres llamadas: la cabecera, la columna clave (para saber cuántas filas
 *  hay) y las últimas N filas (ajuste 3 de la spec). */
@Component
public class ClienteSheets {

    static final String API = "https://sheets.googleapis.com/v4/spreadsheets/";
    static final String ALCANCE = "https://www.googleapis.com/auth/spreadsheets.readonly";
    private static final Duration ESPERA_CABECERAS = Duration.ofSeconds(60);
    static final Duration ESPERA = Duration.ofMinutes(2);
    private static final String OPCIONES = "?valueRenderOption=UNFORMATTED_VALUE&dateTimeRenderOption=SERIAL_NUMBER&majorDimension=ROWS";

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

    public TablaHoja leer(String hoja, String pestana, List<String> obligatorias, String columnaClave, int filas)
            throws IOException, InterruptedException {
        String p = "'" + pestana.replace("'", "''") + "'!";
        List<List<JsonNode>> cabecera = valores(hoja, p + "1:1");
        Map<String, Integer> columnas = new HashMap<>();
        if (!cabecera.isEmpty()) {
            List<JsonNode> c = cabecera.get(0);
            for (int i = 0; i < c.size(); i++) {
                String nombre = TablaHoja.texto(c.get(i));
                if (nombre != null) columnas.putIfAbsent(nombre, i);
            }
        }
        for (String o : obligatorias)
            if (!columnas.containsKey(o)) throw new FormatoHojaException("Falta la columna «" + o + "» en «" + pestana + "»");
        String clave = letra(columnas.get(columnaClave));
        int ultima = valores(hoja, p + clave + ":" + clave).size();
        if (ultima <= 1) return new TablaHoja(0, columnas, List.of());
        int desde = Math.max(2, ultima - filas + 1);
        int ancho = obligatorias.stream().mapToInt(columnas::get).max().orElse(0);
        List<List<JsonNode>> cola = valores(hoja, p + "A" + desde + ":" + letra(ancho) + ultima);
        List<TablaHoja.Fila> lista = new ArrayList<>();
        for (int i = 0; i < cola.size(); i++) lista.add(new TablaHoja.Fila(desde + i, cola.get(i)));
        return new TablaHoja(ultima - 1, columnas, lista);
    }

    /** 0 → A, 25 → Z, 26 → AA. */
    static String letra(int indice) {
        StringBuilder s = new StringBuilder();
        for (int n = indice + 1; n > 0; n = (n - 1) / 26) s.insert(0, (char) ('A' + (n - 1) % 26));
        return s.toString();
    }

    private List<List<JsonNode>> valores(String hoja, String rango) throws IOException, InterruptedException {
        String url = API + URLEncoder.encode(hoja, StandardCharsets.UTF_8) + "/values/"
                + URLEncoder.encode(rango, StandardCharsets.UTF_8).replace("+", "%20") + OPCIONES;
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
            if (e.getCause() instanceof IOException io) throw io;
            throw new IOException("Fallo al pedir a Google Sheets", e.getCause());
        }
        if (respuesta.statusCode() != 200) throw new IOException("Google Sheets respondió " + respuesta.statusCode());
        List<List<JsonNode>> filas = new ArrayList<>();
        for (JsonNode fila : json.readTree(respuesta.body()).path("values")) {
            List<JsonNode> celdas = new ArrayList<>();
            fila.forEach(celdas::add);
            filas.add(celdas);
        }
        return filas;
    }
}
