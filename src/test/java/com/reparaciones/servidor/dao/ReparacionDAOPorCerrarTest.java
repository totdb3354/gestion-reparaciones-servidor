package com.reparaciones.servidor.dao;

import com.reparaciones.servidor.model.ReparacionResumen;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/** Fecha de la marca «por cerrar» (spec 2026-11-16-carga-por-cerrar-hecho §1). */
class ReparacionDAOPorCerrarTest {

    private static final String SQL_POR_CERRAR =
            "UPDATE Reparacion SET POR_CERRAR = ?, POR_CERRAR_AT = CASE WHEN ? THEN NOW() END," +
            " UPDATED_AT = UPDATED_AT WHERE ID_REP = ?";

    private static ReparacionDAO dao(JdbcTemplate jdbc) {
        return new ReparacionDAO(jdbc, mock(BorradorDAO.class), mock(MovimientoDAO.class));
    }

    private static List<String> consultas(JdbcTemplate jdbc) {
        return mockingDetails(jdbc).getInvocations().stream()
                .filter(i -> i.getMethod().getName().equals("query"))
                .map(i -> (String) i.getArgument(0))
                .toList();
    }

    @Test void marcarPoneLaHoraYQuitarLaBorra() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        dao(jdbc).actualizarPorCerrar("A20261116_1", true);
        dao(jdbc).actualizarPorCerrar("A20261116_1", false);
        verify(jdbc).update(SQL_POR_CERRAR, true, true, "A20261116_1");
        verify(jdbc).update(SQL_POR_CERRAR, false, false, "A20261116_1");
    }

    @Test void lasConsultasDeAsignacionesDeReparacionTraenYAgrupanLaFecha() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        ReparacionDAO d = dao(jdbc);
        d.getAsignaciones(null);
        d.getAsignacionesCompletadasHoy(Timestamp.valueOf("2026-11-16 00:00:00"));   // la 1ª es la de A%
        d.getAsignacionById("A20261116_1");
        List<String> qs = consultas(jdbc);
        for (String q : List.of(qs.get(0), qs.get(1), qs.get(3))) {
            assertTrue(q.contains("r.POR_CERRAR, r.POR_CERRAR_AT, (SELECT COUNT(*) FROM Reparacion g"), q);  // select
            assertTrue(q.contains("r.ES_CHASIS, r.POR_CERRAR, r.POR_CERRAR_AT, ta.NOMBRE"), q);  // group by
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Test void elMapperLeeLaFecha() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        dao(jdbc).getAsignaciones(null);
        ArgumentCaptor<RowMapper> mapper = ArgumentCaptor.forClass(RowMapper.class);
        verify(jdbc).query(anyString(), mapper.capture());
        ResultSet rs = mock(ResultSet.class);
        Timestamp t = Timestamp.valueOf("2026-11-16 08:00:00");
        when(rs.getTimestamp("FECHA_ASIG")).thenReturn(t);
        when(rs.getTimestamp("UPDATED_AT")).thenReturn(t);
        when(rs.getTimestamp("POR_CERRAR_AT")).thenReturn(Timestamp.valueOf("2026-11-16 10:30:00"));
        ReparacionResumen rr = (ReparacionResumen) mapper.getValue().mapRow(rs, 0);
        assertEquals(LocalDateTime.of(2026, 11, 16, 10, 30), rr.getPorCerrarAt());

        when(rs.getTimestamp("POR_CERRAR_AT")).thenReturn(null);
        assertNull(((ReparacionResumen) mapper.getValue().mapRow(rs, 0)).getPorCerrarAt());
    }
}
