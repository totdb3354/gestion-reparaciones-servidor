package com.reparaciones.servidor.dao;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.support.KeyHolder;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** El lote de pedidos devuelve los ids creados (spec 4b §4.4): insertar pasa a devolver la clave generada. */
class CompraComponenteDAOInsertarTest {

    @Test void insertaEnElMasterYDevuelveElIdGenerado() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(contains("COALESCE(ID_COM_MASTER, ID_COM)"), eq(Integer.class), eq(12))).thenReturn(3);
        when(jdbc.update(any(PreparedStatementCreator.class), any(KeyHolder.class))).thenAnswer(inv -> {
            KeyHolder claves = inv.getArgument(1);
            claves.getKeyList().add(Map.of("insert_id", 41));
            return 1;
        });

        int id = new CompraComponenteDAO(jdbc).insertar(12, 2, 3, true, 10.0, "USD", 8.8);

        assertEquals(41, id);
        ArgumentCaptor<PreparedStatementCreator> sentencia = ArgumentCaptor.forClass(PreparedStatementCreator.class);
        verify(jdbc).update(sentencia.capture(), any(KeyHolder.class));
        Connection con = mock(Connection.class);
        PreparedStatement ps = mock(PreparedStatement.class);
        when(con.prepareStatement(contains("INSERT INTO Compra_componente"), eq(Statement.RETURN_GENERATED_KEYS)))
                .thenReturn(ps);
        sentencia.getValue().createPreparedStatement(con);
        verify(ps).setInt(1, 3);            // el master, no el slave 12
        verify(ps).setInt(2, 2);
        verify(ps).setInt(3, 3);
        verify(ps).setBoolean(4, true);
        verify(ps).setDouble(5, 10.0);
        verify(ps).setString(6, "USD");
        verify(ps).setDouble(7, 8.8);
    }
}
