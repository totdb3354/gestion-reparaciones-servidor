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
import java.util.Optional;
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

    /** Hace la llamada y devuelve el cuerpo ya comprobado (respuesta 200 y Result OK). */
    private JsonNode pedirCuerpo(String cuerpoPeticion) throws IOException, InterruptedException {
        return pedirCuerpo(cuerpoPeticion, espera);
    }

    /** Igual, con un tope de espera propio (tambien para las cabeceras de la peticion). */
    private JsonNode pedirCuerpo(String cuerpoPeticion, Duration tope) throws IOException, InterruptedException {
        String basic = Base64.getEncoder().encodeToString((clave.clave() + ":").getBytes(StandardCharsets.UTF_8));
        HttpRequest peticion = HttpRequest.newBuilder(URI.create(API)).timeout(tope.compareTo(ESPERA_CABECERAS) < 0 ? tope : ESPERA_CABECERAS)
                .header("Content-Type", "application/json").header("Authorization", "Basic " + basic)
                .header("User-Agent", AGENTE).POST(HttpRequest.BodyPublishers.ofString(cuerpoPeticion)).build();
        CompletableFuture<HttpResponse<String>> futura = http.sendAsync(peticion, HttpResponse.BodyHandlers.ofString());
        HttpResponse<String> respuesta;
        try {
            respuesta = futura.get(tope.toMillis(), TimeUnit.MILLISECONDS);
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
        return cuerpo;
    }

    /** Hace la llamada y devuelve la lista «Orders», ya comprobada (respuesta 200, Result OK, lista presente y Total coherente). */
    private JsonNode pedirLista(String cuerpoPeticion) throws IOException, InterruptedException {
        JsonNode cuerpo = pedirCuerpo(cuerpoPeticion);
        // Sin la lista no hay «cero pendientes»: sería cerrar todos los pedidos de ShippyPro en silencio.
        JsonNode lista = cuerpo.path("Orders");
        if (!lista.isArray()) throw new IOException("ShippyPro: la respuesta no trae la lista de pedidos");
        JsonNode total = cuerpo.path("Total");
        if (total.isNumber() && total.asLong() != lista.size() || total.isNumber() && !total.canConvertToLong())
            throw new IOException("ShippyPro dice Total=" + total.asText() + " pero trae " + lista.size() + " pedidos");
        return lista;
    }

    /** La URL de la etiqueta ya generada del envio (GetLabelUrl, solo lectura): «LabelURL» como texto o, a veces, como lista (la
     *  primera). Vacio si ShippyPro no trae ninguna. */
    public Optional<String> urlEtiqueta(long envioId) throws IOException, InterruptedException {
        return urlEtiqueta(envioId, espera);
    }

    /** Igual, con un tope de espera corto propio (la peticion de una persona espera; las lecturas de fondo no). */
    public Optional<String> urlEtiqueta(long envioId, Duration tope) throws IOException, InterruptedException {
        JsonNode url = pedirCuerpo("{\"Method\":\"GetLabelUrl\",\"Params\":{\"OrderID\":" + envioId + "}}", tope).path("LabelURL");
        if (url.isArray()) url = url.isEmpty() ? null : url.get(0);
        if (url == null || !url.isTextual() || url.asText().isBlank()) return Optional.empty();
        return Optional.of(url.asText().strip());
    }

    public Leidos pendientes() throws IOException, InterruptedException {
        JsonNode lista = pedirLista(CUERPO);
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

    /** Un envio ya hecho: solo lo que el ERP guarda (pedido, marketplace, fecha de creacion en hora de Madrid, transportista,
     *  seguimiento, id del envio en ShippyPro y cuando se imprimio su etiqueta, nulo si no). Nada del cliente. */
    public record EnvioShippyPro(String numero, String marketplace, LocalDateTime fecha, String transportista,
                                 String seguimiento, Long idEnvio, LocalDateTime impresaEn) {
        /** Un envio sin id ni impresion conocidos. */
        public EnvioShippyPro(String numero, String marketplace, LocalDateTime fecha, String transportista, String seguimiento) {
            this(numero, marketplace, fecha, transportista, seguimiento, null, null);
        }
    }

    /** Los pedidos enviados con fecha de envio entre {@code desde} y {@code hasta} (GetShippedOrders, solo lectura). Los que no
     *  traen numero de pedido o fecha valida se saltan. */
    public List<EnvioShippyPro> enviados(Instant desde, Instant hasta) throws IOException, InterruptedException {
        String cuerpo = "{\"Method\":\"GetShippedOrders\",\"Params\":{\"DateMin\":" + desde.getEpochSecond()
                + ",\"DateMax\":" + hasta.getEpochSecond() + "}}";
        List<EnvioShippyPro> envios = new ArrayList<>();
        for (JsonNode o : pedirLista(cuerpo)) {
            String numero = o.path("transaction_id").asText("").strip();
            LocalDateTime fecha = fechaUnix(o.path("created_at").asText(""));
            if (numero.isEmpty() || fecha == null) continue;
            envios.add(new EnvioShippyPro(numero, o.path("marketplace_id").asText("").strip(), fecha,
                    vacioANulo(o.path("carrier_name").asText("")), vacioANulo(o.path("tracking_code").asText("")),
                    idEnvio(o.path("ordine_id").asText("")), impresaEn(o, fecha)));
        }
        return envios;
    }

    /** El id del envio (entero o texto con cifras); null si no es un entero positivo. */
    private static Long idEnvio(String texto) {
        try {
            long id = new BigDecimal(texto.strip()).longValueExact();
            return id > 0 ? id : null;
        } catch (ArithmeticException | NumberFormatException e) {
            return null;
        }
    }

    /** Cuando se imprimio la etiqueta: null si {@code is_printed} no es 1; si lo es, {@code printed_at} y, sin una fecha valida,
     *  la del envio. */
    private static LocalDateTime impresaEn(JsonNode o, LocalDateTime fechaEnvio) {
        try {
            if (new BigDecimal(o.path("is_printed").asText("").strip()).compareTo(BigDecimal.ONE) != 0) return null;
        } catch (NumberFormatException e) {
            return null;
        }
        LocalDateTime impresa = fechaUnix(o.path("printed_at").asText(""));
        return impresa != null ? impresa : fechaEnvio;
    }

    /** Segundos Unix (con decimales o no) a hora de Madrid; null si el texto no es una fecha valida. */
    private static LocalDateTime fechaUnix(String texto) {
        try {
            long segundos = new BigDecimal(texto.strip()).longValue();
            return segundos <= 0 ? null : LocalDateTime.ofInstant(Instant.ofEpochSecond(segundos), ImportadorNsys.MADRID);
        } catch (DateTimeException | ArithmeticException | NumberFormatException e) {
            return null;
        }
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
