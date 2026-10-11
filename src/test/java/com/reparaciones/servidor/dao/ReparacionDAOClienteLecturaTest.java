package com.reparaciones.servidor.dao;

import com.reparaciones.servidor.model.ReparacionResumen;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/** Regla de lectura (spec 0.9.8 §5): abierto → cliente del teléfono; cerrado → el guardado. Y el del teléfono aparte. */
class ReparacionDAOClienteLecturaTest {

    private static final String JOIN =
            " LEFT JOIN Cliente cli ON cli.ID_CLI = CASE WHEN r.FECHA_FIN IS NOT NULL THEN r.ID_CLI ELSE tel.ID_CLI END" +
            " LEFT JOIN Cliente cliTel ON cliTel.ID_CLI = tel.ID_CLI";
    private static final Timestamp CORTE = Timestamp.valueOf("2026-10-09 00:00:00");

    private static ReparacionDAO dao(JdbcTemplate jdbc) {
        return new ReparacionDAO(jdbc, mock(BorradorDAO.class), mock(MovimientoDAO.class));
    }

    /** SQL de cada jdbc.query emitido, en orden. */
    private static List<String> consultas(JdbcTemplate jdbc) {
        return mockingDetails(jdbc).getInvocations().stream()
                .filter(i -> i.getMethod().getName().equals("query"))
                .map(i -> (String) i.getArgument(0))
                .toList();
    }

    @Test void todasLasConsultasDeTrabajosAplicanLaReglaYDevuelvenElClienteDelTelefono() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        ReparacionDAO d = dao(jdbc);
        d.getHistorial(null);
        d.getAsignaciones(null);
        d.getAsignacionesCompletadasHoy(CORTE);   // dos consultas: A y AG
        d.getAsignacionesGlass(null);
        d.getHistorialGlass(null);
        d.getAsignacionesPulido(null);
        d.getHistorialPulido(null);
        List<String> qs = consultas(jdbc);
        assertEquals(8, qs.size());
        for (String q : qs) {
            assertTrue(q.contains(JOIN), q);
            assertTrue(q.contains(" cli.NOMBRE AS CLIENTE, cliTel.NOMBRE AS CLIENTE_TELEFONO"), q);
            assertFalse(q.contains("LEFT JOIN Cliente cli ON tel.ID_CLI = cli.ID_CLI"), q);
        }
    }

    @Test void completadasHoySigueConUnSoloParametro() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        dao(jdbc).getAsignacionesCompletadasHoy(CORTE);
        List<String> qs = consultas(jdbc);
        assertEquals(2, qs.size());
        for (String q : qs) assertEquals(1, q.chars().filter(c -> c == '?').count(), q);
    }

    @Test void lasConsultasAgrupadasAgrupanTambienPorElClienteDelTelefono() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        ReparacionDAO d = dao(jdbc);
        d.getAsignaciones(null);
        d.getAsignacionesGlass(null);
        d.getAsignacionById("A20261009_1");
        d.getAsignacionGlassById("AG20261009_1");
        for (String q : consultas(jdbc)) assertTrue(q.contains("ta.NOMBRE, cli.NOMBRE, cliTel.NOMBRE"), q);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Test void elMapperLeeElClienteDelTrabajoYElDelTelefono() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        dao(jdbc).getHistorial(null);
        ArgumentCaptor<RowMapper> mapper = ArgumentCaptor.forClass(RowMapper.class);
        verify(jdbc).query(anyString(), mapper.capture());
        ResultSet rs = mock(ResultSet.class);
        Timestamp t = Timestamp.valueOf("2026-10-09 08:00:00");
        when(rs.getTimestamp("FECHA_ASIG")).thenReturn(t);
        when(rs.getTimestamp("UPDATED_AT")).thenReturn(t);
        when(rs.getString("CLIENTE")).thenReturn("Cliente A");
        when(rs.getString("CLIENTE_TELEFONO")).thenReturn("Cliente B");
        ReparacionResumen rr = (ReparacionResumen) mapper.getValue().mapRow(rs, 0);
        assertEquals("Cliente A", rr.getCliente());
        assertEquals("Cliente B", rr.getClienteTelefono());
    }
}
