package com.reparaciones.servidor.bot;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BotControllerTest {

    private static final LocalDateTime F = LocalDateTime.of(2026, 11, 25, 10, 0);

    private static MovimientoBot mov(String origen, String motivo) {
        return new MovimientoBot("350000000000001", "DEVUELTO", "OK", origen, "CAJA LISTOS", motivo, F);
    }

    @Test void unCuerpoBuenoPasaYUnoVacioTambien() {
        assertDoesNotThrow(() -> BotController.validar(new MovimientosBot("r1", List.of(mov(null, null)))));
        assertDoesNotThrow(() -> BotController.validar(new MovimientosBot("r1", List.of())));
    }

    @Test void largosYTopesSon400() {
        assertThrows(ResponseStatusException.class, () -> BotController.validar(new MovimientosBot("r".repeat(41), List.of())));
        assertThrows(ResponseStatusException.class, () -> BotController.validar(new MovimientosBot("r1", List.of(mov("x".repeat(81), null)))));
        assertThrows(ResponseStatusException.class, () -> BotController.validar(new MovimientosBot("r1", List.of(mov(null, "x".repeat(31))))));
        assertThrows(ResponseStatusException.class, () -> BotController.validar(new MovimientosBot("r1",
                Collections.nCopies(BotController.MAX_MOVIMIENTOS + 1, mov(null, null)))));
        assertThrows(ResponseStatusException.class, () -> BotController.validar(new MovimientosBot("r1", null)));
    }

    @Test void devueltoSinDestinoEs400PeroDescontadoSinDestinoPasa() {
        var sinDestino = new MovimientoBot("350000000000001", "DEVUELTO", "OK", null, " ", null, F);
        assertThrows(ResponseStatusException.class, () -> BotController.validar(new MovimientosBot("r1", List.of(sinDestino))));
        var descontado = new MovimientoBot("350000000000001", "DESCONTADO", "OK", "CAJA LISTOS", null, null, F);
        assertDoesNotThrow(() -> BotController.validar(new MovimientosBot("r1", List.of(descontado))));
    }
}
