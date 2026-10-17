package com.reparaciones.servidor.nsys;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Aviso de datos antiguos (spec pieza 1 §5.7): solo cuentan los minutos de 07:00 a 23:00. */
class AvisoNsysTest {

    private static LocalDateTime t(int dia, int hora, int minuto) {
        return LocalDateTime.of(2026, 10, dia, hora, minuto);
    }

    @Test void alDia() {
        assertEquals("OK", AvisoNsys.nivel(t(16, 13, 31), t(16, 13, 43)));
    }

    @Test void retrasoPasadaLaHora() {
        assertEquals("RETRASO", AvisoNsys.nivel(t(16, 13, 31), t(16, 15, 0)));
    }

    @Test void sinDatosPasadasTresHorasHabilesOSinImportaciones() {
        assertEquals("SIN_DATOS", AvisoNsys.nivel(t(16, 8, 31), t(16, 13, 43)));
        assertEquals("SIN_DATOS", AvisoNsys.nivel(null, t(16, 13, 43)));
    }

    @Test void laNocheNoCuenta() {
        assertEquals(50, AvisoNsys.minutosHabiles(t(15, 22, 30), t(16, 7, 20)));
        assertEquals("OK", AvisoNsys.nivel(t(15, 22, 30), t(16, 7, 20)));
        assertEquals(0, AvisoNsys.minutosHabiles(t(16, 13, 0), t(16, 12, 0)));
    }
}
