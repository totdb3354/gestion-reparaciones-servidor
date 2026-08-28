package com.reparaciones.servidor.dao;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
        // las tres subconsultas comparten la misma AG (la más antigua): GLASS_TECNICO_NOMBRE usa JOIN interno
        // (ID_TEC es NOT NULL, siempre hay dueño), GLASS_ENTREGADO_POR_NOMBRE usa LEFT JOIN (ENTREGADO_POR
        // puede ser NULL si esa AG aún no se ha entregado) para no descartar la fila antes del ORDER BY/LIMIT 1.
        assertTrue(q.contains("ORDER BY g.FECHA_ASIG ASC LIMIT 1) AS GLASS_TECNICO_NOMBRE"));
        assertTrue(q.contains("JOIN Tecnico tg ON g.ID_TEC = tg.ID_TEC"));
        assertTrue(q.contains("LEFT JOIN Tecnico tg ON g.ENTREGADO_POR = tg.ID_TEC"));
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

    @SuppressWarnings("unchecked")
    @Test void completadasHoyAgrupaLaConsultaGlassPorLasColumnasNuevas() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        java.sql.Timestamp cutoff = java.sql.Timestamp.valueOf("2026-08-28 00:00:00");
        dao(jdbc).getAsignacionesCompletadasHoy(cutoff);
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        // getAsignacionesCompletadasHoy hace dos jdbc.query(...) con el mismo cutoff: uno para las
        // filas A (ASIGNACION_SELECT) y otro para las filas AG (GLASS_ASIGNACION_SELECT); solo el
        // segundo debe llevar ENTREGADO_AT/ENTREGADO_POR en su GROUP BY.
        verify(jdbc, times(2)).query(sql.capture(), any(RowMapper.class), eq(cutoff));
        boolean glassGroupByOk = sql.getAllValues().stream()
                .anyMatch(q -> q.contains("r.ES_CHASIS, r.ENTREGADO_AT, r.ENTREGADO_POR, ta.NOMBRE"));
        assertTrue(glassGroupByOk, "GROUP BY de completadas-hoy (glass) con ENTREGADO_AT/ENTREGADO_POR");
    }

    @Test void entregarSellaTodasLasGlassAbiertasDelImei() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        dao(jdbc).entregarGlass(IMEI, 7);
        verify(jdbc).update(
                "UPDATE Reparacion SET ENTREGADO_AT = NOW(), ENTREGADO_POR = ?, UPDATED_AT = UPDATED_AT" +
                " WHERE IMEI = ? AND ID_REP LIKE 'AG%' AND FECHA_FIN IS NULL",
                7, IMEI);
    }

    @Test void deshacerLimpiaTodasLasGlassAbiertasDelImei() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        dao(jdbc).deshacerEntregaGlass(IMEI);
        verify(jdbc).update(
                "UPDATE Reparacion SET ENTREGADO_AT = NULL, ENTREGADO_POR = NULL, UPDATED_AT = UPDATED_AT" +
                " WHERE IMEI = ? AND ID_REP LIKE 'AG%' AND FECHA_FIN IS NULL",
                IMEI);
    }

    @SuppressWarnings("unchecked")
    @Test void glassAbiertasConsultaSoloAgAbiertasDelImei() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        dao(jdbc).getGlassAbiertas(IMEI);
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).query(sql.capture(), any(RowMapper.class), org.mockito.ArgumentMatchers.eq(IMEI));
        assertTrue(sql.getValue().contains("WHERE r.IMEI = ? AND r.ID_REP LIKE 'AG%' AND r.FECHA_FIN IS NULL"));
    }
}
