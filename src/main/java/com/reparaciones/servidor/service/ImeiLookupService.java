package com.reparaciones.servidor.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ImeiLookupService {

    private static final Logger log = LoggerFactory.getLogger(ImeiLookupService.class);

    private static final String URL_BASE =
            "https://alpha.imeicheck.com/api/free_with_key/modelBrandName";

    // Sincronizado con FormularioReparacionController.MODELOS_ORDENADOS en el cliente.
    // Al añadir modelos nuevos, actualizar en AMBOS sitios.
    private static final List<String> MODELOS_ORDENADOS = List.of(
            "6s", "6splus", "7", "7plus", "8", "8plus", "se2020",
            "x", "xr", "xs", "xsmax",
            "11", "11pro", "11promax",
            "12", "12mini", "12pro", "12promax",
            "13", "13mini", "13pro", "13promax",
            "14", "14plus", "14pro", "14promax",
            "15", "15plus", "15pro", "15promax",
            "16", "16e", "16plus", "16pro", "16promax"
    );

    private final String apiKey;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final Map<String, String> mapaComercialInterno;

    public ImeiLookupService(@Value("${imeicheck.api-key:}") String apiKey) {
        this.apiKey = apiKey;
        this.httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .build();
        this.objectMapper = new ObjectMapper();
        this.mapaComercialInterno = construirMapa();
    }

    public String lookupModeloInterno(String imei) {
        if (apiKey == null || apiKey.isBlank()) return null;
        try {
            String url = URL_BASE + "?key=" + apiKey + "&imei=" + imei + "&format=json";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(3))
                    .header("Accept", "application/json")
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.warn("IMEI lookup status {} para {}", response.statusCode(), imei);
                return null;
            }
            JsonNode root = objectMapper.readTree(response.body());
            JsonNode obj = root.get("object");
            if (obj == null) return null;
            JsonNode brand = obj.get("brand");
            if (brand == null || !brand.asText().equalsIgnoreCase("Apple")) return null;
            JsonNode name = obj.get("name");
            if (name == null) return null;
            return comercialACodigoInterno(name.asText());
        } catch (Exception e) {
            log.warn("IMEI lookup fallido para {}: {}", imei, e.getMessage());
            return null;
        }
    }

    String comercialACodigoInterno(String nombreComercial) {
        return mapaComercialInterno.get(normalizar(nombreComercial));
    }

    private Map<String, String> construirMapa() {
        Map<String, String> mapa = new HashMap<>();
        for (String codigo : MODELOS_ORDENADOS) {
            mapa.put(normalizar(traducirModelo(codigo)), codigo);
        }
        return mapa;
    }

    static String normalizar(String s) {
        return s.toLowerCase()
                .replace("apple", "")
                .replace("iphone", "")
                .replaceAll("\\s+", "");
    }

    static String traducirModelo(String modelo) {
        return switch (modelo) {
            case "se2020" -> "iPhone SE 2020";
            case "x"      -> "iPhone X";
            case "xr"     -> "iPhone XR";
            case "xs"     -> "iPhone XS";
            case "xsmax"  -> "iPhone XS Max";
            case "6s"     -> "iPhone 6S";
            case "6splus" -> "iPhone 6S Plus";
            default -> {
                String num      = modelo.replaceAll("[^0-9]", "");
                String variante = modelo.replaceAll("[0-9]", "");
                String sufijo   = switch (variante) {
                    case "plus"   -> " Plus";
                    case "mini"   -> " Mini";
                    case "pro"    -> " Pro";
                    case "promax" -> " Pro Max";
                    case "e"      -> "e";
                    default       -> "";
                };
                yield "iPhone " + num + sufijo;
            }
        };
    }
}
