package com.reparaciones.servidor.service;

import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CargaTecnicosZonaTest {

    /** Sábado 00:30 en Madrid es viernes 22:30 en UTC: si el día se resolviera en UTC,
     *  el fin de semana se desplazaría y la carga saldría con jornada donde no la hay. */
    @Test
    void elDiaSeResuelveEnMadridNoEnUtc() {
        Instant sabadoDeMadrugadaEnMadrid = Instant.parse("2026-09-25T22:30:00Z");
        DayOfWeek enMadrid = sabadoDeMadrugadaEnMadrid.atZone(ZoneId.of("Europe/Madrid")).getDayOfWeek();
        DayOfWeek enUtc = sabadoDeMadrugadaEnMadrid.atZone(ZoneId.of("UTC")).getDayOfWeek();

        assertEquals(DayOfWeek.SATURDAY, enMadrid);
        assertEquals(DayOfWeek.FRIDAY, enUtc);
        assertEquals(0, CargaTecnicos.JORNADA_HORAS.get(enMadrid));
        assertEquals(6, CargaTecnicos.JORNADA_HORAS.get(enUtc));
    }

    /** El helper que usa el controlador debe devolver el día de Madrid. */
    @Test
    void diaDeHoyUsaMadrid() {
        assertEquals(java.time.LocalDate.now(ZoneId.of("Europe/Madrid")).getDayOfWeek(),
                     CargaTecnicos.diaDeHoy());
    }
}
