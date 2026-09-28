package com.reparaciones.servidor.security;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Ritmo máximo del lookup externo de IMEI, por usuario, en ráfaga (spec sp7b §4.4). */
class FrenoLookupTest {

    private final AtomicLong ahora = new AtomicLong(1_000_000L);
    private final FrenoLookup freno = new FrenoLookup(ahora::get);

    @Test void laRafagaCompletaPasa() {
        for (int i = 0; i < FrenoLookup.MAX_POR_VENTANA; i++) {
            int n = i;
            assertDoesNotThrow(() -> freno.comprobar(8), "consulta " + n + " deberia pasar");
        }
    }

    @Test void laSiguienteDentroDeLaMismaVentanaEsDemasiada() {
        for (int i = 0; i < FrenoLookup.MAX_POR_VENTANA; i++) {
            freno.comprobar(8);
        }
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> freno.comprobar(8));
        assertEquals(429, ex.getStatusCode().value());
        assertEquals(FrenoLookup.MSG_DEMASIADAS, ex.getReason());
    }

    @Test void pasadaLaVentanaVuelveAPasar() {
        for (int i = 0; i < FrenoLookup.MAX_POR_VENTANA; i++) {
            freno.comprobar(8);
        }
        ahora.addAndGet(FrenoLookup.VENTANA_MS);
        assertDoesNotThrow(() -> freno.comprobar(8));
    }

    @Test void elFrenoEsPorUsuario() {
        for (int i = 0; i < FrenoLookup.MAX_POR_VENTANA; i++) {
            freno.comprobar(8);
        }
        assertDoesNotThrow(() -> freno.comprobar(7));
    }

    @Test void unRechazoNoDejaAlUsuarioBloqueadoMasDeLoDebido() {
        for (int i = 0; i < FrenoLookup.MAX_POR_VENTANA; i++) {
            freno.comprobar(8);
        }
        assertThrows(ResponseStatusException.class, () -> freno.comprobar(8));
        // avanzamos justo lo suficiente para que la primera consulta salga de la ventana
        ahora.addAndGet(1L);
        assertThrows(ResponseStatusException.class, () -> freno.comprobar(8));
        ahora.addAndGet(FrenoLookup.VENTANA_MS - 1L);
        assertDoesNotThrow(() -> freno.comprobar(8));
    }
}
