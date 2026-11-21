package com.reparaciones.servidor.pedidos;

import com.reparaciones.servidor.nsys.ImportadorNsys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

/** Calendario laboral de la empresa (spec 2026-11-18 §1): de lunes a viernes y no en {@code Calendario_festivo} (festivos de la Comunidad
 *  de Madrid). Los festivos se leen de la base como mucho una vez por hora; si la lectura falla se siguen usando los ultimos leidos. */
@Component
public class CalendarioLaboral {

    private static final Logger log = LoggerFactory.getLogger(CalendarioLaboral.class);
    static final String SQL_FESTIVOS = "SELECT FECHA FROM Calendario_festivo";

    private final JdbcTemplate jdbc;
    private final Clock clock;
    private Set<LocalDate> festivos = Set.of();
    private LocalDateTime leidosEn;

    @Autowired
    public CalendarioLaboral(JdbcTemplate jdbc) {
        this(jdbc, Clock.system(ImportadorNsys.MADRID));
    }

    CalendarioLaboral(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    public synchronized Set<LocalDate> festivos() {
        LocalDateTime ahora = LocalDateTime.now(clock);
        if (leidosEn == null || ahora.isAfter(leidosEn.plusHours(1))) {
            try {
                festivos = Set.copyOf(new HashSet<>(jdbc.query(SQL_FESTIVOS, (rs, n) -> rs.getDate("FECHA").toLocalDate())));
                leidosEn = ahora;
            } catch (RuntimeException e) {
                log.warn("Calendario laboral: no se pudieron leer los festivos: {}", e.getClass().getSimpleName());
            }
        }
        return festivos;
    }

    public static boolean esLaborable(LocalDate d, Set<LocalDate> festivos) {
        DayOfWeek dia = d.getDayOfWeek();
        return dia != DayOfWeek.SATURDAY && dia != DayOfWeek.SUNDAY && !festivos.contains(d);
    }

    /** El dia laborable numero {@code dias} despues de {@code desde} (sin contar {@code desde}). */
    public static LocalDate sumarLaborables(LocalDate desde, int dias, Set<LocalDate> festivos) {
        LocalDate d = desde;
        int faltan = dias;
        while (faltan > 0) {
            d = d.plusDays(1);
            if (esLaborable(d, festivos)) faltan--;
        }
        return d;
    }
}
