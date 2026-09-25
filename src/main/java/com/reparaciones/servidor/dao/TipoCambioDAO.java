package com.reparaciones.servidor.dao;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Date;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

@Repository
public class TipoCambioDAO {

    /** 503 con un texto que el usuario entiende (sub-proyecto 4b); antes cualquier fallo era un 500 genérico. */
    static final String MSG_SIN_TASA = "No se pudo obtener el tipo de cambio de %s. Inténtalo de nuevo.";

    private final JdbcTemplate jdbc;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    @Autowired
    public TipoCambioDAO(JdbcTemplate jdbc) {
        // followRedirects: Frankfurter redirige HTTP→HTTPS; sin esto la petición falla con respuesta vacía
        this(jdbc, HttpClient.newBuilder().followRedirects(HttpClient.Redirect.ALWAYS).build());
    }

    /** Paquete: para que los tests sustituyan la red por un HttpClient mockeado. */
    TipoCambioDAO(JdbcTemplate jdbc, HttpClient httpClient) {
        this.jdbc = jdbc;
        this.httpClient = httpClient;
        this.objectMapper = new ObjectMapper();
    }

    public double getTasa(String divisa) {
        if ("EUR".equalsIgnoreCase(divisa)) return 1.0;

        LocalDate today = LocalDate.now();
        List<Double> cached = jdbc.query(
                "SELECT TASA FROM TipoCambio WHERE DIVISA = ? AND FECHA = ?",
                (rs, row) -> rs.getDouble("TASA"),
                divisa.toUpperCase(), Date.valueOf(today));

        if (!cached.isEmpty()) return cached.get(0);

        double tasa = fetchFromFrankfurter(divisa.toUpperCase());

        jdbc.update(
                "INSERT INTO TipoCambio (DIVISA, FECHA, TASA) VALUES (?, ?, ?)" +
                " ON DUPLICATE KEY UPDATE TASA = ?",
                divisa.toUpperCase(), Date.valueOf(today), tasa, tasa);

        return tasa;
    }

    private double fetchFromFrankfurter(String divisa) {
        try {
            String url = "https://api.frankfurter.app/latest?from=EUR&to=" + divisa;
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .header("User-Agent", "gestion-reparaciones/1.0") // algunas APIs rechazan peticiones sin User-Agent
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            String body = response.body();

            // Validaciones explícitas: el detalle queda en la causa del 503 para los logs del servidor
            if (body == null || body.isEmpty()) {
                throw new IllegalStateException("Frankfurter respuesta vacia (status " + response.statusCode() + ")");
            }

            JsonNode root = objectMapper.readTree(body);
            JsonNode rates = root.get("rates");
            if (rates == null) {
                throw new IllegalStateException("No hay 'rates' en respuesta: " + body);
            }
            JsonNode value = rates.get(divisa);
            if (value == null) {
                throw new IllegalStateException("Divisa " + divisa + " no encontrada: " + body);
            }
            return value.asDouble();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw sinTasa(divisa, e);
        } catch (Exception e) {
            throw sinTasa(divisa, e);
        }
    }

    private static ResponseStatusException sinTasa(String divisa, Throwable causa) {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, String.format(MSG_SIN_TASA, divisa), causa);
    }
}
