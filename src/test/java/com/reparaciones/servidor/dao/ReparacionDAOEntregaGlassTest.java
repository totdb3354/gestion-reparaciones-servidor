package com.reparaciones.servidor.dao;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ReparacionDAOEntregaGlassTest {

    private static final String IMEI = "351111112222333";

    private static ReparacionDAO dao(JdbcTemplate jdbc) {
        return new ReparacionDAO(jdbc, mock(BorradorDAO.class), mock(MovimientoDAO.class));
    }

    @SuppressWarnings("unchecked")
    @Test void asignacionesNormalesDevuelvenDerivadosDeLaGlassAbierta() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        dao(jdbc).getAsignaciones(null);
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).query(sql.capture(), any(RowMapper.class));
        String q = sql.getValue();
        assertTrue(q.contains("AS GLASS_ABIERTAS"), "GLASS_ABIERTAS");
        assertTrue(q.contains("AS GLASS_ENTREGADO_AT"), "GLASS_ENTREGADO_AT");
        assertTrue(q.contains("AS GLASS_ENTREGADO_POR_NOMBRE"), "GLASS_ENTREGADO_POR_NOMBRE");
        assertTrue(q.contains("AS GLASS_TECNICO_NOMBRE"), "GLASS_TECNICO_NOMBRE");
        // las subconsultas miran solo AG abiertas del mismo IMEI
        assertTrue(q.contains("g.IMEI = r.IMEI AND g.ID_REP LIKE 'AG%' AND g.FECHA_FIN IS NULL"));
    }

    @SuppressWarnings("unchecked")
    @Test void asignacionesGlassDevuelvenEntregaYAgrupanPorLasColumnasNuevas() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        dao(jdbc).getAsignacionesGlass(null);
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).query(sql.capture(), any(RowMapper.class));
        String q = sql.getValue();
        assertTrue(q.contains(" r.ENTREGADO_AT,"), "columna ENTREGADO_AT");
        assertTrue(q.contains("AS ENTREGADO_POR_NOMBRE"), "ENTREGADO_POR_NOMBRE");
        assertTrue(q.contains("r.ES_CHASIS, r.ENTREGADO_AT, r.ENTREGADO_POR, ta.NOMBRE"), "GROUP BY con columnas nuevas");
    }
}
