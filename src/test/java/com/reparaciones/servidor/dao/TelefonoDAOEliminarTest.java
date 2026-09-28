package com.reparaciones.servidor.dao;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Borrar un teléfono con registros asociados responde conflicto con un mensaje que dice cuáles hay (spec sp7b §4.4). */
class TelefonoDAOEliminarTest {

    private static final String IMEI = "355400000000111";

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final TelefonoDAO dao = new TelefonoDAO(jdbc);

    private static Map<String, Object> conteos(int trabajos, int revisiones, int movimientos, int envios) {
        return Map.of(
                "TRABAJOS", trabajos, "REVISIONES", revisiones,
                "MOVIMIENTOS", movimientos, "ENVIOS", envios);
    }

    @Test void conSoloTrabajosEs409ConMensajeDeTrabajosYNoBorra() {
        when(jdbc.queryForMap(anyString(), any(), any(), any(), any())).thenReturn(conteos(1, 0, 0, 0));
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> dao.eliminar(IMEI));
        assertEquals(409, ex.getStatusCode().value());
        assertEquals(TelefonoDAO.MSG_TIENE_TRABAJOS, ex.getReason());
        verify(jdbc, never()).update(anyString(), (Object[]) any());
    }

    /** Movimiento_telefono recoge la vida de cada teléfono importado: es el caso común, no el raro,
     *  y el mensaje no puede decir "trabajos" cuando lo único que hay es un movimiento. */
    @Test void conSoloUnMovimientoEs409ConMensajeQueNoDiceTrabajosYNoBorra() {
        when(jdbc.queryForMap(anyString(), any(), any(), any(), any())).thenReturn(conteos(0, 0, 1, 0));
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> dao.eliminar(IMEI));
        assertEquals(409, ex.getStatusCode().value());
        assertFalse(ex.getReason().contains("trabajos"), "no debería mencionar trabajos: " + ex.getReason());
        assertTrue(ex.getReason().contains("movimientos"), "debería mencionar movimientos: " + ex.getReason());
        verify(jdbc, never()).update(anyString(), (Object[]) any());
    }

    @Test void sinNingunRegistroBorra() {
        when(jdbc.queryForMap(anyString(), any(), any(), any(), any())).thenReturn(conteos(0, 0, 0, 0));
        dao.eliminar(IMEI);
        verify(jdbc).update("DELETE FROM Telefono WHERE IMEI = ?", IMEI);
    }

    /** Carrera: el conteo dice cero en las cuatro tablas pero el borrado choca con una clave ajena porque otra
     *  transacción insertó una fila mientras tanto; responde el mismo conflicto, con el mensaje de reserva
     *  porque aquí ya no se sabe cuál de las cuatro fue. */
    @Test void siElBorradoChocaConLaClaveAjenaEs409ConMensajeDeReserva() {
        when(jdbc.queryForMap(anyString(), any(), any(), any(), any())).thenReturn(conteos(0, 0, 0, 0));
        when(jdbc.update(eq("DELETE FROM Telefono WHERE IMEI = ?"), eq(IMEI)))
                .thenThrow(new DataIntegrityViolationException("fk violada"));
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> dao.eliminar(IMEI));
        assertEquals(409, ex.getStatusCode().value());
        assertEquals(TelefonoDAO.MSG_TIENE_REGISTROS, ex.getReason());
        assertFalse(ex.getReason().contains("trabajos"), "el mensaje de reserva no debería afirmar trabajos");
    }
}
