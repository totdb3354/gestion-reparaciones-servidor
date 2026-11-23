package com.reparaciones.servidor.nsys;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Aviso de datos antiguos (spec pieza 1 §5.7; desde la 0.9.43, con el ciclo de NSYS cada 10 min las 24 h): cuentan todos los
 *  minutos, también de noche, y más de 40 es RETRASO y más de 60, SIN_DATOS. */
class AvisoNsysTest {

    private static LocalDateTime t(int dia, int hora, int minuto) {
        return LocalDateTime.of(2026, 11, dia, hora, minuto);
    }

    @Test void alDia() {
        assertEquals("OK", AvisoNsys.nivel(t(23, 13, 16), t(23, 13, 36)));
    }

    @Test void unItemsQueNoLlegaTodaviaNoEsRetraso() {
        // Se salta un ciclo: el siguiente Items (13:26) entra hacia las 13:46. Hasta ahí, 30 min: aún al día.
        assertEquals("OK", AvisoNsys.nivel(t(23, 13, 16), t(23, 13, 56)));
    }

    @Test void retrasoPasados40Minutos() {
        assertEquals("RETRASO", AvisoNsys.nivel(t(23, 13, 16), t(23, 13, 57)));
        assertEquals("RETRASO", AvisoNsys.nivel(t(23, 13, 16), t(23, 14, 16)));
    }

    @Test void sinDatosPasada1HoraOSinImportaciones() {
        assertEquals("SIN_DATOS", AvisoNsys.nivel(t(23, 13, 16), t(23, 14, 17)));
        assertEquals("SIN_DATOS", AvisoNsys.nivel(null, t(23, 13, 43)));
    }

    @Test void laNocheTambienCuenta() {
        assertEquals(50, AvisoNsys.minutos(t(23, 23, 30), t(24, 0, 20)));
        assertEquals("RETRASO", AvisoNsys.nivel(t(23, 23, 30), t(24, 0, 20)));
        assertEquals("SIN_DATOS", AvisoNsys.nivel(t(23, 2, 6), t(23, 7, 0)));
    }

    @Test void unaHoraEnElFuturoNoDaMinutosNegativos() {
        assertEquals(0, AvisoNsys.minutos(t(23, 13, 0), t(23, 12, 0)));
        assertEquals("OK", AvisoNsys.nivel(t(23, 13, 0), t(23, 12, 0)));
    }
}
