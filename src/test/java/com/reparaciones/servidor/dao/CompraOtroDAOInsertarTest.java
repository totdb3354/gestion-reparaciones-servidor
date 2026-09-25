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

/** El lote de otros pedidos devuelve los ids creados (spec 4b §4.4). */
class CompraOtroDAOInsertarTest {

    @Test void insertaYDevuelveElIdGenerado() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.update(any(PreparedStatementCreator.class), any(KeyHolder.class))).thenAnswer(inv -> {
            KeyHolder claves = inv.getArgument(1);
            claves.getKeyList().add(Map.of("insert_id", 51));
            return 1;
        });

        int id = new CompraOtroDAO(jdbc).insertar(2, "Cinta de embalar", 3, false, 1.5, "EUR", 1.5);

        assertEquals(51, id);
        ArgumentCaptor<PreparedStatementCreator> sentencia = ArgumentCaptor.forClass(PreparedStatementCreator.class);
        verify(jdbc).update(sentencia.capture(), any(KeyHolder.class));
        Connection con = mock(Connection.class);
        PreparedStatement ps = mock(PreparedStatement.class);
        when(con.prepareStatement(contains("INSERT INTO Compra_otro"), eq(Statement.RETURN_GENERATED_KEYS))).thenReturn(ps);
        sentencia.getValue().createPreparedStatement(con);
        verify(ps).setInt(1, 2);
        verify(ps).setString(2, "Cinta de embalar");
        verify(ps).setInt(3, 3);
        verify(ps).setBoolean(4, false);
        verify(ps).setDouble(5, 1.5);
        verify(ps).setString(6, "EUR");
        verify(ps).setDouble(7, 1.5);
    }
}
