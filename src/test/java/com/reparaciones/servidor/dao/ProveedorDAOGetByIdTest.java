package com.reparaciones.servidor.dao;

import com.reparaciones.servidor.model.Proveedor;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Lectura de un proveedor por id para validar pedidos y resolver su divisa (spec 4b §4.2 y §4.4). */
class ProveedorDAOGetByIdTest {

    @Test void devuelveElProveedorConSuDivisaYSuEstado() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        ResultSet rs = mock(ResultSet.class);
        when(rs.getInt("ID_PROV")).thenReturn(3);
        when(rs.getString("NOMBRE")).thenReturn("Proveedor B");
        when(rs.getBoolean("ACTIVO")).thenReturn(false);
        when(rs.getString("DIVISA")).thenReturn("USD");
        when(rs.getString("TIPO")).thenReturn("COMPONENTES");
        when(jdbc.query(eq("SELECT ID_PROV, NOMBRE, ACTIVO, DIVISA, COMENTARIO, TIPO FROM Proveedor WHERE ID_PROV = ?"),
                any(RowMapper.class), eq(3)))
                .thenAnswer(inv -> List.of(((RowMapper<?>) inv.getArgument(1)).mapRow(rs, 0)));

        Proveedor p = new ProveedorDAO(jdbc).getById(3).orElseThrow();
        assertEquals(3, p.getIdProv());
        assertEquals("Proveedor B", p.getNombre());
        assertFalse(p.isActivo());
        assertEquals("USD", p.getDivisa());
    }

    @Test void unIdInexistenteDevuelveVacio() {
        assertTrue(new ProveedorDAO(mock(JdbcTemplate.class)).getById(99).isEmpty());
    }
}
