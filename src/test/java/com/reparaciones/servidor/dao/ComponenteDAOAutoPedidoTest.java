package com.reparaciones.servidor.dao;

import com.reparaciones.servidor.model.Componente;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Marca del pedido automático (spec 0.9.6 §4.2): en el master del grupo y sin tocar UPDATED_AT. */
class ComponenteDAOAutoPedidoTest {

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final ComponenteDAO dao = new ComponenteDAO(jdbc);

    @Test void laMarcaDeUnSlaveSeGuardaEnSuMasterSinTocarUpdatedAt() {
        when(jdbc.queryForObject(contains("COALESCE(ID_COM_MASTER, ID_COM)"), eq(Integer.class), eq(12))).thenReturn(3);
        assertEquals(3, dao.setAutoPedido(12, true));
        verify(jdbc).update("UPDATE Componente SET AUTO_PEDIDO = ?, UPDATED_AT = UPDATED_AT WHERE ID_COM = ?", true, 3);
    }

    @Test void unIdInexistenteNoActualizaNada() {
        when(jdbc.queryForObject(contains("COALESCE(ID_COM_MASTER, ID_COM)"), eq(Integer.class), eq(99)))
                .thenThrow(new EmptyResultDataAccessException(1));
        assertThrows(EmptyResultDataAccessException.class, () -> dao.setAutoPedido(99, true));
        // update(String, Object...) es la llamada real: any(Object[].class) casa con el varargs entero.
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    @Test @SuppressWarnings("unchecked")
    void elListadoDeStockTraeLaMarcaDelMaster() throws Exception {
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<RowMapper<Componente>> mapper = ArgumentCaptor.forClass(RowMapper.class);
        when(jdbc.query(sql.capture(), mapper.capture())).thenReturn(List.of());
        dao.getAllGestionados();
        assertTrue(sql.getValue().contains("COALESCE(master.AUTO_PEDIDO, c.AUTO_PEDIDO) AS AUTO_PEDIDO"));

        ResultSet rs = mock(ResultSet.class);
        Timestamp t = Timestamp.valueOf(LocalDateTime.of(2026, 10, 8, 10, 0));
        when(rs.getInt("ID_COM")).thenReturn(5);
        when(rs.getString("TIPO")).thenReturn("bat-y");
        when(rs.getTimestamp("FECHA_REGISTRO")).thenReturn(t);
        when(rs.getTimestamp("UPDATED_AT")).thenReturn(t);
        when(rs.getBoolean("ACTIVO")).thenReturn(true);
        when(rs.getBoolean("AUTO_PEDIDO")).thenReturn(true);
        when(rs.wasNull()).thenReturn(true);
        assertEquals(Boolean.TRUE, mapper.getValue().mapRow(rs, 0).getAutoPedido());
    }
}
