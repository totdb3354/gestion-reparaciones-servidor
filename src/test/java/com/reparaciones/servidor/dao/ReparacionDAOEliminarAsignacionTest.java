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

/** La existencia de la asignación a borrar la decide el propio DAO contra la fila real de Reparacion
 *  (sin el filtro FECHA_FIN IS NULL / sin 'AP%' que usa la vista del controlador), dentro de la misma
 *  transacción: así una fila que existe pero la vista no devuelve se sigue borrando, y dos borrados
 *  simultáneos de la misma fila quedan cubiertos igual que el caso secuencial. */
class ReparacionDAOEliminarAsignacionTest {

    private static final String IMEI = "351111112222333";

    private static ReparacionDAO dao(JdbcTemplate jdbc) {
        return new ReparacionDAO(jdbc, mock(BorradorDAO.class), mock(MovimientoDAO.class));
    }

    @SuppressWarnings("unchecked")
    @Test void inexistenteEs404YNoBorraNada() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.query(anyString(), any(RowMapper.class), eq("A20260927_1")))
                .thenReturn(Collections.emptyList());

        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> dao(jdbc).eliminarAsignacion("A20260927_1"));
        assertEquals(HttpStatus.NOT_FOUND, e.getStatusCode());
        assertEquals("Recurso no encontrado: A20260927_1", e.getReason());
        verify(jdbc, never()).update(anyString(), any(Object[].class));
        verify(jdbc, never()).queryForObject(anyString(), eq(Integer.class), any());
    }

    @SuppressWarnings("unchecked")
    @Test void existenteBorraYLimpiaComoAntes() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.query(anyString(), any(RowMapper.class), eq("A20260927_1")))
                .thenAnswer(inv -> Collections.singletonList(IMEI));
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq(IMEI))).thenReturn(1);

        dao(jdbc).eliminarAsignacion("A20260927_1");

        verify(jdbc).update("DELETE FROM Reparacion_componente WHERE ID_REP = ?", "A20260927_1");
        verify(jdbc).update("DELETE FROM Reparacion WHERE ID_REP = ?", "A20260927_1");
    }
}
