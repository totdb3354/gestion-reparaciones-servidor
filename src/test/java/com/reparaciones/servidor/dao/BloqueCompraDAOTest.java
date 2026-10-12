package com.reparaciones.servidor.dao;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.KeyHolder;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Cabeceras de pedido (0.9.9, spec «Pedidos por bloques» §3-§4) con el JdbcTemplate mockeado. */
@SuppressWarnings({"unchecked", "rawtypes"})
class BloqueCompraDAOTest {

    private static final LocalDateTime AT = LocalDateTime.of(2026, 10, 9, 10, 0, 0);
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final BloqueCompraDAO dao = new BloqueCompraDAO(jdbc);

    @Test void crearInsertaElProveedorConLaFechaDeAhoraYDevuelveElId() throws Exception {
        when(jdbc.update(any(PreparedStatementCreator.class), any(KeyHolder.class))).thenAnswer(inv -> {
            KeyHolder claves = inv.getArgument(1);
            claves.getKeyList().add(Map.of("insert_id", 90));
            return 1;
        });

        assertEquals(90, dao.crear(2));

        ArgumentCaptor<PreparedStatementCreator> sentencia = ArgumentCaptor.forClass(PreparedStatementCreator.class);
        verify(jdbc).update(sentencia.capture(), any(KeyHolder.class));
        Connection con = mock(Connection.class);
        PreparedStatement ps = mock(PreparedStatement.class);
        when(con.prepareStatement(eq("INSERT INTO Bloque_compra (ID_PROV, FECHA) VALUES (?, NOW())"),
                eq(Statement.RETURN_GENERATED_KEYS))).thenReturn(ps);
        sentencia.getValue().createPreparedStatement(con);
        verify(ps).setInt(1, 2);
    }

    @Test void abiertoMasRecienteEsElPrimeroDeLaConsultaOVacio() {
        when(jdbc.query(eq(BloqueCompraDAO.SQL_ABIERTO), any(RowMapper.class), eq(2))).thenReturn(List.of(41));
        assertEquals(Optional.of(41), dao.abiertoMasReciente(2));
        assertEquals(Optional.empty(), dao.abiertoMasReciente(3));
        assertTrue(BloqueCompraDAO.SQL_ABIERTO.contains("cc.ESTADO <> 'pendiente'"));
        assertTrue(BloqueCompraDAO.SQL_ABIERTO.contains("EXISTS (SELECT 1 FROM Compra_componente cc WHERE cc.ID_BLOQUE = b.ID_BLOQUE)"));
        assertTrue(BloqueCompraDAO.SQL_ABIERTO.endsWith("ORDER BY b.ID_BLOQUE DESC LIMIT 1"));
    }

    @Test void getByIdLeeLaCabeceraConElUpdatedAtEnSegundos() throws Exception {
        dao.getById(41);
        ArgumentCaptor<RowMapper> mapper = ArgumentCaptor.forClass(RowMapper.class);
        verify(jdbc).query(contains("FROM Bloque_compra WHERE ID_BLOQUE = ?"), mapper.capture(), eq(41));
        ResultSet rs = mock(ResultSet.class);
        when(rs.getInt("ID_BLOQUE")).thenReturn(41);
        when(rs.getInt("ID_PROV")).thenReturn(2);
        when(rs.getTimestamp("FECHA")).thenReturn(Timestamp.valueOf(AT));
        when(rs.getString("NOTA")).thenReturn("Ped. 123");
        when(rs.getBoolean("RECONSTRUIDO")).thenReturn(true);
        when(rs.getTimestamp("UPDATED_AT")).thenReturn(Timestamp.valueOf(AT.withNano(500_000_000)));
        assertEquals(new BloqueCompraDAO.Bloque(41, 2, AT, "Ped. 123", true, AT), mapper.getValue().mapRow(rs, 0));
    }

    @Test void lineasLeeIdTipoCantidadEstadoYUpdatedAtEnSegundos() throws Exception {
        dao.lineas(41);
        ArgumentCaptor<RowMapper> mapper = ArgumentCaptor.forClass(RowMapper.class);
        verify(jdbc).query(eq(BloqueCompraDAO.SQL_LINEAS), mapper.capture(), eq(41));
        ResultSet rs = mock(ResultSet.class);
        when(rs.getInt("ID_COMPRA")).thenReturn(5);
        when(rs.getString("TIPO")).thenReturn("lcd-x-negro");
        when(rs.getInt("CANTIDAD")).thenReturn(3);
        when(rs.getString("ESTADO")).thenReturn("en_camino");
        when(rs.getTimestamp("UPDATED_AT")).thenReturn(Timestamp.valueOf(AT.withNano(900_000_000)));
        assertEquals(new BloqueCompraDAO.LineaBloque(5, "lcd-x-negro", 3, "en_camino", AT), mapper.getValue().mapRow(rs, 0));
        assertTrue(BloqueCompraDAO.SQL_LINEAS.endsWith("ORDER BY cc.FECHA_PEDIDO, cc.ID_COMPRA"));
    }

    @Test void contarLineas() {
        when(jdbc.queryForObject(BloqueCompraDAO.SQL_CONTAR, Integer.class, 41)).thenReturn(3);
        assertEquals(3, dao.contarLineas(41));
    }

    @Test void estaTerminadoCuentaLasLineasVivas() {
        assertEquals("SELECT COUNT(*) FROM Compra_componente WHERE ID_BLOQUE = ? AND ESTADO IN ('pendiente','en_camino','parcial')",
                BloqueCompraDAO.SQL_VIVAS);
        when(jdbc.queryForObject(BloqueCompraDAO.SQL_VIVAS, Integer.class, 41)).thenReturn(0);
        when(jdbc.queryForObject(BloqueCompraDAO.SQL_VIVAS, Integer.class, 42)).thenReturn(2);
        assertTrue(dao.estaTerminado(41));
        assertFalse(dao.estaTerminado(42));
    }

    @Test void borrarSiVacioSoloBorraUnBloqueSinLineas() {
        when(jdbc.update(BloqueCompraDAO.SQL_BORRAR_SI_VACIO, 41, 41)).thenReturn(1);
        assertTrue(dao.borrarSiVacio(41));
        assertFalse(dao.borrarSiVacio(42));
        assertTrue(BloqueCompraDAO.SQL_BORRAR_SI_VACIO.contains("NOT EXISTS (SELECT 1 FROM Compra_componente WHERE ID_BLOQUE = ?)"));
    }

    @Test void cambiarNotaGuardaElTextoONulo() {
        dao.cambiarNota(41, "Ped. 123");
        dao.cambiarNota(41, null);
        verify(jdbc).update(BloqueCompraDAO.SQL_CAMBIAR_NOTA, "Ped. 123", 41);
        verify(jdbc).update(BloqueCompraDAO.SQL_CAMBIAR_NOTA, null, 41);
    }
}
