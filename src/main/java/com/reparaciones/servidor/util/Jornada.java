package com.reparaciones.servidor.util;

import java.sql.Timestamp;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Map;

/**
 * Horario del taller (spec 2026-09-08-estadisticas-horario-no-promedia §2): decide si un cierre
 * cayó "en jornada" (cuenta en las medias de Estadísticas) o "fuera de horario" (suma en los
 * totales, no promedia). Solo importan entrada y salida: la media hora de comida de L–J no tiene
 * hora fija y no se descuenta. El fin de semana es el caso extremo: jornada de 0 h.
 * <p>Futuro apuntado (como los topes de la carga de capacidad): mover a tabla configurable.</p>
 */
public final class Jornada {

    private Jornada() {}

    /** La BD y el contenedor van en UTC; la comparación se hace siempre en hora de Madrid. */
    public static final ZoneId MADRID = ZoneId.of("Europe/Madrid");

    /** Entrada de todos los días laborables (dato del usuario, 2026-09-08). */
    public static final LocalTime ENTRADA = LocalTime.of(8, 30);

    /**
     * Salida por día de semana (dato del usuario, 2026-09-08). Sábado y domingo ausentes = sin
     * jornada. Las horas resultantes (9 / 9 / 8 / 8 / 6, con media hora de comida de L a J) son
     * las mismas {@code JORNADA_HORAS} de la carga de capacidad del cliente ({@code CargaTecnicos}).
     */
    public static final Map<DayOfWeek, LocalTime> SALIDA = Map.of(
            DayOfWeek.MONDAY,    LocalTime.of(18, 0),
            DayOfWeek.TUESDAY,   LocalTime.of(18, 0),
            DayOfWeek.WEDNESDAY, LocalTime.of(17, 0),
            DayOfWeek.THURSDAY,  LocalTime.of(17, 0),
            DayOfWeek.FRIDAY,    LocalTime.of(14, 30));

    /** Margen ANTES de la entrada (decisión del usuario 2026-09-08): la franja arranca a las 8:00
     *  en punto — quien llega pronto y cierra algo antes de las 8:30 no está haciendo horas extra. */
    public static final Duration MARGEN_ENTRADA = Duration.ofMinutes(30);

    /** Margen DESPUÉS de la salida (decisión del usuario 2026-09-08): cerrar el último móvil a las
     *  18:03 no es hora extra. Franja efectiva 8:00–18:15 (L-M) / 8:00–17:15 (X-J) / 8:00–14:45 (V). */
    public static final Duration MARGEN_SALIDA = Duration.ofMinutes(15);

    /** Cierre en hora de Madrid a partir del Timestamp UTC de la BD (la JVM del contenedor va en UTC). */
    public static ZonedDateTime aMadrid(Timestamp utc) {
        return utc.toInstant().atZone(MADRID);
    }

    /** true si el cierre cae en [ENTRADA − MARGEN_ENTRADA, SALIDA + MARGEN_SALIDA] (extremos incluidos) de un día con jornada. */
    public static boolean enJornada(ZonedDateTime cierreMadrid) {
        LocalTime salida = SALIDA.get(cierreMadrid.getDayOfWeek());
        if (salida == null) return false;
        LocalTime hora = cierreMadrid.toLocalTime();
        return !hora.isBefore(ENTRADA.minus(MARGEN_ENTRADA)) && !hora.isAfter(salida.plus(MARGEN_SALIDA));
    }
}
