package com.reparaciones.servidor.dao;

import com.reparaciones.servidor.model.Revision;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class RevisionDAOEstadoTest {

    private static final String IMEI = "351111112222333";

    /** Revision vigente completa (dos partes) con la batería indicada, servida vía el SELECT de getVigente. */
    private JdbcTemplate conVigente(Integer bateria, boolean ambasPartes, int abiertos) {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        Revision r = new Revision();
        r.setEstFecha(ambasPartes ? LocalDateTime.now() : null);
        r.setFunFecha(ambasPartes ? LocalDateTime.now() : null);
        r.setFunBateriaPct(bateria);
        when(jdbc.query(contains("FROM Revision r"), any(RowMapper.class), eq(IMEI))).thenReturn(List.of(r));
        when(jdbc.queryForObject(contains("COUNT(*) FROM Reparacion"), eq(Integer.class), eq(IMEI))).thenReturn(abiertos);
        return jdbc;
    }

    @Test void okConTodoEnReglaCambiaEstado() {
        JdbcTemplate jdbc = conVigente(92, true, 0);
        when(jdbc.update("UPDATE Telefono SET ESTADO = 'OK' WHERE IMEI = ? AND ESTADO = 'EN_REVISION'", IMEI)).thenReturn(1);
        new RevisionDAO(jdbc, mock(MovimientoDAO.class)).marcarOk(IMEI, 7);
        verify(jdbc).update("UPDATE Telefono SET ESTADO = 'OK' WHERE IMEI = ? AND ESTADO = 'EN_REVISION'", IMEI);
    }

    @Test void okEscribeMovimientoAListosOPedidos() {
        JdbcTemplate jdbc = conVigente(92, true, 0);
        when(jdbc.update("UPDATE Telefono SET ESTADO = 'OK' WHERE IMEI = ? AND ESTADO = 'EN_REVISION'", IMEI)).thenReturn(1);
        when(jdbc.query(contains("SELECT ID_CLI FROM Telefono"), any(RowMapper.class), eq(IMEI)))
                .thenReturn(java.util.Collections.singletonList((Integer) null));
        MovimientoDAO mov = mock(MovimientoDAO.class);
        new RevisionDAO(jdbc, mov).marcarOk(IMEI, 7);
        verify(mov).registrar(IMEI, "PARA_REVISAR", "LISTOS", 7, null, null);
    }

    @Test void okVetadoConBateriaBaja() {
        RevisionDAO dao = new RevisionDAO(conVigente(78, true, 0), mock(MovimientoDAO.class));
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> dao.marcarOk(IMEI, 7));
        assertTrue(ex.getReason().contains("Batería"));
    }

    @Test void okVetadoConBateriaNull() {
        assertThrows(ResponseStatusException.class,
                () -> new RevisionDAO(conVigente(null, true, 0), mock(MovimientoDAO.class)).marcarOk(IMEI, 7));
    }

    @Test void okVetadoConRevisionIncompleta() {
        assertThrows(ResponseStatusException.class,
                () -> new RevisionDAO(conVigente(92, false, 0), mock(MovimientoDAO.class)).marcarOk(IMEI, 7));
    }

    @Test void okVetadoConTrabajosAbiertos() {
        assertThrows(ResponseStatusException.class,
                () -> new RevisionDAO(conVigente(92, true, 2), mock(MovimientoDAO.class)).marcarOk(IMEI, 7));
    }

    @Test void desbloquearVuelveAEnRevision() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.update("UPDATE Telefono SET ESTADO = 'EN_REVISION' WHERE IMEI = ? AND ESTADO = 'BLOQUEADO'", IMEI)).thenReturn(1);
        new RevisionDAO(jdbc, mock(MovimientoDAO.class)).desbloquear(IMEI, 7);
        verify(jdbc).update("UPDATE Telefono SET ESTADO = 'EN_REVISION' WHERE IMEI = ? AND ESTADO = 'BLOQUEADO'", IMEI);
    }

    @Test void bloquearEscribeMovimientoConMotivo() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.update("UPDATE Telefono SET ESTADO = 'BLOQUEADO' WHERE IMEI = ? AND ESTADO = 'EN_REVISION'", IMEI)).thenReturn(1);
        MovimientoDAO mov = mock(MovimientoDAO.class);
        new RevisionDAO(jdbc, mov).bloquear(IMEI, 7, "MS externa");
        verify(mov).registrar(IMEI, "PARA_REVISAR", "BLOQUEO", 7, "MS externa", null);
    }

    @Test void desguaceDesdeRevisionOBloqueo() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.update("UPDATE Telefono SET ESTADO = 'DESGUACE' WHERE IMEI = ? AND ESTADO IN ('EN_REVISION','BLOQUEADO')", IMEI)).thenReturn(1);
        new RevisionDAO(jdbc, mock(MovimientoDAO.class)).desguace(IMEI, 7, "motivo test");
        verify(jdbc).update("UPDATE Telefono SET ESTADO = 'DESGUACE' WHERE IMEI = ? AND ESTADO IN ('EN_REVISION','BLOQUEADO')", IMEI);
    }

    @Test void desguaceEscribeMovimientoDesdeEstadoPrevio() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.query(contains("SELECT ESTADO FROM Telefono"), any(RowMapper.class), eq(IMEI)))
                .thenReturn(java.util.Collections.singletonList("BLOQUEADO"));
        when(jdbc.update("UPDATE Telefono SET ESTADO = 'DESGUACE' WHERE IMEI = ? AND ESTADO IN ('EN_REVISION','BLOQUEADO')", IMEI)).thenReturn(1);
        MovimientoDAO mov = mock(MovimientoDAO.class);
        new RevisionDAO(jdbc, mov).desguace(IMEI, 7, "placa muerta");
        verify(mov).registrar(IMEI, "BLOQUEO", "DESGUACE", 7, "placa muerta", null);
    }

    @Test void transicionSinFilasEs409() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.update(anyString(), eq(IMEI))).thenReturn(0);
        assertThrows(ResponseStatusException.class,
                () -> new RevisionDAO(jdbc, mock(MovimientoDAO.class)).desbloquear(IMEI, 7));
    }
}
