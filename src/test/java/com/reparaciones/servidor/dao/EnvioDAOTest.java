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
}
