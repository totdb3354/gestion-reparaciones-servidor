package com.reparaciones.servidor.dao;

import com.reparaciones.servidor.model.CompraComponente;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.web.server.ResponseStatusException;

import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** GET /api/compras con los datos del bloque de cada línea y el cambio de bloque (0.9.9, spec §5.1). */
@SuppressWarnings({"unchecked", "rawtypes"})
class CompraComponenteDAOBloqueTest {

    private static final LocalDateTime AT = LocalDateTime.of(2026, 10, 9, 10, 0, 0);
    private static final String MOVER = "UPDATE Compra_componente SET ID_BLOQUE=? WHERE ID_COMPRA=? AND ID_BLOQUE <=> ?";
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final CompraComponenteDAO dao = new CompraComponenteDAO(jdbc);

    private RowMapper mapperDeGetAll() {
        dao.getAll();
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<RowMapper> mapper = ArgumentCaptor.forClass(RowMapper.class);
        verify(jdbc).query(sql.capture(), mapper.capture());
        assertTrue(sql.getValue().contains("LEFT JOIN Bloque_compra b ON b.ID_BLOQUE = cc.ID_BLOQUE"));
        assertTrue(sql.getValue().contains("cc.ID_BLOQUE, b.FECHA AS FECHA_BLOQUE, b.NOTA AS NOTA_BLOQUE, b.UPDATED_AT AS UPDATED_AT_BLOQUE, b.RECONSTRUIDO"));
        return mapper.getValue();
    }

    private static ResultSet filaBase() throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getTimestamp("FECHA_PEDIDO")).thenReturn(Timestamp.valueOf(AT));
        when(rs.getTimestamp("UPDATED_AT")).thenReturn(Timestamp.valueOf(AT));
        when(rs.getString("ESTADO")).thenReturn("pendiente");
        return rs;
    }

    @Test void cadaLineaTraeLosDatosDeSuBloque() throws Exception {
        RowMapper mapper = mapperDeGetAll();
        ResultSet rs = filaBase();
        when(rs.getObject("ID_BLOQUE", Integer.class)).thenReturn(41);
        when(rs.getTimestamp("FECHA_BLOQUE")).thenReturn(Timestamp.valueOf(AT));
        when(rs.getString("NOTA_BLOQUE")).thenReturn("Ped. 123");
        when(rs.getTimestamp("UPDATED_AT_BLOQUE")).thenReturn(Timestamp.valueOf(AT));
        when(rs.getBoolean("RECONSTRUIDO")).thenReturn(true);

        CompraComponente c = (CompraComponente) mapper.mapRow(rs, 0);

        assertEquals(41, c.getIdBloque());
        assertEquals(AT, c.getFechaBloque());
        assertEquals("Ped. 123", c.getNotaBloque());
        assertEquals(AT, c.getBloqueUpdatedAt());
        assertTrue(c.isBloqueAntiguo());
    }

    @Test void unaLineaSinBloqueLlegaConLosCamposDelBloqueVacios() throws Exception {
        CompraComponente c = (CompraComponente) mapperDeGetAll().mapRow(filaBase(), 0);
        assertNull(c.getIdBloque());
        assertNull(c.getFechaBloque());
        assertNull(c.getNotaBloque());
        assertNull(c.getBloqueUpdatedAt());
        assertFalse(c.isBloqueAntiguo());
    }

    @Test void moverABloqueCambiaElBloqueDeLaLineaSoloSiSigueEnElOrigenLeido() {
        when(jdbc.update(MOVER, 41, 5, 7)).thenReturn(1);
        dao.moverABloque(5, 7, 41);
        verify(jdbc).update(MOVER, 41, 5, 7);
    }

    @Test void moverUnaLineaSinBloqueUsaOrigenNulo() {
        when(jdbc.update(MOVER, 41, 5, null)).thenReturn(1);
        dao.moverABloque(5, null, 41);
        verify(jdbc).update(MOVER, 41, 5, null);
    }

    @Test void moverUnaLineaQueYaNoExisteEs409() {
        when(jdbc.queryForObject("SELECT COUNT(*) FROM Compra_componente WHERE ID_COMPRA=?", Integer.class, 5)).thenReturn(0);
        ResponseStatusException e = assertThrows(ResponseStatusException.class, () -> dao.moverABloque(5, 7, 41));
        assertEquals(HttpStatus.CONFLICT, e.getStatusCode());
        assertEquals("El pedido ya no existe", e.getReason());
    }

    @Test void moverUnaLineaQueCambioDeBloqueEs409() {
        when(jdbc.queryForObject("SELECT COUNT(*) FROM Compra_componente WHERE ID_COMPRA=?", Integer.class, 5)).thenReturn(1);
        ResponseStatusException e = assertThrows(ResponseStatusException.class, () -> dao.moverABloque(5, 7, 41));
        assertEquals(HttpStatus.CONFLICT, e.getStatusCode());
        assertEquals("El pedido cambió de bloque, vuelve a cargar", e.getReason());
    }

    @Test void borrarPendienteDeUnBloqueComparaElOrigenEnElDelete() {
        String sql = "DELETE FROM Compra_componente WHERE ID_COMPRA=? AND ESTADO='pendiente' AND ID_BLOQUE <=> ?";
        when(jdbc.update(sql, 5, 7)).thenReturn(1);
        dao.borrarPendiente(5, 7);
        verify(jdbc).update(sql, 5, 7);
        ResponseStatusException e = assertThrows(ResponseStatusException.class, () -> dao.borrarPendiente(5, 8));
        assertEquals(HttpStatus.CONFLICT, e.getStatusCode());
    }
}
