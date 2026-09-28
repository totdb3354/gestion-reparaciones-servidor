package com.reparaciones.servidor.security;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Ritmo máximo del lookup externo de IMEI, por usuario (spec sp7b §4.4). */
class FrenoLookupTest {

    private final AtomicLong ahora = new AtomicLong(1_000_000L);
    private final FrenoLookup freno = new FrenoLookup(ahora::get);

    @Test void laPrimeraConsultaPasa() {
        assertDoesNotThrow(() -> freno.comprobar(8));
    }

    @Test void dosSeguidasDelMismoUsuarioSonDemasiadas() {
        freno.comprobar(8);
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> freno.comprobar(8));
        assertEquals(429, ex.getStatusCode().value());
        assertEquals(FrenoLookup.MSG_DEMASIADAS, ex.getReason());
    }

    @Test void pasadoElIntervaloVuelveAPasar() {
        freno.comprobar(8);
        ahora.addAndGet(FrenoLookup.INTERVALO_MIN_MS);
        assertDoesNotThrow(() -> freno.comprobar(8));
    }

    @Test void elFrenoEsPorUsuario() {
        freno.comprobar(8);
        assertDoesNotThrow(() -> freno.comprobar(7));
    }
}
