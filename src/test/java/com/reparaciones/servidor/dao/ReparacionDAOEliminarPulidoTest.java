package com.reparaciones.servidor.dao;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.web.server.ResponseStatusException;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** Borrar del historial un pulido que ya no existe (o que ya no está finalizado) usaba queryForObject
 *  y lanzaba EmptyResultDataAccessException, que el controlador dejaba pasar como 500; debe ser 404. */
class ReparacionDAOEliminarPulidoTest {

    private static final String IMEI = "351111112222333";

    private static ReparacionDAO dao(JdbcTemplate jdbc) {
        return new ReparacionDAO(jdbc, mock(BorradorDAO.class), mock(MovimientoDAO.class));
    }

    @SuppressWarnings("unchecked")
    @Test void eliminarPulidoInexistenteEs404YNoBorra() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.query(anyString(), any(RowMapper.class), eq("AP20260927_1")))
                .thenReturn(Collections.emptyList());

        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> dao(jdbc).eliminarPulido("AP20260927_1"));
        assertEquals(HttpStatus.NOT_FOUND, e.getStatusCode());
        assertEquals("Recurso no encontrado: AP20260927_1", e.getReason());
        verify(jdbc, never()).update(eq("DELETE FROM Reparacion WHERE ID_REP = ?"), eq("AP20260927_1"));
    }

    @SuppressWarnings("unchecked")
    @Test void eliminarPulidoExistenteBorraComoAntes() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.query(anyString(), any(RowMapper.class), eq("AP20260927_1")))
                .thenAnswer(inv -> Collections.singletonList(IMEI));
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq(IMEI))).thenReturn(1);

        dao(jdbc).eliminarPulido("AP20260927_1");

        verify(jdbc).update("DELETE FROM Reparacion WHERE ID_REP = ?", "AP20260927_1");
    }
}
