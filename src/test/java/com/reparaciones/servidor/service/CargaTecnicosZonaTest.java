package com.reparaciones.servidor.service;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CargaTecnicosZonaTest {

    /** Viernes 22:30 UTC es sábado 00:30 en Madrid: si {@code diaDeHoy(Clock)} resolviera el día
     *  en la zona propia del reloj (o en UTC) en vez de forzar Europe/Madrid, este instante daría
     *  FRIDAY en vez de SATURDAY — justo el cruce de medianoche que desplazaría el fin de semana y
     *  la jornada (spec 2026-07-09-carga-capacidad-diaria). El reloj se fija a un instante y una
     *  zona (UTC) explícitos con {@link Clock#fixed}, así que el resultado esperado no depende del
     *  reloj real ni de la zona por defecto de la máquina que ejecute el test: si alguien quita el
     *  {@code withZone(Europe/Madrid)} de la implementación, este test falla en cualquier runner,
     *  no solo en uno que ya corra en UTC. */
    @Test
    void diaDeHoySiempreInterpretaElInstanteEnMadrid() {
        Clock relojFijoEnUtc = Clock.fixed(Instant.parse("2026-09-25T22:30:00Z"), ZoneId.of("UTC"));

        assertEquals(DayOfWeek.SATURDAY, CargaTecnicos.diaDeHoy(relojFijoEnUtc));
    }
}
