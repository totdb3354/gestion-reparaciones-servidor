package com.reparaciones.servidor.dao;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

/**
 * Hook "OK vuelve a revisión al asignar" (F2b/F2c): al dar de alta una asignación,
 * si el teléfono estaba en OK, se reabre el ciclo a EN_REVISION y se escribe el
 * movimiento de trazabilidad (F2c). El check logístico antiguo (REVISION_LOGISTICA)
 * ya NO se toca aquí — eso queda fuera del hook (F2c).
 */
class ReparacionDAOResetOkTest {

    private static final String IMEI = "351111112222333";
    private static final int ID_USU = 9;

    private JdbcTemplate jdbcConStubsDeAlta() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        // nextId: FOR UPDATE sobre el MAX del correlativo del día.
        when(jdbc.queryForObject(contains("COALESCE(MAX"), eq(Integer.class), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(0);
        // tieneAbiertaUrgente: sin asignaciones urgentes abiertas.
        when(jdbc.queryForObject(contains("FECHA_FIN IS NULL AND URGENTE = TRUE"), eq(Integer.class), eq(IMEI)))
                .thenReturn(0);
        // flip OK -> EN_REVISION: ocurre (el teléfono estaba OK).
        when(jdbc.update("UPDATE Telefono SET ESTADO = 'EN_REVISION' WHERE IMEI = ? AND ESTADO = 'OK'", IMEI))
                .thenReturn(1);
        when(jdbc.query(contains("SELECT ID_CLI FROM Telefono"), any(RowMapper.class), eq(IMEI)))
                .thenReturn(java.util.Collections.singletonList((Integer) null));
        return jdbc;
    }

    @Test void insertarAsignacionQuitaOkYEscribeMovimiento() {
        JdbcTemplate jdbc = jdbcConStubsDeAlta();
        MovimientoDAO mov = mock(MovimientoDAO.class);
        ReparacionDAO dao = new ReparacionDAO(jdbc, mock(BorradorDAO.class), mov);

        dao.insertarAsignacion(IMEI, 1, "comentario", false, false, null, ID_USU);

        // ANTES verificaba: UPDATE Telefono SET REVISION_LOGISTICA = 0 ... — ELIMINADO (F2c)
        verify(jdbc).update("UPDATE Telefono SET ESTADO = 'EN_REVISION' WHERE IMEI = ? AND ESTADO = 'OK'", IMEI);
        // Con flip OK->EN_REVISION (update devuelve 1) el hook escribe el movimiento:
        verify(mov).registrar(eq(IMEI), eq("LISTOS"), eq("PARA_REVISAR"), eq(ID_USU), eq("Trabajo asignado"), isNull());
    }

    @Test void insertarAsignacionGlassQuitaOkYEscribeMovimiento() {
        JdbcTemplate jdbc = jdbcConStubsDeAlta();
        MovimientoDAO mov = mock(MovimientoDAO.class);
        ReparacionDAO dao = new ReparacionDAO(jdbc, mock(BorradorDAO.class), mov);

        dao.insertarAsignacionGlass(IMEI, 1, "comentario", false, null, ID_USU);

        verify(jdbc).update("UPDATE Telefono SET ESTADO = 'EN_REVISION' WHERE IMEI = ? AND ESTADO = 'OK'", IMEI);
        verify(mov).registrar(eq(IMEI), eq("LISTOS"), eq("PARA_REVISAR"), eq(ID_USU), eq("Trabajo asignado"), isNull());
    }

    @Test void insertarAsignacionPulidoQuitaOkYEscribeMovimiento() {
        JdbcTemplate jdbc = jdbcConStubsDeAlta();
        MovimientoDAO mov = mock(MovimientoDAO.class);
        ReparacionDAO dao = new ReparacionDAO(jdbc, mock(BorradorDAO.class), mov);

        dao.insertarAsignacionPulido(IMEI, 1, "comentario", null, ID_USU);

        verify(jdbc).update("UPDATE Telefono SET ESTADO = 'EN_REVISION' WHERE IMEI = ? AND ESTADO = 'OK'", IMEI);
        verify(mov).registrar(eq(IMEI), eq("LISTOS"), eq("PARA_REVISAR"), eq(ID_USU), eq("Trabajo asignado"), isNull());
    }

    @Test void flipNoOcurreNoEscribeMovimiento() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(contains("COALESCE(MAX"), eq(Integer.class), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(0);
        when(jdbc.queryForObject(contains("FECHA_FIN IS NULL AND URGENTE = TRUE"), eq(Integer.class), eq(IMEI)))
                .thenReturn(0);
        when(jdbc.update("UPDATE Telefono SET ESTADO = 'EN_REVISION' WHERE IMEI = ? AND ESTADO = 'OK'", IMEI))
                .thenReturn(0);
        MovimientoDAO mov = mock(MovimientoDAO.class);
        ReparacionDAO dao = new ReparacionDAO(jdbc, mock(BorradorDAO.class), mov);

        dao.insertarAsignacion(IMEI, 1, "comentario", false, false, null, ID_USU);

        verify(jdbc).update("UPDATE Telefono SET ESTADO = 'EN_REVISION' WHERE IMEI = ? AND ESTADO = 'OK'", IMEI);
        verifyNoInteractions(mov);
    }
}
