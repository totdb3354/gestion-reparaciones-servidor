package com.reparaciones.servidor.dao;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;

import java.sql.ResultSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Piezas que vende cada proveedor (spec 2026-10-11 §3): SQL y reparto, con el JdbcTemplate simulado. */
class ProveedorComponenteDAOTest {

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final ProveedorComponenteDAO dao = new ProveedorComponenteDAO(jdbc);

    /** Da al RowCallbackHandler de la llamada las filas (ID_PROV, ID_COM) indicadas. */
    private static void filas(RowCallbackHandler h, int[][] filas) throws Exception {
        for (int[] f : filas) {
            ResultSet rs = mock(ResultSet.class);
            when(rs.getInt("ID_PROV")).thenReturn(f[0]);
            when(rs.getInt("ID_COM")).thenReturn(f[1]);
            when(rs.getInt("ID_MASTER")).thenReturn(f[1]);
            h.processRow(rs);
        }
    }

    @Test void proveedoresPorPiezaAgrupaPorPiezaYOrdena() {
        doAnswer(inv -> { filas(inv.getArgument(1), new int[][]{{2, 5}, {1, 5}, {1, 6}}); return null; })
                .when(jdbc).query(eq(ProveedorComponenteDAO.SQL_TODAS), any(RowCallbackHandler.class));
        Map<Integer, List<Integer>> mapa = dao.proveedoresPorPieza();
        assertEquals(Map.of(5, List.of(1, 2), 6, List.of(1)), mapa);
        assertEquals("SELECT ID_PROV, ID_COM FROM Proveedor_componente", ProveedorComponenteDAO.SQL_TODAS);
    }

    @Test void piezasDeUnProveedor() {
        when(jdbc.queryForList(ProveedorComponenteDAO.SQL_DE_PROVEEDOR, Integer.class, 7)).thenReturn(List.of(5, 6));
        assertEquals(Set.of(5, 6), dao.piezasDe(7));
        assertEquals("SELECT ID_COM FROM Proveedor_componente WHERE ID_PROV = ?", ProveedorComponenteDAO.SQL_DE_PROVEEDOR);
    }

    @Test void mastersDeConsultaConUnaMarcaPorIdYDevuelveSoloLosQueExisten() {
        doAnswer(inv -> {
            RowCallbackHandler h = inv.getArgument(1);
            ResultSet rs = mock(ResultSet.class);
            when(rs.getInt("ID_COM")).thenReturn(12);
            when(rs.getInt("ID_MASTER")).thenReturn(3);
            h.processRow(rs);
            return null;
        }).when(jdbc).query(eq("SELECT ID_COM, COALESCE(ID_COM_MASTER, ID_COM) AS ID_MASTER FROM Componente WHERE ID_COM IN (?, ?)"),
                any(RowCallbackHandler.class), eq(12), eq(99));
        assertEquals(Map.of(12, 3), dao.mastersDe(List.of(12, 99)));
    }

    @Test void mastersDeConColeccionVaciaNoConsulta() {
        assertEquals(Map.of(), dao.mastersDe(List.of()));
        verifyNoInteractions(jdbc);
    }

    @Test void anadirYQuitarVanEnLote() {
        dao.anadir(7, List.of(5, 6));
        dao.quitar(7, List.of(8));
        verify(jdbc).batchUpdate(eq("INSERT INTO Proveedor_componente (ID_PROV, ID_COM) VALUES (?, ?)"),
                argThat((List<Object[]> l) -> l.size() == 2 && l.get(0)[0].equals(7) && l.get(0)[1].equals(5) && l.get(1)[1].equals(6)));
        verify(jdbc).batchUpdate(eq("DELETE FROM Proveedor_componente WHERE ID_PROV = ? AND ID_COM = ?"),
                argThat((List<Object[]> l) -> l.size() == 1 && l.get(0)[0].equals(7) && l.get(0)[1].equals(8)));
    }
}
