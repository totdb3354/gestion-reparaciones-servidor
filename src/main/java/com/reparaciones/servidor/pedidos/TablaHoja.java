package com.reparaciones.servidor.pedidos;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;

/** Las filas de datos de una pestaña (sin la cabecera): {@code columnas}, cabecera → posición; cada fila con su número en la hoja.
 *  Las celdas son JSON sin formato (números, textos; fechas como número de serie). */
public record TablaHoja(Map<String, Integer> columnas, List<Fila> filas) {

    private static final LocalDateTime CERO = LocalDateTime.of(1899, 12, 30, 0, 0);
    /** Una fecha fuera de estos años es un número cualquiera, no una fecha de la hoja. */
    static final int PRIMER_ANIO = 2000;
    static final int ULTIMO_ANIO = 2100;
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

    /** Una fecha como número de serie (días desde el 30/12/1899, con la hora en la parte decimal) o como texto
     *  «d/M/yyyy[ H:mm[:ss]]». Null si está vacía, no se entiende o cae fuera de 2000-2100 (un número imposible no es una fecha). */
    public static LocalDateTime fecha(JsonNode v) {
        LocalDateTime f = fechaSinLimite(v);
        return f == null || f.getYear() < PRIMER_ANIO || f.getYear() > ULTIMO_ANIO ? null : f;
    }

    private static LocalDateTime fechaSinLimite(JsonNode v) {
        if (v == null || v.isNull() || v.isMissingNode()) return null;
        if (v.isNumber()) {
            double d = v.asDouble();
            if (Double.isNaN(d) || Double.isInfinite(d) || d < 0 || d > 200_000) return null;
            try {
                return CERO.plusSeconds(Math.round(d * 86_400));
            } catch (DateTimeException | ArithmeticException e) {
                return null;
            }
        }
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
