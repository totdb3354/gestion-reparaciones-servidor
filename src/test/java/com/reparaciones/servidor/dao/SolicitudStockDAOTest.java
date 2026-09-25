package com.reparaciones.servidor.dao;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** El lote de pedidos casa cada solicitud preventiva con su línea por el componente (spec 4b §4.4). */
class SolicitudStockDAOTest {

    @Test void devuelveElComponenteDeLaSolicitudONullSiNoExiste() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        ResultSet rs = mock(ResultSet.class);
        when(rs.getObject("ID_COM", Integer.class)).thenReturn(1);
        when(jdbc.query(eq("SELECT ID_COM FROM Solicitud_Stock WHERE ID_SOL = ?"), any(RowMapper.class), eq(21)))
                .thenAnswer(inv -> Collections.singletonList(((RowMapper<?>) inv.getArgument(1)).mapRow(rs, 0)));

        SolicitudStockDAO dao = new SolicitudStockDAO(jdbc);
        assertEquals(Integer.valueOf(1), dao.getIdCom(21));
        assertNull(dao.getIdCom(99));
    }
}
