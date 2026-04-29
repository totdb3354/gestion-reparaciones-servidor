package com.reparaciones.servidor.dao;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

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

    private final JdbcTemplate jdbc;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public TipoCambioDAO(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        this.httpClient = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.ALWAYS).build();
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
                    .header("User-Agent", "gestion-reparaciones/1.0")
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            String body = response.body();

            if (body == null || body.isEmpty()) {
                throw new RuntimeException("Frankfurter respuesta vacia (status " + response.statusCode() + ")");
            }

            JsonNode root = objectMapper.readTree(body);
            JsonNode rates = root.get("rates");
            if (rates == null) {
                throw new RuntimeException("No hay 'rates' en respuesta: " + body);
            }
            JsonNode value = rates.get(divisa);
            if (value == null) {
                throw new RuntimeException("Divisa " + divisa + " no encontrada: " + body);
            }
            return value.asDouble();
        } catch (Exception e) {
            throw new RuntimeException("Error Frankfurter API: " + e.getMessage(), e);
        }
    }
}