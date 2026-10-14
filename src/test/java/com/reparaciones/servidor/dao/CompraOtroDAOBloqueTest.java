package com.reparaciones.servidor.dao;

import com.reparaciones.servidor.model.CompraOtro;
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

/** Un otro pedido dentro del bloque de su proveedor (0.9.15, spec «Añadir otro» §4): GET /api/compras-otros con los
 *  datos del bloque, el cambio de bloque y el borrado que comprueba el bloque leído (como CompraComponenteDAO). */
@SuppressWarnings({"unchecked", "rawtypes"})
class CompraOtroDAOBloqueTest {

    private static final LocalDateTime AT = LocalDateTime.of(2026, 10, 14, 10, 0, 0);
    private static final String MOVER = "UPDATE Compra_otro SET ID_BLOQUE=? WHERE ID_COMPRA_OTRO=? AND ID_BLOQUE <=> ?";
    private static final String EXISTE = "SELECT COUNT(*) FROM Compra_otro WHERE ID_COMPRA_OTRO=?";
    private static final String BORRAR = "DELETE FROM Compra_otro WHERE ID_COMPRA_OTRO=? AND ESTADO='pendiente' AND ID_BLOQUE <=> ?";
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final CompraOtroDAO dao = new CompraOtroDAO(jdbc);

    private RowMapper mapperDeGetAll() {
        dao.getAll();
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<RowMapper> mapper = ArgumentCaptor.forClass(RowMapper.class);
        verify(jdbc).query(sql.capture(), mapper.capture());
        assertTrue(sql.getValue().contains("LEFT JOIN Bloque_compra b ON b.ID_BLOQUE = co.ID_BLOQUE"));
        assertTrue(sql.getValue().contains("co.ID_BLOQUE, b.FECHA AS FECHA_BLOQUE, b.NOTA AS NOTA_BLOQUE, b.UPDATED_AT AS UPDATED_AT_BLOQUE, b.RECONSTRUIDO"));
        return mapper.getValue();
    }

    private static ResultSet filaBase() throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getInt("ID_COMPRA_OTRO")).thenReturn(88);
        when(rs.getString("CONCEPTO")).thenReturn("Cinta de embalar");
        when(rs.getTimestamp("FECHA_PEDIDO")).thenReturn(Timestamp.valueOf(AT));
        when(rs.getTimestamp("UPDATED_AT")).thenReturn(Timestamp.valueOf(AT.withNano(400_000_000)));
        when(rs.getString("ESTADO")).thenReturn("pendiente");
        return rs;
    }

    @Test void cadaOtroTraeLosDatosDeSuBloque() throws Exception {
        RowMapper mapper = mapperDeGetAll();
        ResultSet rs = filaBase();
        when(rs.getObject("ID_BLOQUE", Integer.class)).thenReturn(41);
        when(rs.getTimestamp("FECHA_BLOQUE")).thenReturn(Timestamp.valueOf(AT));
        when(rs.getString("NOTA_BLOQUE")).thenReturn("Ped. 123");
        when(rs.getTimestamp("UPDATED_AT_BLOQUE")).thenReturn(Timestamp.valueOf(AT));
        when(rs.getBoolean("RECONSTRUIDO")).thenReturn(true);

        CompraOtro o = (CompraOtro) mapper.mapRow(rs, 0);

        assertEquals(88, o.getIdCompraOtro());
        assertEquals("Cinta de embalar", o.getConcepto());
        assertEquals(AT, o.getUpdatedAt());
        assertEquals(41, o.getIdBloque());
        assertEquals(AT, o.getFechaBloque());
        assertEquals("Ped. 123", o.getNotaBloque());
        assertEquals(AT, o.getBloqueUpdatedAt());
        assertTrue(o.isBloqueAntiguo());
    }

    @Test void unOtroSinBloqueLlegaConLosCamposDelBloqueVacios() throws Exception {
        CompraOtro o = (CompraOtro) mapperDeGetAll().mapRow(filaBase(), 0);
        assertNull(o.getIdBloque());
        assertNull(o.getFechaBloque());
        assertNull(o.getNotaBloque());
        assertNull(o.getBloqueUpdatedAt());
        assertFalse(o.isBloqueAntiguo());
    }

    @Test void moverABloqueCambiaElBloqueDelOtroSoloSiSigueEnElOrigenLeido() {
        when(jdbc.update(MOVER, 41, 88, 7)).thenReturn(1);
        dao.moverABloque(88, 7, 41);
        verify(jdbc).update(MOVER, 41, 88, 7);
    }

    @Test void moverUnOtroSinBloqueUsaOrigenNulo() {
        when(jdbc.update(MOVER, 41, 88, null)).thenReturn(1);
        dao.moverABloque(88, null, 41);
        verify(jdbc).update(MOVER, 41, 88, null);
    }

    @Test void moverUnOtroQueYaNoExisteOQueCambioDeBloqueEs409() {
        when(jdbc.queryForObject(EXISTE, Integer.class, 88)).thenReturn(0);
        ResponseStatusException e = assertThrows(ResponseStatusException.class, () -> dao.moverABloque(88, 7, 41));
        assertEquals(HttpStatus.CONFLICT, e.getStatusCode());
        assertEquals("El pedido ya no existe", e.getReason());

        when(jdbc.queryForObject(EXISTE, Integer.class, 88)).thenReturn(1);
        e = assertThrows(ResponseStatusException.class, () -> dao.moverABloque(88, 7, 41));
        assertEquals(HttpStatus.CONFLICT, e.getStatusCode());
        assertEquals("El pedido cambió de bloque, vuelve a cargar", e.getReason());
    }

    @Test void borrarPendienteComparaElBloqueLeidoEnElDelete() {
        when(jdbc.update(BORRAR, 88, 7)).thenReturn(1);
        dao.borrarPendiente(88, 7);
        verify(jdbc).update(BORRAR, 88, 7);
        ResponseStatusException e = assertThrows(ResponseStatusException.class, () -> dao.borrarPendiente(88, 8));
        assertEquals(HttpStatus.CONFLICT, e.getStatusCode());
        assertEquals("El pedido ya no está pendiente (no se puede borrar)", e.getReason());
    }
}
