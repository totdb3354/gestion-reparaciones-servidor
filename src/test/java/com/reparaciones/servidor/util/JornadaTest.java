package com.reparaciones.servidor.util;

import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.time.*;

import static org.junit.jupiter.api.Assertions.*;

/** Horario del taller con margen de 15 min (spec 2026-09-08 §2). Fechas: 2026-08-31 lunes,
 *  2026-09-02 miércoles, 2026-08-28 viernes, 2026-09-05 sábado, 2026-01-12 lunes. */
class JornadaTest {

    private static ZonedDateTime madrid(String isoLocal) {
        return LocalDateTime.parse(isoLocal).atZone(Jornada.MADRID);
    }

    @Test void lunesEntraALasOchoYMediaYSaleALasSeisConMargen() {
        assertFalse(Jornada.enJornada(madrid("2026-08-31T08:14:59")));
        assertTrue (Jornada.enJornada(madrid("2026-08-31T08:15:00")));
        assertTrue (Jornada.enJornada(madrid("2026-08-31T13:00:00")));   // la comida no descuenta
        assertTrue (Jornada.enJornada(madrid("2026-08-31T18:15:00")));
        assertFalse(Jornada.enJornada(madrid("2026-08-31T18:15:01")));
    }

    @Test void miercolesSaleALasCinco() {
        assertTrue (Jornada.enJornada(madrid("2026-09-02T17:15:00")));
        assertFalse(Jornada.enJornada(madrid("2026-09-02T17:16:00")));
    }

    @Test void viernesSaleADosYMedia() {
        assertTrue (Jornada.enJornada(madrid("2026-08-28T08:30:00")));
        assertTrue (Jornada.enJornada(madrid("2026-08-28T14:45:00")));
        assertFalse(Jornada.enJornada(madrid("2026-08-28T14:46:00")));
        assertFalse(Jornada.enJornada(madrid("2026-08-28T22:30:00")));
    }

    @Test void finDeSemanaSiempreFuera() {
        assertFalse(Jornada.enJornada(madrid("2026-09-05T10:00:00")));   // sábado
        assertFalse(Jornada.enJornada(madrid("2026-09-06T12:00:00")));   // domingo
    }

    @Test void aMadridConvierteElTimestampUtcDeLaBd() {
        // viernes 28/08 20:30Z = 22:30 Madrid (verano): mismo día, fuera de horario
        ZonedDateTime z = Jornada.aMadrid(Timestamp.from(Instant.parse("2026-08-28T20:30:00Z")));
        assertEquals(LocalDate.of(2026, 8, 28), z.toLocalDate());
        assertEquals(LocalTime.of(22, 30), z.toLocalTime());
        assertFalse(Jornada.enJornada(z));
        // 28/08 22:30Z = sábado 29/08 00:30 Madrid: la fecha del cierre es la de Madrid
        ZonedDateTime s = Jornada.aMadrid(Timestamp.from(Instant.parse("2026-08-28T22:30:00Z")));
        assertEquals(LocalDate.of(2026, 8, 29), s.toLocalDate());
        assertFalse(Jornada.enJornada(s));
    }

    @Test void enInviernoTambienSeComparaEnHoraLocal() {
        // lunes 12/01 17:00Z = 18:00 Madrid (invierno, UTC+1): en jornada
        assertTrue(Jornada.enJornada(Jornada.aMadrid(Timestamp.from(Instant.parse("2026-01-12T17:00:00Z")))));
    }
}
