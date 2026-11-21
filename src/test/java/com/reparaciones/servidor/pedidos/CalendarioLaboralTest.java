package com.reparaciones.servidor.pedidos;

import com.reparaciones.servidor.nsys.ImportadorNsys;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.Date;
import java.sql.ResultSet;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CalendarioLaboralTest {
    private static final Set<LocalDate> FESTIVOS = Set.of(LocalDate.of(2026, 12, 7), LocalDate.of(2026, 12, 8), LocalDate.of(2026, 12, 25));

    @Test
    void finesDeSemanaYFestivosNoSonLaborables() {
        assertThat(CalendarioLaboral.esLaborable(LocalDate.of(2026, 11, 18), FESTIVOS)).isTrue();  // miercoles
        assertThat(CalendarioLaboral.esLaborable(LocalDate.of(2026, 11, 21), FESTIVOS)).isFalse(); // sabado
        assertThat(CalendarioLaboral.esLaborable(LocalDate.of(2026, 11, 22), FESTIVOS)).isFalse(); // domingo
        assertThat(CalendarioLaboral.esLaborable(LocalDate.of(2026, 12, 8), FESTIVOS)).isFalse();  // festivo
    }

    @Test
    void sumarLaborablesNoCuentaElDiaDePartidaNiLosFinesDeSemana() {
        assertThat(CalendarioLaboral.sumarLaborables(LocalDate.of(2026, 11, 16), 2, FESTIVOS)).isEqualTo(LocalDate.of(2026, 11, 18)); // lunes -> miercoles
        assertThat(CalendarioLaboral.sumarLaborables(LocalDate.of(2026, 11, 20), 2, FESTIVOS)).isEqualTo(LocalDate.of(2026, 11, 24)); // viernes -> martes
        assertThat(CalendarioLaboral.sumarLaborables(LocalDate.of(2026, 11, 21), 2, FESTIVOS)).isEqualTo(LocalDate.of(2026, 11, 24)); // sabado -> martes
        assertThat(CalendarioLaboral.sumarLaborables(LocalDate.of(2026, 11, 20), 3, FESTIVOS)).isEqualTo(LocalDate.of(2026, 11, 25)); // viernes -> miercoles
    }

    @Test
    void sumarLaborablesSaltaLosFestivos() {
        // viernes 4/12 + 2: lunes 7 y martes 8 son festivos -> miercoles 9 y jueves 10
        assertThat(CalendarioLaboral.sumarLaborables(LocalDate.of(2026, 12, 4), 2, FESTIVOS)).isEqualTo(LocalDate.of(2026, 12, 10));
    }

    /** Un reloj que se puede adelantar. */
    private static final class RelojMovil extends Clock {
        Instant ahora = Instant.parse("2026-11-18T10:00:00Z");
        @Override public java.time.ZoneId getZone() { return ImportadorNsys.MADRID; }
        @Override public Clock withZone(java.time.ZoneId z) { return this; }
        @Override public Instant instant() { return ahora; }
    }

    @SuppressWarnings("unchecked")
    private static void siLeeFechas(JdbcTemplate jdbc, LocalDate... fechas) throws Exception {
        when(jdbc.query(anyString(), any(RowMapper.class))).thenAnswer(inv -> {
            RowMapper<LocalDate> m = inv.getArgument(1);
            List<LocalDate> res = new java.util.ArrayList<>();
            for (LocalDate f : fechas) {
                ResultSet rs = mock(ResultSet.class);
                when(rs.getDate("FECHA")).thenReturn(Date.valueOf(f));
                res.add(m.mapRow(rs, 0));
            }
            return res;
        });
    }

    @Test
    @SuppressWarnings("unchecked")
    void festivosSeLeenUnaVezPorHoraYSiFallaSeUsanLosUltimos() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        siLeeFechas(jdbc, LocalDate.of(2026, 12, 8));
        RelojMovil reloj = new RelojMovil();
        CalendarioLaboral cal = new CalendarioLaboral(jdbc, reloj);

        assertThat(cal.festivos()).containsExactly(LocalDate.of(2026, 12, 8));
        assertThat(cal.festivos()).containsExactly(LocalDate.of(2026, 12, 8));
        verify(jdbc, times(1)).query(anyString(), any(RowMapper.class));

        reloj.ahora = reloj.ahora.plus(Duration.ofMinutes(61));
        when(jdbc.query(anyString(), any(RowMapper.class))).thenThrow(new RuntimeException("caida"));
        assertThat(cal.festivos()).containsExactly(LocalDate.of(2026, 12, 8));
        verify(jdbc, times(2)).query(anyString(), any(RowMapper.class));
    }
}
