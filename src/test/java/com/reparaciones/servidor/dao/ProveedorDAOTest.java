package com.reparaciones.servidor.dao;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class ProveedorDAOTest {

    @Test void insertarSinDivisaNiTipoAplicaDefaults() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        new ProveedorDAO(jdbc).insertar("ACME", null, null);
        verify(jdbc).update("INSERT INTO Proveedor (NOMBRE, ACTIVO, DIVISA, TIPO) VALUES (?, 1, ?, ?)",
                "ACME", "EUR", "COMPONENTES");
    }

    @Test void insertarConDivisaYTipoLosRespeta() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        new ProveedorDAO(jdbc).insertar("Hy5", "USD", "TELEFONOS");
        verify(jdbc).update(anyString(), eq("Hy5"), eq("USD"), eq("TELEFONOS"));
    }

    @Test void getActivosConTipoFiltraYSinTipoNo() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        ProveedorDAO dao = new ProveedorDAO(jdbc);
        dao.getActivos("TELEFONOS");
        verify(jdbc).query(contains("ACTIVO = 1 AND TIPO = ?"), any(RowMapper.class), eq("TELEFONOS"));
        dao.getActivos(null);
        verify(jdbc).query(contains("ACTIVO = 1 ORDER BY"), any(RowMapper.class));
    }

    @Test void getAllConTipoFiltra() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        new ProveedorDAO(jdbc).getAll("COMPONENTES");
        verify(jdbc).query(contains("WHERE TIPO = ?"), any(RowMapper.class), eq("COMPONENTES"));
    }

    @Test void tienePedidosCuentaTambienLotes() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq(7), eq(7), eq(7))).thenReturn(1);
        ProveedorDAO dao = new ProveedorDAO(jdbc);

        boolean result = dao.tienePedidos(7);

        verify(jdbc).queryForObject(contains("FROM Lote"), eq(Integer.class), eq(7), eq(7), eq(7));
        assertTrue(result);
    }

    @Test void tienePedidosSinPedidosNiLotesDevuelveFalse() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq(7), eq(7), eq(7))).thenReturn(null);
        ProveedorDAO dao = new ProveedorDAO(jdbc);

        boolean result = dao.tienePedidos(7);

        assertFalse(result);
    }
}
