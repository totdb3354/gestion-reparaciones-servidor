package com.reparaciones.servidor.dao;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class EnvioDAOTest {

    private static final String IMEI = "351111112222333";

    @SuppressWarnings("unchecked")
    private JdbcTemplate conTelefono(String estado, Integer idCli) {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.query(contains("SELECT ESTADO, ID_CLI FROM Telefono"), any(RowMapper.class), eq(IMEI)))
                .thenReturn(Collections.singletonList(new Object[]{ estado, idCli }));
        when(jdbc.update(eq("UPDATE Telefono SET ESTADO = 'ENVIADO', ES_DEVOLUCION = 0 WHERE IMEI = ? AND ESTADO = 'OK'"), eq(IMEI)))
                .thenReturn("OK".equals(estado) ? 1 : 0);
        when(jdbc.queryForObject(eq("SELECT LAST_INSERT_ID()"), eq(Integer.class))).thenReturn(7);
        return jdbc;
    }

    @Test void telefonoOkSeEnviaCreaEnvioYPuente() {
        JdbcTemplate jdbc = conTelefono("OK", null);
        MovimientoDAO mov = mock(MovimientoDAO.class);
        EnvioDAO.ResultadoLote r = new EnvioDAO(jdbc, mov).enviarLote(null, "CashPhone", "ALB-99", List.of(IMEI), 3);
        assertEquals(7, r.idEnvio());
        assertEquals("ENVIADO", r.items().get(0).resultado());
        verify(jdbc).update(eq("INSERT INTO Envio (FECHA, ID_CLI, DESTINO_TEXTO, REFERENCIA, ID_USU) VALUES (NOW(), ?, ?, ?, ?)"),
                isNull(), eq("CashPhone"), eq("ALB-99"), eq(3));
        verify(jdbc).update("INSERT INTO Envio_Telefono (ID_ENVIO, IMEI) VALUES (?, ?)", 7, IMEI);
        verify(mov).registrar(IMEI, "LISTOS", "ENVIADO", 3, null, "ENVIO 7");
    }

    @Test void telefonoOkConClienteSaleDePedidos() {
        JdbcTemplate jdbc = conTelefono("OK", 5);
        MovimientoDAO mov = mock(MovimientoDAO.class);
        new EnvioDAO(jdbc, mov).enviarLote(null, "CashPhone", null, List.of(IMEI), 3);
        verify(mov).registrar(IMEI, "PEDIDOS", "ENVIADO", 3, null, "ENVIO 7");
    }

    @Test void noOkSeRechazaConSuEstadoYNoCreaEnvio() {
        JdbcTemplate jdbc = conTelefono("EN_REVISION", null);
        EnvioDAO.ResultadoLote r = new EnvioDAO(jdbc, mock(MovimientoDAO.class)).enviarLote(null, "X", null, List.of(IMEI), 3);
        assertNull(r.idEnvio());
        assertEquals("NO_OK", r.items().get(0).resultado());
        assertEquals("EN_REVISION", r.items().get(0).estado());
        verify(jdbc, never()).update(contains("INSERT INTO Envio"), any(), any(), any(), any());
    }

    @Test void historicoYNoExistente() {
        assertEquals("HISTORICO", new EnvioDAO(conTelefono(null, null), mock(MovimientoDAO.class))
                .enviarLote(null, "X", null, List.of(IMEI), 3).items().get(0).resultado());
        JdbcTemplate sin = mock(JdbcTemplate.class);
        when(sin.query(contains("SELECT ESTADO, ID_CLI FROM Telefono"), any(RowMapper.class), eq(IMEI)))
                .thenReturn(List.of());
        assertEquals("NO_EXISTE", new EnvioDAO(sin, mock(MovimientoDAO.class))
                .enviarLote(null, "X", null, List.of(IMEI), 3).items().get(0).resultado());
    }

    @Test void imeiDuplicadoSeDeduplicaEnvioPuente() {
        JdbcTemplate jdbc = conTelefono("OK", null);
        MovimientoDAO mov = mock(MovimientoDAO.class);
        EnvioDAO.ResultadoLote r = new EnvioDAO(jdbc, mov).enviarLote(null, "X", null, List.of(IMEI, IMEI), 3);
        assertEquals(1, r.items().size());
        assertEquals("ENVIADO", r.items().get(0).resultado());
        verify(jdbc, times(1)).update("INSERT INTO Envio_Telefono (ID_ENVIO, IMEI) VALUES (?, ?)", 7, IMEI);
    }

    @Test void devolucionMarcaPuenteYVuelveAlAlmacen() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.update("UPDATE Telefono SET ESTADO = 'RECIBIDO', ES_DEVOLUCION = 1 WHERE IMEI = ? AND ESTADO = 'ENVIADO'", IMEI)).thenReturn(1);
        when(jdbc.query(contains("FROM Envio_Telefono"), any(RowMapper.class), eq(IMEI)))
                .thenReturn(Collections.singletonList(new Object[]{ 42, 9 }));   // ID_ET, ID_ENVIO
        MovimientoDAO mov = mock(MovimientoDAO.class);
        EnvioDAO.ItemDevolucion r = new EnvioDAO(jdbc, mov).devolver(IMEI, "pantalla amarilla", 3);
        assertEquals("DEVUELTO", r.resultado());
        assertEquals(9, r.envio());
        verify(jdbc).update("UPDATE Envio_Telefono SET DEVUELTO = 1, MOTIVO_DEVOLUCION = ?, FECHA_DEVOLUCION = NOW(), ID_USU_DEVOLUCION = ? WHERE ID_ET = ?",
                "pantalla amarilla", 3, 42);
        verify(mov).registrar(IMEI, "ENVIADO", "ALMACEN", 3, "pantalla amarilla", "ENVIO 9");
    }

    @Test void devolucionSinPuenteActivaSeProcesaConEnvioVacio() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.update("UPDATE Telefono SET ESTADO = 'RECIBIDO', ES_DEVOLUCION = 1 WHERE IMEI = ? AND ESTADO = 'ENVIADO'", IMEI)).thenReturn(1);
        when(jdbc.query(contains("FROM Envio_Telefono"), any(RowMapper.class), eq(IMEI))).thenReturn(List.of());
        MovimientoDAO mov = mock(MovimientoDAO.class);
        EnvioDAO.ItemDevolucion r = new EnvioDAO(jdbc, mov).devolver(IMEI, "sin caja", 3);
        assertEquals("DEVUELTO", r.resultado());
        assertNull(r.envio());
        verify(mov).registrar(IMEI, "ENVIADO", "ALMACEN", 3, "sin caja", null);
    }

    @Test void noEnviadoYNoExistente() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.update(contains("SET ESTADO = 'RECIBIDO'"), eq(IMEI))).thenReturn(0);
        when(jdbc.query(contains("SELECT ESTADO, ID_CLI FROM Telefono"), any(RowMapper.class), eq(IMEI)))
                .thenReturn(Collections.singletonList(new Object[]{ "OK", null }));
        assertEquals("NO_ENVIADO", new EnvioDAO(jdbc, mock(MovimientoDAO.class)).devolver(IMEI, "x", 3).resultado());
        JdbcTemplate sin = mock(JdbcTemplate.class);
        when(sin.update(contains("SET ESTADO = 'RECIBIDO'"), eq(IMEI))).thenReturn(0);
        when(sin.query(contains("SELECT ESTADO, ID_CLI FROM Telefono"), any(RowMapper.class), eq(IMEI))).thenReturn(List.of());
        assertEquals("NO_EXISTE", new EnvioDAO(sin, mock(MovimientoDAO.class)).devolver(IMEI, "x", 3).resultado());
    }
}
