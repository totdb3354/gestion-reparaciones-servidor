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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** La existencia de la reparación a borrar la decide el propio DAO contra la fila real de Reparacion
 *  (sin el filtro 'R%'/'G%' que usa la vista del controlador): así un pulido 'P...', borrado por esta
 *  misma ruta desde el JavaFX (vista Agrupado), se sigue borrando aunque la vista no lo devuelva. */
class ReparacionDAOEliminarTest {

    private static final String IMEI = "351111112222333";

    private static ReparacionDAO dao(JdbcTemplate jdbc) {
        return new ReparacionDAO(jdbc, mock(BorradorDAO.class), mock(MovimientoDAO.class));
    }

    @SuppressWarnings("unchecked")
    @Test void inexistenteEs404YNoBorraNiConsultaNada() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.query(anyString(), any(RowMapper.class), eq("P20260927_1")))
                .thenReturn(Collections.emptyList());

        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> dao(jdbc).eliminar("P20260927_1"));
        assertEquals(HttpStatus.NOT_FOUND, e.getStatusCode());
        assertEquals("Recurso no encontrado: P20260927_1", e.getReason());
        // Solo las dos consultas antes de la comprobación (componentes e IMEI); nunca llega a mirar
        // ID_REP_ANTERIOR ni a escribir nada.
        verify(jdbc, times(2)).query(anyString(), any(RowMapper.class), eq("P20260927_1"));
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    @SuppressWarnings("unchecked")
    @Test void existenteBorraComoAntes() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.query(
                eq("SELECT ID_COM, ES_REUTILIZADO, CANTIDAD FROM Reparacion_componente WHERE ID_REP = ?"),
                any(RowMapper.class), eq("R20260927_1")))
                .thenReturn(Collections.emptyList());
        when(jdbc.query(
                eq("SELECT IMEI FROM Reparacion WHERE ID_REP = ?"),
                any(RowMapper.class), eq("R20260927_1")))
                .thenAnswer(inv -> Collections.singletonList(IMEI));
        when(jdbc.query(
                eq("SELECT ID_REP_ANTERIOR FROM Reparacion WHERE ID_REP = ? AND ID_REP_ANTERIOR IS NOT NULL"),
                any(RowMapper.class), eq("R20260927_1")))
                .thenReturn(Collections.emptyList());
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq(IMEI))).thenReturn(1);

        dao(jdbc).eliminar("R20260927_1");

        verify(jdbc).update("DELETE FROM Reparacion_componente WHERE ID_REP = ?", "R20260927_1");
        verify(jdbc).update("DELETE FROM Reparacion WHERE ID_REP = ?", "R20260927_1");
    }
}
