package com.reparaciones.servidor.nsys;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/** Aviso de datos antiguos (spec pieza 1 §5.7). Solo cuentan los minutos de 07:00 a 23:00, cuando el bot exporta: más
 *  de 60 es RETRASO y más de 180, SIN_DATOS. */
public final class AvisoNsys {

    static final LocalTime ABRE = LocalTime.of(7, 0);
    static final LocalTime CIERRA = LocalTime.of(23, 0);
    static final long RETRASO = 60;
    static final long SIN_DATOS = 180;

    private AvisoNsys() {}

    public static String nivel(LocalDateTime ultima, LocalDateTime ahora) {
        if (ultima == null) return "SIN_DATOS";
        long minutos = minutosHabiles(ultima, ahora);
        return minutos > SIN_DATOS ? "SIN_DATOS" : minutos > RETRASO ? "RETRASO" : "OK";
    }

    static long minutosHabiles(LocalDateTime desde, LocalDateTime hasta) {
        long total = 0;
        for (LocalDate dia = desde.toLocalDate(); !dia.isAfter(hasta.toLocalDate()); dia = dia.plusDays(1)) {
            LocalDateTime inicio = mayor(desde, dia.atTime(ABRE));
            LocalDateTime fin = menor(hasta, dia.atTime(CIERRA));
            if (fin.isAfter(inicio)) total += Duration.between(inicio, fin).toMinutes();
        }
        return total;
    }

    private static LocalDateTime mayor(LocalDateTime a, LocalDateTime b) {
        return a.isAfter(b) ? a : b;
    }

    private static LocalDateTime menor(LocalDateTime a, LocalDateTime b) {
        return a.isBefore(b) ? a : b;
    }
}
