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

/** Borrar una fila de Reparacion_componente que ya no existe (ID_REP + ID_COM) usaba queryForObject y
 *  lanzaba EmptyResultDataAccessException, que el controlador dejaba pasar como 500; debe ser 404. */
class ReparacionComponenteDAOEliminarTest {

    @SuppressWarnings("unchecked")
    @Test void eliminarInexistenteEs404YNoBorraNiTocaStock() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.query(anyString(), any(RowMapper.class), eq("A20260927_1"), eq(5)))
                .thenReturn(Collections.emptyList());
        ReparacionComponenteDAO dao = new ReparacionComponenteDAO(jdbc);

        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> dao.eliminar("A20260927_1", 5));
        assertEquals(HttpStatus.NOT_FOUND, e.getStatusCode());
        assertEquals("Recurso no encontrado: A20260927_1/5", e.getReason());
        verify(jdbc, never()).update(startsWith("DELETE FROM Reparacion_componente"), any(), any());
        verify(jdbc, never()).update(startsWith("UPDATE Componente"), any(), any(), any());
    }

    @SuppressWarnings("unchecked")
    @Test void eliminarExistenteBorraYRepone() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        java.sql.ResultSet rs = mock(java.sql.ResultSet.class);
        when(rs.getBoolean("ES_REUTILIZADO")).thenReturn(false);
        when(rs.getBoolean("ES_SOLICITUD")).thenReturn(false);
        when(rs.getInt("CANTIDAD")).thenReturn(2);
        when(jdbc.query(anyString(), any(RowMapper.class), eq("A20260927_1"), eq(5)))
                .thenAnswer(inv -> Collections.singletonList(
                        ((RowMapper<?>) inv.getArgument(1)).mapRow(rs, 0)));

        ReparacionComponenteDAO dao = new ReparacionComponenteDAO(jdbc);
        dao.eliminar("A20260927_1", 5);

        verify(jdbc).update("DELETE FROM Reparacion_componente WHERE ID_REP = ? AND ID_COM = ?", "A20260927_1", 5);
        verify(jdbc).update("UPDATE Componente SET STOCK = STOCK + ? WHERE ID_COM = ? AND TIPO NOT LIKE 'otro%'", 2, 5);
    }
}
