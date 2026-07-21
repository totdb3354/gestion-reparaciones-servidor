package com.reparaciones.servidor.dao;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class ReparacionDAOUrgenteTest {

    private static final String IMEI = "351111112222333";

    @Test void propagarUrgenteActualizaTodasLasAbiertasDelImei() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        ReparacionDAO dao = new ReparacionDAO(jdbc, mock(BorradorDAO.class));
        dao.propagarUrgente(IMEI, true);
        verify(jdbc).update("UPDATE Reparacion SET URGENTE = ?, UPDATED_AT = UPDATED_AT WHERE IMEI = ? AND FECHA_FIN IS NULL",
                true, IMEI);
    }

    @Test void tieneAbiertaUrgenteConsultaSoloAbiertasUrgentes() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(contains("FECHA_FIN IS NULL AND URGENTE = TRUE"), eq(Integer.class), eq(IMEI)))
                .thenReturn(2);
        ReparacionDAO dao = new ReparacionDAO(jdbc, mock(BorradorDAO.class));
        assertTrue(dao.tieneAbiertaUrgente(IMEI));
    }

    @SuppressWarnings("unchecked")
    @Test void actualizarUrgenteResuelveElImeiYPropaga() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        // getImeiByIdRep usa jdbc.query(...) con RowMapper (no queryForObject); se mockea
        // el método realmente invocado, el SQL sigue conteniendo "SELECT IMEI" literal.
        when(jdbc.query(contains("SELECT IMEI"), any(RowMapper.class), eq("A20260721_1")))
                .thenReturn(List.of(IMEI));
        ReparacionDAO dao = new ReparacionDAO(jdbc, mock(BorradorDAO.class));
        dao.actualizarUrgente("A20260721_1", false);
        verify(jdbc).update(contains("WHERE IMEI = ? AND FECHA_FIN IS NULL"), eq(false), eq(IMEI));
    }

    @Test void marcarUrgentesClienteVencidasMarcaTodoElTelefono() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        ReparacionDAO dao = new ReparacionDAO(jdbc, mock(BorradorDAO.class));
        java.sql.Timestamp cutoff = java.sql.Timestamp.valueOf("2026-07-21 00:00:00");
        dao.marcarUrgentesClienteVencidas(cutoff);
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).update(sql.capture(), eq(cutoff));
        String s = sql.getValue();
        assertTrue(s.contains("SELECT DISTINCT r2.IMEI"));                       // cualifica por teléfono
        assertTrue(s.contains("r2.ID_REP LIKE 'A%' AND r2.ID_REP NOT LIKE 'AP%'")); // disparador: rep/glass
        assertTrue(s.contains("t.ID_CLI IS NOT NULL"));
        assertTrue(s.contains("WHERE r.FECHA_FIN IS NULL AND r.URGENTE = FALSE"));  // marca TODAS las abiertas
    }
}
