package com.reparaciones.servidor.dao;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** Consultas del «Abrir» automático al entregar a glass (spec 2026-10-13). */
class ReparacionDAOAbrirGlassTest {

    private static final String IMEI = "351111112222333";

    private static ReparacionDAO dao(JdbcTemplate jdbc) {
        return new ReparacionDAO(jdbc, mock(BorradorDAO.class), mock(MovimientoDAO.class));
    }

    @SuppressWarnings("unchecked")
    @Test void normalAbiertaMasAntiguaMiraSoloReparacionesNormalesAbiertasDelImei() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        dao(jdbc).getNormalAbiertaMasAntigua(IMEI);
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).query(sql.capture(), any(RowMapper.class), eq(IMEI));
        String q = sql.getValue();
        assertTrue(q.contains("r.ID_REP LIKE 'A%' AND r.ID_REP NOT LIKE 'AG%' AND r.ID_REP NOT LIKE 'AP%' AND r.FECHA_FIN IS NULL"), q);
        assertTrue(q.endsWith(" AND r.IMEI = ? ORDER BY r.FECHA_ASIG ASC, r.ID_REP ASC LIMIT 1"), q);
        assertTrue(q.contains("r.ID_REP_ANTERIOR"), "lleva la incidencia de la asignación");
    }

    @SuppressWarnings("unchecked")
    @Test void normalAbiertaPorIdFiltraPorEseId() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        dao(jdbc).getNormalAbierta("A20261013_1");
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).query(sql.capture(), any(RowMapper.class), eq("A20261013_1"));
        assertTrue(sql.getValue().endsWith(" AND r.ID_REP = ?"), sql.getValue());
    }

    @SuppressWarnings("unchecked")
    @Test void modeloDelTelefonoNuloSiNoExiste() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.query(eq("SELECT MODELO FROM Telefono WHERE IMEI = ?"), any(RowMapper.class), eq(IMEI)))
                .thenReturn(List.of())
                .thenReturn(List.of("13pro"));
        assertNull(dao(jdbc).getModeloTelefono(IMEI));
        assertEquals("13pro", dao(jdbc).getModeloTelefono(IMEI));
    }

    @SuppressWarnings("unchecked")
    @Test void componentesOtroSinMirarSiEstanActivos() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        dao(jdbc).getComponentesOtro();
        verify(jdbc).query(eq("SELECT ID_COM, TIPO FROM Componente WHERE TIPO LIKE 'otro%'"), any(RowMapper.class));
    }

    @Test void aperturaManualCuentaAbrYAbiDelTecnicoDesdeSuAsignacion() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        LocalDateTime desde = LocalDateTime.of(2026, 10, 13, 8, 40);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq(IMEI), eq(7), eq(Timestamp.valueOf(desde))))
                .thenReturn(1)
                .thenReturn(0);
        assertTrue(dao(jdbc).tieneAperturaManual(IMEI, 7, desde));
        assertFalse(dao(jdbc).tieneAperturaManual(IMEI, 7, desde));
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc, times(2)).queryForObject(sql.capture(), eq(Integer.class), eq(IMEI), eq(7), eq(Timestamp.valueOf(desde)));
        String q = sql.getValue();
        assertTrue(q.contains("r.IMEI = ? AND r.ID_REP LIKE 'R%' AND r.ID_TEC = ? AND r.FECHA_FIN >= ?"), q);
        assertTrue(q.contains("c.TIPO LIKE 'otro%'"), q);
        assertTrue(q.contains("LOWER(TRIM(rc.OBSERVACIONES)) LIKE 'abr%'"), q);
        assertTrue(q.contains("LOWER(TRIM(rc.OBSERVACIONES)) LIKE 'abi%'"), q);
        assertTrue(q.contains("AND r.ID_REP NOT IN (SELECT g.ID_REP_ABRIR FROM Reparacion g WHERE g.ID_REP_ABRIR IS NOT NULL)"),
                "excluye los Abrir automaticos de entregas anteriores: " + q);
    }

    @SuppressWarnings("unchecked")
    @Test void accionesOtroSinDesdeQuedaComoAntes() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        dao(jdbc).getAccionesOtro(IMEI, "R", "A1", null);
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).query(sql.capture(), any(RowMapper.class), eq(IMEI), eq("R%"), eq("A1"));
        assertFalse(sql.getValue().contains("FECHA_FIN >= ?"), sql.getValue());
        assertTrue(sql.getValue().endsWith(" ORDER BY r.FECHA_FIN"), sql.getValue());
    }

    @SuppressWarnings("unchecked")
    @Test void accionesOtroConDesdeFiltraPorFechaFin() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        LocalDateTime desde = LocalDateTime.of(2026, 10, 13, 8, 40);
        dao(jdbc).getAccionesOtro(IMEI, "G", null, desde);
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).query(sql.capture(), any(RowMapper.class), eq(IMEI), eq("G%"), eq(""), eq(Timestamp.valueOf(desde)));
        assertTrue(sql.getValue().contains("AND r.FECHA_FIN >= ?"), sql.getValue());
        assertTrue(sql.getValue().endsWith(" ORDER BY r.FECHA_FIN"), sql.getValue());
    }

    @Test void marcarAbrirLoApuntaEnLasGlassAbiertasDelImei() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        dao(jdbc).marcarAbrir(IMEI, "R20261013_101");
        verify(jdbc).update(
                "UPDATE Reparacion SET ID_REP_ABRIR = ?, UPDATED_AT = UPDATED_AT" +
                " WHERE IMEI = ? AND ID_REP LIKE 'AG%' AND FECHA_FIN IS NULL",
                "R20261013_101", IMEI);
    }

    @SuppressWarnings("unchecked")
    @Test void abrirDeEntregaSoloDevuelveLosQueSiguenExistiendo() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        dao(jdbc).getAbrirDeEntrega(IMEI);
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).query(sql.capture(), any(RowMapper.class), eq(IMEI));
        String q = sql.getValue();
        assertTrue(q.contains("JOIN Reparacion r ON r.ID_REP = g.ID_REP_ABRIR"), q);
        assertTrue(q.contains("g.IMEI = ? AND g.ID_REP LIKE 'AG%' AND g.FECHA_FIN IS NULL"), q);
    }
}
