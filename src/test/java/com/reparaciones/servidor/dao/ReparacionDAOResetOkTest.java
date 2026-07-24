package com.reparaciones.servidor.dao;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Hook "quitar OK al asignar" (F2b): al dar de alta una asignación se apaga el check
 * logístico antiguo y, si el teléfono estaba en OK, se reabre el ciclo a EN_REVISION.
 */
class ReparacionDAOResetOkTest {

    private static final String IMEI = "351111112222333";

    private JdbcTemplate jdbcConStubsDeAlta() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        // nextId: FOR UPDATE sobre el MAX del correlativo del día.
        when(jdbc.queryForObject(contains("COALESCE(MAX"), eq(Integer.class), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(0);
        // tieneAbiertaUrgente: sin asignaciones urgentes abiertas.
        when(jdbc.queryForObject(contains("FECHA_FIN IS NULL AND URGENTE = TRUE"), eq(Integer.class), eq(IMEI)))
                .thenReturn(0);
        return jdbc;
    }

    @Test void insertarAsignacionResetaRevisionLogisticaYQuitaOk() {
        JdbcTemplate jdbc = jdbcConStubsDeAlta();
        ReparacionDAO dao = new ReparacionDAO(jdbc, mock(BorradorDAO.class));

        dao.insertarAsignacion(IMEI, 1, "comentario", false, false, null);

        verify(jdbc).update("UPDATE Telefono SET REVISION_LOGISTICA = 0 WHERE IMEI = ?", IMEI);
        verify(jdbc).update("UPDATE Telefono SET ESTADO = 'EN_REVISION' WHERE IMEI = ? AND ESTADO = 'OK'", IMEI);
    }

    @Test void insertarAsignacionGlassResetaRevisionLogisticaYQuitaOk() {
        JdbcTemplate jdbc = jdbcConStubsDeAlta();
        ReparacionDAO dao = new ReparacionDAO(jdbc, mock(BorradorDAO.class));

        dao.insertarAsignacionGlass(IMEI, 1, "comentario", false, null);

        verify(jdbc).update("UPDATE Telefono SET REVISION_LOGISTICA = 0 WHERE IMEI = ?", IMEI);
        verify(jdbc).update("UPDATE Telefono SET ESTADO = 'EN_REVISION' WHERE IMEI = ? AND ESTADO = 'OK'", IMEI);
    }

    @Test void insertarAsignacionPulidoResetaRevisionLogisticaYQuitaOk() {
        JdbcTemplate jdbc = jdbcConStubsDeAlta();
        ReparacionDAO dao = new ReparacionDAO(jdbc, mock(BorradorDAO.class));

        dao.insertarAsignacionPulido(IMEI, 1, "comentario", null);

        verify(jdbc).update("UPDATE Telefono SET REVISION_LOGISTICA = 0 WHERE IMEI = ?", IMEI);
        verify(jdbc).update("UPDATE Telefono SET ESTADO = 'EN_REVISION' WHERE IMEI = ? AND ESTADO = 'OK'", IMEI);
    }
}
