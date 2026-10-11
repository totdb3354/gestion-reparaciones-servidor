package com.reparaciones.servidor.dao;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** Spec 0.9.8 §5: un cliente con teléfonos o con trabajos que lo guardaron no se puede borrar, solo desactivar. */
class ClienteDAOTieneTelefonosTest {

    @Test void cuentaTelefonosYTrabajosConElClienteGuardado() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq(5), eq(5))).thenReturn(0, 2);
        ClienteDAO dao = new ClienteDAO(jdbc);
        assertFalse(dao.tieneTelefonos(5));
        assertTrue(dao.tieneTelefonos(5));
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc, times(2)).queryForObject(sql.capture(), eq(Integer.class), eq(5), eq(5));
        assertTrue(sql.getValue().contains("FROM Telefono WHERE ID_CLI = ?"), sql.getValue());
        assertTrue(sql.getValue().contains("FROM Reparacion WHERE ID_CLI = ?"), sql.getValue());
    }
}
