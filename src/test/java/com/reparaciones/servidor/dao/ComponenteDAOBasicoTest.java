package com.reparaciones.servidor.dao;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Lectura mínima de un componente para validar pedidos (spec 4b §4.2): existe, está activo y cuál es su master. */
class ComponenteDAOBasicoTest {

    @Test void devuelveIdMasterTipoYActivoLeyendoSusColumnas() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        ResultSet rs = mock(ResultSet.class);
        when(rs.getInt("ID_COM")).thenReturn(12);
        when(rs.getInt("ID_MASTER")).thenReturn(3);
        when(rs.getString("TIPO")).thenReturn("lcd-y-negro");
        when(rs.getBoolean("ACTIVO")).thenReturn(true);
        when(jdbc.query(contains("COALESCE(ID_COM_MASTER, ID_COM) AS ID_MASTER"), any(RowMapper.class), eq(12)))
                .thenAnswer(inv -> List.of(((RowMapper<?>) inv.getArgument(1)).mapRow(rs, 0)));

        assertEquals(Optional.of(new ComponenteDAO.Basico(12, 3, "lcd-y-negro", true)),
                new ComponenteDAO(jdbc).getBasico(12));
    }

    @Test void unIdInexistenteDevuelveVacio() {
        assertEquals(Optional.empty(), new ComponenteDAO(mock(JdbcTemplate.class)).getBasico(99));
    }
}
