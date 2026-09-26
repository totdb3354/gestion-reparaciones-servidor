package com.reparaciones.servidor.dao;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** Comprobación de datos asociados antes de borrar un técnico (spec 6 §4.2, G8): cada una de las nueve columnas con FK
 *  hacia el técnico o su usuario basta para responder true; sin ninguna, false. Un queryForObject sin stub devuelve
 *  null (= no existe). JdbcTemplate mockeado, sin Spring. */
class UsuarioDAOReferenciasTest {

    private static final int ID_TEC = 7;
    private static final int ID_USU = 20;
    private static final String SQL_ID_USU = "SELECT ID_USU FROM Usuario WHERE ID_TEC = ?";

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final UsuarioDAO dao = new UsuarioDAO(jdbc, mock(PasswordEncoder.class));

    UsuarioDAOReferenciasTest() {
        when(jdbc.queryForList(SQL_ID_USU, Integer.class, ID_TEC)).thenReturn(List.of(ID_USU));
    }

    private void existe(String sql, int id) {
        when(jdbc.queryForObject(sql, Integer.class, id)).thenReturn(1);
    }

    // ── las tres columnas hacia Tecnico.ID_TEC ──
    @Test void reparacionPropiaCuenta() {
        existe("SELECT EXISTS(SELECT 1 FROM Reparacion WHERE ID_TEC = ?)", ID_TEC);
        assertTrue(dao.tieneReferencias(ID_TEC));
    }

    @Test void haberAsignadoCuenta() {
        existe("SELECT EXISTS(SELECT 1 FROM Reparacion WHERE ID_TEC_ASIGNA = ?)", ID_TEC);
        assertTrue(dao.tieneReferencias(ID_TEC));
    }

    @Test void haberEntregadoGlassCuenta() {
        existe("SELECT EXISTS(SELECT 1 FROM Reparacion WHERE ENTREGADO_POR = ?)", ID_TEC);
        assertTrue(dao.tieneReferencias(ID_TEC));
    }

    // ── las seis columnas hacia Usuario.ID_USU (con el idUsu resuelto desde idTec) ──
    @Test void revisionEsteticaCuenta() {
        existe("SELECT EXISTS(SELECT 1 FROM Revision WHERE EST_ID_USU = ?)", ID_USU);
        assertTrue(dao.tieneReferencias(ID_TEC));
    }

    @Test void revisionFuncionalCuenta() {
        existe("SELECT EXISTS(SELECT 1 FROM Revision WHERE FUN_ID_USU = ?)", ID_USU);
        assertTrue(dao.tieneReferencias(ID_TEC));
    }

    @Test void envioCuenta() {
        existe("SELECT EXISTS(SELECT 1 FROM Envio WHERE ID_USU = ?)", ID_USU);
        assertTrue(dao.tieneReferencias(ID_TEC));
    }

    @Test void devolucionDeEnvioCuenta() {
        existe("SELECT EXISTS(SELECT 1 FROM Envio_Telefono WHERE ID_USU_DEVOLUCION = ?)", ID_USU);
        assertTrue(dao.tieneReferencias(ID_TEC));
    }

    @Test void solicitudDeStockCuenta() {
        existe("SELECT EXISTS(SELECT 1 FROM Solicitud_Stock WHERE ID_USU = ?)", ID_USU);
        assertTrue(dao.tieneReferencias(ID_TEC));
    }

    @Test void movimientoDeTelefonoCuenta() {
        existe("SELECT EXISTS(SELECT 1 FROM Movimiento_telefono WHERE ID_USU = ?)", ID_USU);
        assertTrue(dao.tieneReferencias(ID_TEC));
    }

    // ── ninguna ──
    @Test void sinNingunaReferenciaEsFalseYMiraLasNueve() {
        assertFalse(dao.tieneReferencias(ID_TEC));
        for (String sql : UsuarioDAO.REFERENCIAS_TECNICO) verify(jdbc).queryForObject(sql, Integer.class, ID_TEC);
        for (String sql : UsuarioDAO.REFERENCIAS_USUARIO) verify(jdbc).queryForObject(sql, Integer.class, ID_USU);
        assertEquals(3, UsuarioDAO.REFERENCIAS_TECNICO.size());
        assertEquals(6, UsuarioDAO.REFERENCIAS_USUARIO.size());
    }

    /** Un técnico sin fila en Usuario solo se mira por sus columnas de Tecnico. */
    @Test void tecnicoSinUsuarioSoloMiraReparacion() {
        when(jdbc.queryForList(SQL_ID_USU, Integer.class, ID_TEC)).thenReturn(List.of());
        assertFalse(dao.tieneReferencias(ID_TEC));
        verify(jdbc, times(3)).queryForObject(anyString(), eq(Integer.class), anyInt());
    }

    /** tieneReparaciones (lo que llama el JavaFX) delega: un supertécnico que solo ha asignado ya no pasa. */
    @Test void tieneReparacionesDelegaEnTieneReferencias() {
        existe("SELECT EXISTS(SELECT 1 FROM Reparacion WHERE ID_TEC_ASIGNA = ?)", ID_TEC);
        assertTrue(dao.tieneReparaciones(ID_TEC));
    }

    // ── lecturas auxiliares ──
    @Test void existeTecnicoCuentaPorId() {
        when(jdbc.queryForObject("SELECT COUNT(*) FROM Tecnico WHERE ID_TEC = ?", Integer.class, ID_TEC)).thenReturn(1);
        assertTrue(dao.existeTecnico(ID_TEC));
        assertFalse(dao.existeTecnico(99));
    }

    @Test void getIdUsuByIdTecDevuelveElUsuarioONull() {
        assertEquals(Integer.valueOf(ID_USU), dao.getIdUsuByIdTec(ID_TEC));
        assertNull(dao.getIdUsuByIdTec(99));
    }
}
