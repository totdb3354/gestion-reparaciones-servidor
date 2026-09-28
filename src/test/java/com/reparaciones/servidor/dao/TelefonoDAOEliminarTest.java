package com.reparaciones.servidor.dao;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Borrar un teléfono con trabajos registrados responde conflicto con un mensaje que lo explica (spec sp7b §4.4). */
class TelefonoDAOEliminarTest {

    private static final String IMEI = "355400000000111";

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final TelefonoDAO dao = new TelefonoDAO(jdbc);

    @Test void conTrabajosRegistradosEs409ConMensajeYNoBorra() {
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq(IMEI))).thenReturn(1);
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> dao.eliminar(IMEI));
        assertEquals(409, ex.getStatusCode().value());
        assertEquals(TelefonoDAO.MSG_TIENE_TRABAJOS, ex.getReason());
        verify(jdbc, never()).update(anyString(), (Object[]) any());
    }

    @Test void sinTrabajosBorra() {
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq(IMEI))).thenReturn(0);
        dao.eliminar(IMEI);
        verify(jdbc).update("DELETE FROM Telefono WHERE IMEI = ?", IMEI);
    }

    /** Carrera: el conteo dice 0 pero el borrado choca con la clave ajena porque otra transacción
     *  insertó un trabajo mientras tanto; debe responder el mismo conflicto, con el mismo mensaje. */
    @Test void siElBorradoChocaConLaClaveAjenaEs409ConMensaje() {
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq(IMEI))).thenReturn(0);
        when(jdbc.update(eq("DELETE FROM Telefono WHERE IMEI = ?"), eq(IMEI)))
                .thenThrow(new DataIntegrityViolationException("fk violada"));
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> dao.eliminar(IMEI));
        assertEquals(409, ex.getStatusCode().value());
        assertEquals(TelefonoDAO.MSG_TIENE_TRABAJOS, ex.getReason());
    }
}
