package com.reparaciones.servidor.dao;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** «Corregir cliente» (spec 0.9.8 añadido §3): lectura de los cerrados de un IMEI y corrección todo o nada. */
class ReparacionDAOCorregirClienteTest {

    private static final String IMEI = "355400000000111";

    private static ReparacionDAO dao(JdbcTemplate jdbc) {
        return new ReparacionDAO(jdbc, mock(BorradorDAO.class), mock(MovimientoDAO.class));
    }

    @SuppressWarnings("unchecked")
    @Test void losCerradosDeUnImeiSonDeCualquierTipoYSoloCerrados() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        dao(jdbc).getCerradosPorImei(IMEI);
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).query(sql.capture(), any(RowMapper.class), eq(IMEI));
        String q = sql.getValue();
        assertTrue(q.contains("WHERE r.IMEI = ? AND r.FECHA_FIN IS NOT NULL"), q);
        assertTrue(q.contains("LEFT JOIN Cliente cli ON cli.ID_CLI = r.ID_CLI"), q);
        assertTrue(q.contains("ORDER BY r.FECHA_FIN DESC"), q);
        assertFalse(q.contains("LIKE 'R%' AND"), "no se limita a un tipo");
        assertFalse(q.contains("JOIN Reparacion_componente rc ON rc.ID_REP = r.ID_REP"), "sin JOIN a las piezas: no repite trabajos");
        assertTrue(q.contains("ORDER BY rc.ID_RC LIMIT 1"), "una sola descripción por trabajo");
    }

    @Test void corregirCambiaElClienteConservandoUpdatedAt() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(startsWith("SELECT COUNT(*) FROM Reparacion"), eq(Integer.class), any(Object[].class))).thenReturn(2);
        dao(jdbc).corregirCliente(IMEI, List.of("R20261002_1", "R20261002_2"), 7);
        verify(jdbc).update(eq("UPDATE Reparacion SET ID_CLI = ?, UPDATED_AT = UPDATED_AT"
                + " WHERE IMEI = ? AND FECHA_FIN IS NOT NULL AND ID_REP IN (?,?)"), eq(7), eq(IMEI), eq("R20261002_1"), eq("R20261002_2"));
    }

    @Test void siAlgunoNoEstaCerradoONoEsDelImeiEs409YNoCambiaNada() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(startsWith("SELECT COUNT(*) FROM Reparacion"), eq(Integer.class), any(Object[].class))).thenReturn(1);
        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> dao(jdbc).corregirCliente(IMEI, List.of("R20261002_1", "A20261002_9"), null));
        assertEquals(409, e.getStatusCode().value());
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }
}
