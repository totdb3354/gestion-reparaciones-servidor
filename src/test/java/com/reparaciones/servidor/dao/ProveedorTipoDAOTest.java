package com.reparaciones.servidor.dao;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Tipos que vende cada proveedor (spec 2026-10-12 §3): SQL y reparto, con el JdbcTemplate simulado. */
class ProveedorTipoDAOTest {

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final ProveedorTipoDAO dao = new ProveedorTipoDAO(jdbc);

    /** Una fila con ID_PROV, PREFIJO y MODELO (el que no se use, da igual). */
    private static ResultSet fila(int idProv, String prefijo, String modelo) throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getInt("ID_PROV")).thenReturn(idProv);
        when(rs.getString("PREFIJO")).thenReturn(prefijo);
        when(rs.getString("MODELO")).thenReturn(modelo);
        return rs;
    }

    @Test void lasConsultasSonLasDeLaSpec() {
        assertEquals("SELECT ID_PROV, PREFIJO FROM Proveedor_tipo", ProveedorTipoDAO.SQL_TIPOS);
        assertEquals("SELECT ID_PROV, PREFIJO, MODELO FROM Proveedor_tipo_excluido", ProveedorTipoDAO.SQL_EXCLUIDOS);
        assertEquals("SELECT ID_PROV, PREFIJO FROM Proveedor_tipo WHERE ID_PROV = ?", ProveedorTipoDAO.SQL_TIPOS_DE);
        assertEquals("SELECT ID_PROV, PREFIJO, MODELO FROM Proveedor_tipo_excluido WHERE ID_PROV = ?", ProveedorTipoDAO.SQL_EXCLUIDOS_DE);
        assertEquals("DELETE FROM Proveedor_tipo WHERE ID_PROV = ?", ProveedorTipoDAO.SQL_BORRAR);
        assertEquals("INSERT INTO Proveedor_tipo (ID_PROV, PREFIJO) VALUES (?, ?)", ProveedorTipoDAO.SQL_INSERTAR_TIPO);
        assertEquals("INSERT INTO Proveedor_tipo_excluido (ID_PROV, PREFIJO, MODELO) VALUES (?, ?, ?)", ProveedorTipoDAO.SQL_INSERTAR_EXCLUIDO);
        assertEquals("SELECT TIPO, ACTIVO FROM Componente WHERE TIPO NOT LIKE 'otro%'", ProveedorTipoDAO.SQL_PIEZAS);
    }

    @Test void todosJuntaTiposYExcepcionesPorProveedor() {
        doAnswer(inv -> {
            RowCallbackHandler h = inv.getArgument(1);
            h.processRow(fila(2, "bat", null));
            h.processRow(fila(1, "g", null));
            h.processRow(fila(1, "bat", null));
            return null;
        }).when(jdbc).query(eq(ProveedorTipoDAO.SQL_TIPOS), any(RowCallbackHandler.class));
        doAnswer(inv -> {
            RowCallbackHandler h = inv.getArgument(1);
            h.processRow(fila(1, "g", "13mini"));
            h.processRow(fila(1, "g", "12mini"));
            return null;
        }).when(jdbc).query(eq(ProveedorTipoDAO.SQL_EXCLUIDOS), any(RowCallbackHandler.class));
        Map<Integer, Map<String, Set<String>>> todos = dao.todos();
        assertEquals(Map.of(1, Map.of("bat", Set.of(), "g", Set.of("12mini", "13mini")), 2, Map.of("bat", Set.of())), todos);
    }

    @Test void deProveedorConsultaSoloLosSuyos() {
        doAnswer(inv -> { ((RowCallbackHandler) inv.getArgument(1)).processRow(fila(7, "lcd", null)); return null; })
                .when(jdbc).query(eq(ProveedorTipoDAO.SQL_TIPOS_DE), any(RowCallbackHandler.class), eq(7));
        doAnswer(inv -> { ((RowCallbackHandler) inv.getArgument(1)).processRow(fila(7, "lcd", "16e")); return null; })
                .when(jdbc).query(eq(ProveedorTipoDAO.SQL_EXCLUIDOS_DE), any(RowCallbackHandler.class), eq(7));
        assertEquals(Map.of("lcd", Set.of("16e")), dao.deProveedor(7));
    }

    @SuppressWarnings("unchecked")
    @Test void reemplazarBorraEInsertaTiposYExcepciones() {
        Map<String, Set<String>> tipos = new TreeMap<>(Map.of("bat", Set.of(), "g", new TreeSet<>(Set.of("12mini", "13mini"))));
        dao.reemplazar(7, tipos);
        verify(jdbc).update(ProveedorTipoDAO.SQL_BORRAR, 7);
        ArgumentCaptor<List<Object[]>> deTipos = ArgumentCaptor.forClass(List.class);
        verify(jdbc).batchUpdate(eq(ProveedorTipoDAO.SQL_INSERTAR_TIPO), deTipos.capture());
        assertEquals(List.of(List.of(7, "bat"), List.of(7, "g")), deTipos.getValue().stream().map(Arrays::asList).toList());
        ArgumentCaptor<List<Object[]>> deExcluidos = ArgumentCaptor.forClass(List.class);
        verify(jdbc).batchUpdate(eq(ProveedorTipoDAO.SQL_INSERTAR_EXCLUIDO), deExcluidos.capture());
        assertEquals(List.of(List.of(7, "g", "12mini"), List.of(7, "g", "13mini")), deExcluidos.getValue().stream().map(Arrays::asList).toList());
    }

    @Test void reemplazarSinTiposSoloBorra() {
        dao.reemplazar(7, Map.of());
        verify(jdbc).update(ProveedorTipoDAO.SQL_BORRAR, 7);
        verify(jdbc, never()).batchUpdate(anyString(), anyList());
    }

    @Test void reemplazarSinExcepcionesNoLasInserta() {
        dao.reemplazar(7, Map.of("bat", Set.of()));
        verify(jdbc).batchUpdate(eq(ProveedorTipoDAO.SQL_INSERTAR_TIPO), anyList());
        verify(jdbc, never()).batchUpdate(eq(ProveedorTipoDAO.SQL_INSERTAR_EXCLUIDO), anyList());
    }

    @SuppressWarnings("unchecked")
    @Test void piezasLeeTipoYActivo() throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getString("TIPO")).thenReturn("bati13");
        when(rs.getBoolean("ACTIVO")).thenReturn(false);
        when(jdbc.query(eq(ProveedorTipoDAO.SQL_PIEZAS), any(RowMapper.class))).thenAnswer(inv ->
                List.of(((RowMapper<ProveedorTipoDAO.Pieza>) inv.getArgument(1)).mapRow(rs, 0)));
        assertEquals(List.of(new ProveedorTipoDAO.Pieza("bati13", false)), dao.piezas());
    }
}
