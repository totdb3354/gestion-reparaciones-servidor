package com.reparaciones.servidor.pedidos;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.reparaciones.servidor.nsys.ImportadorNsys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import java.time.DateTimeException;
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

    private static final Logger log = LoggerFactory.getLogger(ClienteShippyPro.class);
    static final String API = "https://www.shippypro.com/api";
    static final String AGENTE = "FSGR-ERP (lectura de pedidos)";
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

    /** Lo leído: los pedidos válidos y el número de cada pedido saltado por no tener fecha válida. */
    public record Leidos(List<PedidoShippyPro> pedidos, List<String> descartados) {
        public Leidos {
            pedidos = List.copyOf(pedidos);
            descartados = List.copyOf(descartados);
        }
    }

    public Leidos pendientes() throws IOException, InterruptedException {
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
        } catch (InterruptedException e) {
            futura.cancel(true);
            throw e;
        } catch (ExecutionException e) {
            if (e.getCause() instanceof IOException io) throw io;
            throw new IOException("Fallo al pedir a ShippyPro", e.getCause());
        }
        if (respuesta.statusCode() != 200) throw new IOException("ShippyPro respondió " + respuesta.statusCode());
        JsonNode cuerpo = json.readTree(respuesta.body());
        String resultado = cuerpo.path("Result").asText("");
        if (!"OK".equals(resultado))
            throw new IOException(("ShippyPro: " + resultado + " " + cuerpo.path("Error").asText("")).strip());
        // Sin la lista no hay «cero pendientes»: sería cerrar todos los pedidos de ShippyPro en silencio.
        JsonNode lista = cuerpo.path("Orders");
        if (!lista.isArray()) throw new IOException("ShippyPro: la respuesta no trae la lista de pedidos");
        JsonNode total = cuerpo.path("Total");
        if (total.isNumber() && total.asLong() != lista.size() || total.isNumber() && !total.canConvertToLong())
            throw new IOException("ShippyPro dice Total=" + total.asText() + " pero trae " + lista.size() + " pedidos");
        List<PedidoShippyPro> pedidos = new ArrayList<>();
        List<String> descartados = new ArrayList<>();
        for (JsonNode o : lista) {
            try {
                pedidos.add(pedido(o));
            } catch (IllegalArgumentException e) {
                String numero = o.path("transaction_id").asText("").strip();
                if (numero.isEmpty()) numero = "(sin número)";
                descartados.add(numero);
                log.warn("Pedidos: el pedido {} de ShippyPro no tiene una fecha válida y se salta", numero);
            }
        }
        return new Leidos(pedidos, descartados);
    }

    /** Un pedido de ShippyPro a nuestro modelo. Lanza IllegalArgumentException si el pedido no trae una fecha válida
     *  (vacía, ausente, no numérica o no positiva); acepta decimales como «1793629400.0». */
    static PedidoShippyPro pedido(JsonNode o) {
        String texto = o.path("date").asText("").strip();
        if (texto.isEmpty()) throw new IllegalArgumentException("sin fecha");
        LocalDateTime fecha;
        try {
            long segundos = new BigDecimal(texto).longValue();
            if (segundos <= 0) throw new IllegalArgumentException("fecha no válida");
            fecha = LocalDateTime.ofInstant(Instant.ofEpochSecond(segundos), ImportadorNsys.MADRID);
        } catch (DateTimeException | ArithmeticException e) {
            throw new IllegalArgumentException("fecha no válida", e);
        }
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
