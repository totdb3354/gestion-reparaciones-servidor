package com.reparaciones.servidor.pedidos;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.KeyHolder;

import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PedidosDAOTest {

    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 11, 4, 10, 0);
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final PedidosDAO dao = new PedidosDAO(jdbc);

    private static PedidoLeido.Unidad unidad(int pos, String imei, String cierre) {
        return new PedidoLeido.Unidad(pos, "i13128AzulA", imei, imei == null ? null : FuentePedidos.SIMULADOR, imei == null ? null : 7,
                imei == null ? null : AHORA, false, null, cierre);
    }

    @SuppressWarnings("unchecked")
    @Test void aplicarInsertaLosNuevosActualizaLosQueYaEstanYCierraLosQueNoHanSalido() throws Exception {
        // Ya están: el 6480 del Simulador (id 5) y un pedido de ShippyPro abierto que esta vez no sale (id 9).
        doAnswer(inv -> {
            RowCallbackHandler h = inv.getArgument(1);
            ResultSet rs = mock(ResultSet.class);
            when(rs.getInt("ID_PEDIDO")).thenReturn(5);
            when(rs.getString("CANAL")).thenReturn("SIMULADOR");
            when(rs.getString("NUMERO")).thenReturn("6480");
            h.processRow(rs);
            return null;
        }).when(jdbc).query(startsWith("SELECT ID_PEDIDO, CANAL, NUMERO FROM Pedido WHERE NUMERO IN"), any(RowCallbackHandler.class), any(Object[].class));
        doAnswer(inv -> {
            RowCallbackHandler h = inv.getArgument(1);
            for (Object[] fila : new Object[][]{{5, "SIMULADOR"}, {9, "SHIPPYPRO"}}) {
                ResultSet rs = mock(ResultSet.class);
                when(rs.getInt("ID_PEDIDO")).thenReturn((Integer) fila[0]);
                when(rs.getString("FUENTE")).thenReturn((String) fila[1]);
                h.processRow(rs);
            }
            return null;
        }).when(jdbc).query(eq(PedidosDAO.SQL_ABIERTOS_IDS), any(RowCallbackHandler.class));
        when(jdbc.update(any(PreparedStatementCreator.class), any(KeyHolder.class))).thenAnswer(inv -> {
            ((KeyHolder) inv.getArgument(1)).getKeyList().add(Map.of("insert_id", 12));
            return 1;
        });

        dao.aplicar(List.of(
                new PedidoLeido(Canal.SIMULADOR, "6480", "Móviles Levante SL", null, AHORA.minusDays(2), FuentePedidos.SIMULADOR,
                        List.of(unidad(1, "350000000000001", "RELEASED"), unidad(2, "350000000000002", "ABONO"))),
                new PedidoLeido(Canal.AMAZON, "402-0000000-0000001", "Cliente A", "DE", AHORA.minusDays(1), FuentePedidos.SHIPPYPRO,
                        List.of(unidad(1, null, null)))), AHORA);

        Timestamp ahora = Timestamp.valueOf(AHORA);
        verify(jdbc).update(PedidosDAO.SQL_ACTUALIZAR_PEDIDO, "Móviles Levante SL", null, Timestamp.valueOf(AHORA.minusDays(2)), ahora,
                "TODO_VENDIDO", ahora, "TODO_VENDIDO", 5);
        verify(jdbc).update(any(PreparedStatementCreator.class), any(KeyHolder.class));
        ArgumentCaptor<List<Object[]>> unidades = ArgumentCaptor.forClass(List.class);
        verify(jdbc).batchUpdate(eq(PedidosDAO.SQL_GUARDAR_UNIDAD), unidades.capture());
        assertEquals(3, unidades.getValue().size());
        assertArrayEquals(new Object[]{5, 1, "i13128AzulA", "350000000000001", "SIMULADOR", 7, ahora, false, null, ahora, "RELEASED"},
                unidades.getValue().get(0));
        assertArrayEquals(new Object[]{12, 1, "i13128AzulA", null, null, null, null, false, null, null, null}, unidades.getValue().get(2));
        verify(jdbc).update("DELETE FROM Pedido_unidad WHERE ID_PEDIDO = ? AND POSICION NOT IN (?, ?)", 5, 1, 2);
        verify(jdbc).update("DELETE FROM Pedido_unidad WHERE ID_PEDIDO = ? AND POSICION NOT IN (?)", 12, 1);
        ArgumentCaptor<List<Object[]>> cierres = ArgumentCaptor.forClass(List.class);
        verify(jdbc).batchUpdate(eq(PedidosDAO.SQL_CERRAR_PEDIDO), cierres.capture());
        assertEquals(1, cierres.getValue().size());
        assertArrayEquals(new Object[]{ahora, "YA_NO_PENDIENTE", 9}, cierres.getValue().get(0));
    }

    @Test void lasUnidadesSeGuardanPorPedidoYPosicionSinCambiarDeId() {
        assertTrue(PedidosDAO.SQL_GUARDAR_UNIDAD.startsWith("INSERT INTO Pedido_unidad"), PedidosDAO.SQL_GUARDAR_UNIDAD);
        assertTrue(PedidosDAO.SQL_GUARDAR_UNIDAD.contains("ON DUPLICATE KEY UPDATE SKU = VALUES(SKU)"), PedidosDAO.SQL_GUARDAR_UNIDAD);
        assertEquals("DELETE FROM Pedido_unidad WHERE ID_PEDIDO = ? AND POSICION NOT IN (%s)", PedidosDAO.SQL_RECORTAR_UNIDADES);
    }

    @Test void losDesconocidosSeResumenEnQuinientosCaracteres() {
        dao.anotarDesconocidos(3, List.of("i1", "i2"));
        verify(jdbc).update(PedidosDAO.SQL_DESCONOCIDOS, "i1, i2", 3);
        dao.anotarDesconocidos(4, List.of());
        verify(jdbc).update(PedidosDAO.SQL_DESCONOCIDOS, null, 4);
        assertEquals("i1, i2, … y 1 más", PedidosDAO.resumen(List.of("i1", "i2", "i3"), 2));
    }

    @SuppressWarnings("unchecked")
    @Test void laUltimaLecturaDeUnaFuenteConEsosResultados() {
        dao.ultimaLectura(FuentePedidos.PEDIDOS_2026, java.util.Set.of("OK"));
        verify(jdbc).query(contains("WHERE FUENTE = ? AND RESULTADO IN (?) ORDER BY ID_LECTURA DESC LIMIT 1"),
                any(RowMapper.class), eq("PEDIDOS_2026"), eq("OK"));
    }

    @SuppressWarnings("unchecked")
    @Test void laUltimaLecturaConFilasExigeFilasMayorQueCero() {
        dao.ultimaLecturaConFilas(FuentePedidos.PEDIDOS_2026, java.util.Set.of("OK"));
        verify(jdbc).query(contains("RESULTADO IN (?) AND FILAS > 0 ORDER BY ID_LECTURA DESC LIMIT 1"),
                any(RowMapper.class), eq("PEDIDOS_2026"), eq("OK"));
    }

    @Test void marcarErrorPonePorIdLasLecturasIndicadas() {
        dao.marcarError(List.of(4, 5, 6), "No se pudo");
        verify(jdbc).update("UPDATE Pedido_lectura SET RESULTADO = 'ERROR', MENSAJE = ? WHERE ID_LECTURA IN (?, ?, ?)",
                "No se pudo", 4, 5, 6);
    }

    @Test void marcarErrorSinLecturasNoHaceNada() {
        dao.marcarError(List.of(), "x");
        verifyNoInteractions(jdbc);
    }

    @Test void dosPedidosConLaMismaClaveEnUnaVueltaInsertanUnoYActualizanElOtro() {
        when(jdbc.update(any(PreparedStatementCreator.class), any(KeyHolder.class))).thenAnswer(inv -> {
            ((KeyHolder) inv.getArgument(1)).getKeyList().add(Map.of("insert_id", 12));
            return 1;
        });
        PedidoLeido a = new PedidoLeido(Canal.AMAZON, "402-1", "A", "DE", AHORA, FuentePedidos.SHIPPYPRO, List.of(unidad(1, null, null)));
        PedidoLeido b = new PedidoLeido(Canal.AMAZON, "402-1", "A2", "DE", AHORA, FuentePedidos.PEDIDOS_2026, List.of(unidad(1, null, null)));

        dao.aplicar(List.of(a, b), AHORA);

        verify(jdbc, times(1)).update(any(PreparedStatementCreator.class), any(KeyHolder.class));
        verify(jdbc, times(1)).update(eq(PedidosDAO.SQL_ACTUALIZAR_PEDIDO), any(), any(), any(), any(), any(), any(), any(), eq(12));
    }
}
