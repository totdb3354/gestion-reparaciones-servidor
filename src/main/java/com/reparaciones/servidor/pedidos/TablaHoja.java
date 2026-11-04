package com.reparaciones.servidor.pedidos;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;

/** Las últimas filas de una pestaña: {@code total}, cuántas filas de datos tiene (sin la cabecera); {@code columnas}, cabecera
 *  → posición; cada fila con su número en la hoja. Las celdas son JSON sin formato (números, textos; fechas como número de
 *  serie). */
public record TablaHoja(int total, Map<String, Integer> columnas, List<Fila> filas) {

    private static final LocalDateTime CERO = LocalDateTime.of(1899, 12, 30, 0, 0);
    private static final List<DateTimeFormatter> FORMATOS = List.of(DateTimeFormatter.ofPattern("d/M/yyyy H:mm:ss"),
            DateTimeFormatter.ofPattern("d/M/yyyy H:mm"));
    private static final DateTimeFormatter SOLO_DIA = DateTimeFormatter.ofPattern("d/M/yyyy");

    public TablaHoja {
        columnas = Map.copyOf(columnas);
        filas = List.copyOf(filas);
    }

    public record Fila(int numero, List<JsonNode> celdas) {}

    /** La celda de esa columna en esa fila; null si la columna no está o la fila no llega (Sheets corta las vacías del final). */
    public JsonNode celda(Fila f, String columna) {
        Integer i = columnas.get(columna);
        return i == null || i >= f.celdas().size() ? null : f.celdas().get(i);
    }

    /** Una celda como texto, sin espacios a los lados; los números enteros sin «.0»; null si está vacía. */
    public static String texto(JsonNode v) {
        if (v == null || v.isNull() || v.isMissingNode()) return null;
        String s = v.isNumber() && v.decimalValue().stripTrailingZeros().scale() <= 0
                ? v.decimalValue().toBigInteger().toString() : v.asText();
        s = s.strip();
        return s.isEmpty() ? null : s;
    }

    /** Una fecha como número de serie (días desde el 30/12/1899, con la hora en la parte decimal) o como texto «d/M/yyyy[ H:mm[:ss]]». */
    public static LocalDateTime fecha(JsonNode v) {
        if (v == null || v.isNull() || v.isMissingNode()) return null;
        if (v.isNumber()) return CERO.plusSeconds(Math.round(v.asDouble() * 86_400));
        String s = v.asText().strip();
        if (s.isEmpty()) return null;
        for (DateTimeFormatter f : FORMATOS) {
            try {
                return LocalDateTime.parse(s, f);
            } catch (DateTimeParseException e) {
                // otro formato
            }
        }
        try {
            return LocalDate.parse(s, SOLO_DIA).atStartOfDay();
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
