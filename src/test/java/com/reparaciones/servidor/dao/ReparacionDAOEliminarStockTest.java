package com.reparaciones.servidor.dao;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.*;

/** 0.9.22.2: borrar un trabajo con DELETE /api/reparaciones/{idRep} devuelve al stock solo lo que gastó. Las filas de una
 *  asignación (A…, AG…, AP…) son solicitudes de pieza y nunca gastaron stock: lo gasta la R…/G…/P… que se guarda desde ella, y
 *  «agotar» lo descuenta como corrección del stock que no había. Antes se devolvía la cantidad de cada solicitud (visto el 11/10 en
 *  la limpieza de una E2E). */
class ReparacionDAOEliminarStockTest {

    private static final String IMEI = "351111112222333";
    private static final String SQL_FILAS = "SELECT ID_COM, ES_REUTILIZADO, CANTIDAD FROM Reparacion_componente WHERE ID_REP = ?";
    private static final String DEVOLVER = "UPDATE Componente SET STOCK = STOCK + ? WHERE ID_COM = ?";

    private static ReparacionDAO dao(JdbcTemplate jdbc) {
        return new ReparacionDAO(jdbc, mock(BorradorDAO.class), mock(MovimientoDAO.class));
    }

    /** Un trabajo que existe, sin incidencia que revertir, con una fila de la pieza 7 (master 70) y 2 unidades. */
    @SuppressWarnings("unchecked")
    private static JdbcTemplate conUnaFila(String idRep, boolean reutilizada) {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.query(eq(SQL_FILAS), any(RowMapper.class), eq(idRep))).thenAnswer(inv -> {
            RowMapper<?> mapper = inv.getArgument(1);
            ResultSet rs = mock(ResultSet.class);
            when(rs.getInt("ID_COM")).thenReturn(7);
            when(rs.getBoolean("ES_REUTILIZADO")).thenReturn(reutilizada);
            when(rs.getInt("CANTIDAD")).thenReturn(2);
            return List.of(mapper.mapRow(rs, 0));
        });
        when(jdbc.query(eq("SELECT IMEI FROM Reparacion WHERE ID_REP = ?"), any(RowMapper.class), eq(idRep)))
                .thenAnswer(inv -> Collections.singletonList(IMEI));
        when(jdbc.query(eq("SELECT ID_REP_ANTERIOR FROM Reparacion WHERE ID_REP = ? AND ID_REP_ANTERIOR IS NOT NULL"),
                any(RowMapper.class), eq(idRep)))
                .thenReturn(Collections.emptyList());
        when(jdbc.queryForObject(startsWith("SELECT COALESCE(ID_COM_MASTER, ID_COM)"), eq(Integer.class), eq(7))).thenReturn(70);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq(IMEI))).thenReturn(1);
        return jdbc;
    }

    @ParameterizedTest
    @ValueSource(strings = {"A20261103_1", "AG20261103_1", "AP20261103_1"})
    void unaAsignacionNoDevuelveStockYSeBorraIgual(String idAsig) {
        JdbcTemplate jdbc = conUnaFila(idAsig, false);

        dao(jdbc).eliminar(idAsig);

        verify(jdbc, never()).update(eq(DEVOLVER), anyInt(), anyInt());
        verify(jdbc).update("DELETE FROM Reparacion_componente WHERE ID_REP = ?", idAsig);
        verify(jdbc).update("DELETE FROM Reparacion WHERE ID_REP = ?", idAsig);
    }

    @ParameterizedTest
    @ValueSource(strings = {"R20261103_1", "G20261103_1", "P20261103_1"})
    void unTrabajoDevuelveLoQueGastoASuMaster(String idRep) {
        JdbcTemplate jdbc = conUnaFila(idRep, false);

        dao(jdbc).eliminar(idRep);

        verify(jdbc).update(DEVOLVER, 2, 70);
        verify(jdbc).update("DELETE FROM Reparacion WHERE ID_REP = ?", idRep);
    }

    @Test
    void unaPiezaReutilizadaNoDevuelveNada() {
        JdbcTemplate jdbc = conUnaFila("R20261103_2", true);

        dao(jdbc).eliminar("R20261103_2");

        verify(jdbc, never()).update(eq(DEVOLVER), anyInt(), anyInt());
    }
}
