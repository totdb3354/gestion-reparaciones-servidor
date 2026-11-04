package com.reparaciones.servidor.pedidos;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.reparaciones.servidor.nsys.ImportadorNsys;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Los pedidos pendientes de ShippyPro (spec pieza 2 §4.1): POST JSON con «Method»/«Params» y la clave por Basic (la clave como
 *  usuario, sin contraseña), como `devoluciones` de Automatización. Sin un User-Agent propio ShippyPro responde 403. */
@Component
public class ClienteShippyPro {

    static final String API = "https://www.shippypro.com/api";
    static final String AGENTE = "FSGR-ERP/0.9.26 (lectura de pedidos)";
    private static final Duration ESPERA_CABECERAS = Duration.ofSeconds(60);
    static final Duration ESPERA = Duration.ofMinutes(2);
    private static final String CUERPO = "{\"Method\":\"GetPendingOrders\",\"Params\":{}}";

    /** Da la clave de ShippyPro; los tests la sustituyen por una fija. */
    interface ProveedorClave {
        String clave() throws IOException;
    }

    private final HttpClient http;
    private final ProveedorClave clave;
    private final Duration espera;
    private final ObjectMapper json = new ObjectMapper();

    @Autowired
    public ClienteShippyPro(@Value("${pedidos.shippypro.clave:}") String ruta) {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(), deFichero(ruta), ESPERA);
    }

    ClienteShippyPro(HttpClient http, ProveedorClave clave, Duration espera) {
        this.http = http;
        this.clave = clave;
        this.espera = espera;
    }

    /** La clave se lee la primera vez que hace falta: el contexto arranca sin el fichero (tests y lectura apagada). */
    static ProveedorClave deFichero(String ruta) {
        return new ProveedorClave() {
            private String leida;

            @Override public synchronized String clave() throws IOException {
                if (leida == null) {
                    if (ruta == null || ruta.isBlank()) throw new IOException("Falta el ajuste pedidos.shippypro.clave");
                    leida = Files.readString(Path.of(ruta), StandardCharsets.UTF_8).strip();
                }
                return leida;
            }
        };
    }

    public List<PedidoShippyPro> pendientes() throws IOException, InterruptedException {
        String basic = Base64.getEncoder().encodeToString((clave.clave() + ":").getBytes(StandardCharsets.UTF_8));
        HttpRequest peticion = HttpRequest.newBuilder(URI.create(API)).timeout(ESPERA_CABECERAS)
                .header("Content-Type", "application/json").header("Authorization", "Basic " + basic)
                .header("User-Agent", AGENTE).POST(HttpRequest.BodyPublishers.ofString(CUERPO)).build();
        CompletableFuture<HttpResponse<String>> futura = http.sendAsync(peticion, HttpResponse.BodyHandlers.ofString());
        HttpResponse<String> respuesta;
        try {
            respuesta = futura.get(espera.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            futura.cancel(true);
            throw new IOException("ShippyPro no respondió a tiempo");
        } catch (ExecutionException e) {
            if (e.getCause() instanceof IOException io) throw io;
            throw new IOException("Fallo al pedir a ShippyPro", e.getCause());
        }
        if (respuesta.statusCode() != 200) throw new IOException("ShippyPro respondió " + respuesta.statusCode());
        JsonNode cuerpo = json.readTree(respuesta.body());
        String resultado = cuerpo.path("Result").asText("");
        if (!"OK".equals(resultado))
            throw new IOException(("ShippyPro: " + resultado + " " + cuerpo.path("Error").asText("")).strip());
        List<PedidoShippyPro> pedidos = new ArrayList<>();
        for (JsonNode o : cuerpo.path("Orders")) pedidos.add(pedido(o));
        return pedidos;
    }

    static PedidoShippyPro pedido(JsonNode o) {
        LocalDateTime fecha = LocalDateTime.ofInstant(Instant.ofEpochSecond(Long.parseLong(o.path("date").asText("0").strip())),
                ImportadorNsys.MADRID);
        List<PedidoShippyPro.Articulo> articulos = new ArrayList<>();
        for (JsonNode i : o.path("items")) {
            String sku = i.path("sku").asText("").strip();
            int cantidad = cantidad(i.path("quantity").asText(""));
            if (!sku.isEmpty() && cantidad > 0) articulos.add(new PedidoShippyPro.Articulo(sku, cantidad));
        }
        return new PedidoShippyPro(o.path("transaction_id").asText("").strip(), o.path("marketplace_id").asText("").strip(),
                fecha, vacioANulo(o.path("name").asText("")), vacioANulo(o.path("country").asText("")), articulos);
    }

    private static int cantidad(String texto) {
        try {
            return new BigDecimal(texto.strip()).intValue();
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    private static String vacioANulo(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }
}
