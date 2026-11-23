package com.reparaciones.servidor.nsys;

import java.time.Duration;
import java.time.LocalDateTime;

/** Aviso de datos antiguos (spec pieza 1 §5.7). Desde la 0.9.43 cuentan todos los minutos, también de noche: NSYS publica un
 *  Items cada 10 min las 24 h (desde el 23/11/2026; antes, cada 30 min de 07:00 a 23:00, y solo contaba ese horario). Lo
 *  normal es tener el último Items de hace 10-20 min (el ERP lo importa cada 5 min, con 2 de espera); un ciclo saltado llega a
 *  unos 30. Más de 40 es RETRASO y más de 60, SIN_DATOS. Pedidos usa la misma regla para ShippyPro, que se lee cada 5 min. */
public final class AvisoNsys {

    static final long RETRASO = 40;
    static final long SIN_DATOS = 60;

    private AvisoNsys() {}

    public static String nivel(LocalDateTime ultima, LocalDateTime ahora) {
        if (ultima == null) return "SIN_DATOS";
        long minutos = minutos(ultima, ahora);
        return minutos > SIN_DATOS ? "SIN_DATOS" : minutos > RETRASO ? "RETRASO" : "OK";
    }

    /** Minutos de {@code desde} a {@code hasta}; 0 si {@code desde} es posterior. */
    static long minutos(LocalDateTime desde, LocalDateTime hasta) {
        return Math.max(0, Duration.between(desde, hasta).toMinutes());
    }
}
