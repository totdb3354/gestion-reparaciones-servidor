package com.reparaciones.servidor.dao;

import com.reparaciones.servidor.model.Revision;
import com.reparaciones.servidor.model.RevisionFuncional;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class RevisionDAOTest {

    private static final String IMEI = "351111112222333";

    /** Telefono EN_REVISION con revisión vigente id=7: guardar estética actualiza la fila vigente y espeja el grado. */
    @Test void guardarEsteticaActualizaVigenteYEspejaGrado() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        RevisionDAO dao = new RevisionDAO(jdbc);
        when(jdbc.queryForObject(eq("SELECT MAX(ID_REVISION) FROM Revision WHERE IMEI = ?"), eq(Integer.class), eq(IMEI))).thenReturn(7);
        when(jdbc.queryForObject(eq("SELECT ESTADO FROM Telefono WHERE IMEI = ?"), eq(String.class), eq(IMEI))).thenReturn("EN_REVISION");
        dao.guardarEstetica(IMEI, "B", "P", 3);
        verify(jdbc).update("UPDATE Revision SET EST_GRADO = ?, EST_PANT = ?, EST_ID_USU = ?, EST_FECHA = NOW() WHERE ID_REVISION = ?",
                "B", "P", 3, 7);
        verify(jdbc).update("UPDATE Telefono SET GRADO_PROPIO = ? WHERE IMEI = ?", "B", IMEI);
    }

    @Test void guardarEsteticaRechazaSiNoEstaEnRevision() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        RevisionDAO dao = new RevisionDAO(jdbc);
        when(jdbc.queryForObject(eq("SELECT MAX(ID_REVISION) FROM Revision WHERE IMEI = ?"), eq(Integer.class), eq(IMEI))).thenReturn(7);
        when(jdbc.queryForObject(eq("SELECT ESTADO FROM Telefono WHERE IMEI = ?"), eq(String.class), eq(IMEI))).thenReturn("OK");
        assertThrows(ResponseStatusException.class, () -> dao.guardarEstetica(IMEI, "B", null, 3));
        verify(jdbc, never()).update(contains("UPDATE Revision"), any(), any(), any(), any());
    }

    @Test void guardarFuncionalActualizaVigente() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        RevisionDAO dao = new RevisionDAO(jdbc);
        when(jdbc.queryForObject(eq("SELECT MAX(ID_REVISION) FROM Revision WHERE IMEI = ?"), eq(Integer.class), eq(IMEI))).thenReturn(9);
        when(jdbc.queryForObject(eq("SELECT ESTADO FROM Telefono WHERE IMEI = ?"), eq(String.class), eq(IMEI))).thenReturn("EN_REVISION");
        RevisionFuncional f = new RevisionFuncional(78, false, true, false, false, false,
                false, false, false, false, true, "pantalla", false, "obs");
        dao.guardarFuncional(IMEI, f, 5);
        verify(jdbc).update(contains("UPDATE Revision SET FUN_BATERIA_PCT = ?"),
                eq(78), eq(false), eq(true), eq(false), eq(false), eq(false), eq(false), eq(false),
                eq(false), eq(false), eq(true), eq("pantalla"), eq(false), eq("obs"), eq(5), eq(9));
    }

    @Test void getVigenteDevuelveNullSinFilas() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        RevisionDAO dao = new RevisionDAO(jdbc);
        when(jdbc.query(contains("FROM Revision r"), any(RowMapper.class), eq(IMEI))).thenReturn(List.of());
        assertNull(dao.getVigente(IMEI));
    }

    @Test void bloquearPorRevisionSoloDesdeEnRevision() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        RevisionDAO dao = new RevisionDAO(jdbc);
        when(jdbc.update("UPDATE Telefono SET ESTADO = 'BLOQUEADO' WHERE IMEI = ? AND ESTADO = 'EN_REVISION'", IMEI)).thenReturn(1);
        assertTrue(dao.bloquearPorRevision(IMEI));
        when(jdbc.update("UPDATE Telefono SET ESTADO = 'BLOQUEADO' WHERE IMEI = ? AND ESTADO = 'EN_REVISION'", IMEI)).thenReturn(0);
        assertFalse(dao.bloquearPorRevision(IMEI));
    }
}
