package com.reparaciones.servidor.pedidos;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.KeyHolder;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SuppressWarnings("unchecked")
class FilasDAOTest {

    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 11, 10, 10, 0);
    private static final String COLUMNAS = "SELECT ID_FILA, FECHA, PLATAFORMA, SKU, IMEI, PEDIDO, FECHA_COMPRA, ORIGEN, CANAL, POSICION, CERRADA_EN,"
            + " ENVIO_FECHA, TRANSPORTISTA, SEGUIMIENTO, REABIERTA_EN FROM Pedido_fila";
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final FilasDAO dao = new FilasDAO(jdbc);

    private static FilaPedido fila(int id, String plataforma, String imei) {
        return new FilaPedido(id, LocalDate.of(2026, 11, 10), plataforma, "i13128AzulA", imei, "402-0000000-0000001",
                LocalDate.of(2026, 11, 9), "SHIPPYPRO", Canal.AMAZON, 1, null);
    }

    @Test void laFilaEstaVivaSoloSiNoEstaCerradaYEnviadaSoloConFechaDeEnvio() {
        assertTrue(fila(1, "AMZ", null).viva());
        assertFalse(fila(1, "AMZ", null).enviada());
        FilaPedido cerrada = new FilaPedido(1, null, "AMZ", null, null, null, null, "SHIPPYPRO", null, null, AHORA);
        assertFalse(cerrada.viva());
        assertFalse(cerrada.enviada());
        assertTrue(new FilaPedido(1, null, "AMZ", null, null, null, null, "SHIPPYPRO", null, null, AHORA, AHORA, "UPSv2",
                "1Z0000000000000001").enviada());
    }

    @Test void vivasDeShippyProPideLasVivasDeEseOrigen() {
        dao.vivasDeShippyPro();
        verify(jdbc).query(startsWith(COLUMNAS + " WHERE ORIGEN = 'SHIPPYPRO' AND CERRADA_EN IS NULL"), any(RowMapper.class));
    }

    @Test void insertarVaPorLotesYCortaLosTextos() {
        String larga = "x".repeat(200);
        dao.insertar(List.of(new FilaPedido(0, LocalDate.of(2026, 11, 10), larga, larga, larga, larga, null, "SHIPPYPRO",
                Canal.PARTICULARES, 2, null)));
        ArgumentCaptor<List<Object[]>> lote = ArgumentCaptor.forClass(List.class);
        verify(jdbc).batchUpdate(startsWith("INSERT INTO Pedido_fila (FECHA, PLATAFORMA, SKU, IMEI, PEDIDO, FECHA_COMPRA, ORIGEN, CANAL, POSICION)"),
                lote.capture());
        assertEquals(1, lote.getValue().size());
        assertArrayEquals(new Object[]{java.sql.Date.valueOf("2026-11-10"), "x".repeat(150), "x".repeat(60), "x".repeat(60),
                "x".repeat(60), null, "SHIPPYPRO", "PARTICULARES", 2}, lote.getValue().get(0));
    }

    @Test void insertarSinNadaNoToca() {
        dao.insertar(List.of());
        verifyNoInteractions(jdbc);
    }

    @Test void cerrarPoneLaHoraEnLasFilasIndicadasEnTandasDe500() {
        List<Integer> ids = new ArrayList<>();
        for (int i = 1; i <= 501; i++) ids.add(i);
        dao.cerrar(ids, AHORA);
        ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc, times(2)).update(startsWith("UPDATE Pedido_fila SET CERRADA_EN = ? WHERE CERRADA_EN IS NULL AND ID_FILA IN (?"), args.capture());
        assertEquals(501, args.getAllValues().get(0).length, "la hora y 500 ids");
        assertEquals(Timestamp.valueOf(AHORA), args.getAllValues().get(0)[0]);
        assertEquals(2, args.getAllValues().get(1).length, "la hora y 1 id");
    }

    @Test void cerrarSinIdsNoToca() {
        dao.cerrar(List.of(), AHORA);
        verifyNoInteractions(jdbc);
    }

    @Test void delPeriodoPideLasDeLasFechasOLasVivasEnOrdenDeHoja() {
        dao.delPeriodo(LocalDate.of(2026, 10, 10), LocalDate.of(2026, 11, 10));
        verify(jdbc).query(eq(COLUMNAS + " WHERE (FECHA BETWEEN ? AND ?) OR CERRADA_EN IS NULL ORDER BY FECHA, ID_FILA"),
                any(RowMapper.class), eq(java.sql.Date.valueOf("2026-10-10")), eq(java.sql.Date.valueOf("2026-11-10")));
    }

    @Test void buscarEscapaLosComodinesPideUnaMasYDevuelveEnOrdenDeHoja() {
        when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class)))
                .thenReturn(new ArrayList<>(List.of(fila(3, "AMZ", null), fila(2, "AMZ", null), fila(1, "AMZ", null))));
        List<FilaPedido> r = dao.buscar("50%_a\\b", 2);
        String patron = "%50\\%\\_a\\\\b%";
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).query(sql.capture(), any(RowMapper.class), eq(patron), eq(patron), eq(patron), eq(patron), eq(3));
        assertTrue(sql.getValue().contains(" WHERE PLATAFORMA LIKE ? ESCAPE '\\\\' OR SKU LIKE ? ESCAPE '\\\\'"
                + " OR IMEI LIKE ? ESCAPE '\\\\' OR PEDIDO LIKE ? ESCAPE '\\\\'"), sql.getValue());
        assertTrue(sql.getValue().endsWith(" ORDER BY FECHA DESC, ID_FILA DESC LIMIT ?"), sql.getValue());
        assertEquals(List.of(1, 2, 3), r.stream().map(FilaPedido::id).toList(), "el mas antiguo primero");
    }

    @Test void porIdDevuelveVacioSiNoExiste() {
        assertTrue(dao.porId(7).isEmpty());
        verify(jdbc).query(eq(COLUMNAS + " WHERE ID_FILA = ?"), any(RowMapper.class), eq(7));
    }

    @Test void shippyProConImeiBuscaPorLosImeiDeLaTandaEnLasVivasYLasRecientes() {
        dao.shippyProConImei(List.of("350000000000001", "350000000000002"), LocalDate.of(2026, 9, 11));
        verify(jdbc).query(eq(COLUMNAS + " WHERE ORIGEN = 'SHIPPYPRO' AND IMEI IS NOT NULL AND IMEI <> ''"
                        + " AND (CERRADA_EN IS NULL OR FECHA >= ?) AND IMEI IN (?, ?) ORDER BY FECHA, ID_FILA"),
                any(RowMapper.class), eq(java.sql.Date.valueOf("2026-09-11")), eq("350000000000001"), eq("350000000000002"));
    }

    @Test void shippyProConImeiDesdePideTodasLasConImeiVivasORecientes() {
        dao.shippyProConImeiDesde(LocalDate.of(2026, 9, 11));
        verify(jdbc).query(eq(COLUMNAS + " WHERE ORIGEN = 'SHIPPYPRO' AND IMEI IS NOT NULL AND IMEI <> ''"
                        + " AND (CERRADA_EN IS NULL OR FECHA >= ?) ORDER BY FECHA, ID_FILA"),
                any(RowMapper.class), eq(java.sql.Date.valueOf("2026-09-11")));
    }

    @Test void sinEnvioDeShippyProPideLasFilasSinFechaDeEnvioVivasOCerradasDeEsosPedidos() {
        dao.sinEnvioDeShippyPro(List.of(new SincroShippyPro.ClavePedido(Canal.AMAZON, "402-0000000-0000011"),
                new SincroShippyPro.ClavePedido(Canal.PARTICULARES, "W-1")));
        verify(jdbc).query(eq(COLUMNAS + " WHERE ORIGEN = 'SHIPPYPRO' AND ENVIO_FECHA IS NULL AND ((CANAL = ? AND PEDIDO = ?) OR (CANAL = ? AND PEDIDO = ?))"),
                any(RowMapper.class), eq("AMAZON"), eq("402-0000000-0000011"), eq("PARTICULARES"), eq("W-1"));
    }

    @Test void sinEnvioDeShippyProSinPedidosNoToca() {
        assertTrue(dao.sinEnvioDeShippyPro(List.of()).isEmpty());
        verifyNoInteractions(jdbc);
    }

    @Test void marcarEnviadasPoneElEnvioYCierraSoloLasVivasEnUnLoteCortandoLosTextos() {
        dao.marcarEnviadas(List.of(
                new SincroShippyPro.MarcaEnvio(4, LocalDateTime.of(2026, 11, 9, 18, 0), "UPSv2", "1Z0000000000000001"),
                new SincroShippyPro.MarcaEnvio(5, LocalDateTime.of(2026, 11, 9, 19, 0), "t".repeat(60), null)), AHORA);
        ArgumentCaptor<List<Object[]>> lote = ArgumentCaptor.forClass(List.class);
        verify(jdbc).batchUpdate(eq("UPDATE Pedido_fila SET ENVIO_FECHA = ?, TRANSPORTISTA = ?, SEGUIMIENTO = ?,"
                + " CERRADA_EN = COALESCE(CERRADA_EN, ?) WHERE ID_FILA = ? AND ENVIO_FECHA IS NULL"), lote.capture());
        assertArrayEquals(new Object[]{Timestamp.valueOf("2026-11-09 18:00:00"), "UPSv2", "1Z0000000000000001",
                Timestamp.valueOf(AHORA), 4}, lote.getValue().get(0));
        assertArrayEquals(new Object[]{Timestamp.valueOf("2026-11-09 19:00:00"), "t".repeat(40), null,
                Timestamp.valueOf(AHORA), 5}, lote.getValue().get(1));
    }

    @Test void marcarEnviadasSinNadaNoToca() {
        dao.marcarEnviadas(List.of(), AHORA);
        verifyNoInteractions(jdbc);
    }

    private static final String SQL_BLOQUEO = "SELECT IMEI, ORIGEN FROM Pedido_fila WHERE ID_FILA = ? FOR UPDATE";

    /** El mapeador del bloqueo recibe un ResultSet: se simula con la fila indicada. */
    @SuppressWarnings("unchecked")
    private void filaBloqueada(String imei, String origen) throws Exception {
        when(jdbc.query(eq(SQL_BLOQUEO), any(RowMapper.class), eq(5))).thenAnswer(inv -> {
            java.sql.ResultSet rs = mock(java.sql.ResultSet.class);
            when(rs.getString("IMEI")).thenReturn(imei);
            when(rs.getString("ORIGEN")).thenReturn(origen);
            return new ArrayList<>(List.of(inv.<RowMapper<Object>>getArgument(1).mapRow(rs, 0)));
        });
    }

    @Test void ponerImeiBloqueaLeeElAnteriorActualizaYApuntaElCambio() throws Exception {
        filaBloqueada("350000000000001", "SHIPPYPRO");
        assertEquals(FilasDAO.Resultado.PUESTO, dao.ponerImei(5, "350000000000002", 9, AHORA));
        var orden = inOrder(jdbc);
        orden.verify(jdbc).query(eq(SQL_BLOQUEO), any(RowMapper.class), eq(5));
        orden.verify(jdbc).update("UPDATE Pedido_fila SET IMEI = ? WHERE ID_FILA = ?", "350000000000002", 5);
        orden.verify(jdbc).update("INSERT INTO Pedido_fila_cambio (ID_FILA, ID_USU, FECHA, IMEI_ANTERIOR, IMEI_NUEVO) VALUES (?, ?, ?, ?, ?)",
                5, 9, Timestamp.valueOf(AHORA), "350000000000001", "350000000000002");
    }

    @Test void ponerImeiCortaYAdmiteAnteriorNuloYUsuarioNulo() throws Exception {
        filaBloqueada(null, "SHIPPYPRO");
        dao.ponerImei(5, "9".repeat(80), null, AHORA);
        verify(jdbc).update("UPDATE Pedido_fila SET IMEI = ? WHERE ID_FILA = ?", "9".repeat(60), 5);
        verify(jdbc).update(startsWith("INSERT INTO Pedido_fila_cambio"), eq(5), isNull(), eq(Timestamp.valueOf(AHORA)), isNull(), eq("9".repeat(60)));
    }

    @Test void ponerImeiDeUnaFilaQueNoExisteNoEscribeNada() {
        assertEquals(FilasDAO.Resultado.NO_EXISTE, dao.ponerImei(5, "350000000000002", 9, AHORA));
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    @Test void ponerImeiDeUnaFilaDelHistoricoNoEscribeNadaYDiceCerrada() throws Exception {
        filaBloqueada("texto de la hoja", "HISTORICO");
        assertEquals(FilasDAO.Resultado.CERRADA, dao.ponerImei(5, "350000000000002", 9, AHORA));
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    @Test void ponerImeiDeUnaFilaDeShippyProCerradaOEnviadaSiSePuede() throws Exception {
        // El bloqueo solo lee IMEI y ORIGEN: una fila de ShippyPro se edita se haya cerrado o enviado o no.
        filaBloqueada("350000000000001", "SHIPPYPRO");
        assertEquals(FilasDAO.Resultado.PUESTO, dao.ponerImei(5, "350000000000002", 9, AHORA));
        verify(jdbc).update("UPDATE Pedido_fila SET IMEI = ? WHERE ID_FILA = ?", "350000000000002", 5);
    }

    @Test void empezarLecturaDevuelveElIdGenerado() {
        when(jdbc.update(any(PreparedStatementCreator.class), any(KeyHolder.class))).thenAnswer(inv -> {
            ((KeyHolder) inv.getArgument(1)).getKeyList().add(Map.of("insert_id", 12));
            return 1;
        });
        assertEquals(12, dao.empezarLectura(AHORA));
    }

    @Test void terminarLecturaUneLosDesconocidosOrdenadosYCortaElMensaje() {
        dao.terminarLectura(4, "OK", 30, Set.of("b-sku", "a-sku"), "m".repeat(1200), AHORA);
        verify(jdbc).update("UPDATE Pedido_lectura SET FIN = ?, RESULTADO = ?, FILAS = ?, SKU_DESCONOCIDOS = ?, MENSAJE = ? WHERE ID_LECTURA = ?",
                Timestamp.valueOf(AHORA), "OK", 30, "a-sku, b-sku", "m".repeat(1000), 4);
    }

    @Test void terminarLecturaSinDesconocidosLosDejaNulos() {
        dao.terminarLectura(4, "ERROR", 0, List.of(), null, AHORA);
        verify(jdbc).update(anyString(), eq(Timestamp.valueOf(AHORA)), eq("ERROR"), eq(0), isNull(), isNull(), eq(4));
    }

    @Test void terminarLecturaCortaLosDesconocidosA500() {
        List<String> muchos = new ArrayList<>();
        for (int i = 0; i < 100; i++) muchos.add("sku-largo-numero-%03d".formatted(i));
        dao.terminarLectura(4, "OK", 1, muchos, null, AHORA);
        ArgumentCaptor<Object> texto = ArgumentCaptor.forClass(Object.class);
        verify(jdbc).update(anyString(), eq(Timestamp.valueOf(AHORA)), eq("OK"), eq(1), texto.capture(), isNull(), eq(4));
        assertEquals(500, ((String) texto.getValue()).length());
    }

    @Test void cerradasDeShippyProPideLasCerradasDeEsosPedidosPorCanalYNumero() {
        dao.cerradasDeShippyPro(List.of(new SincroShippyPro.ClavePedido(Canal.AMAZON, "402-0000000-0000011"),
                new SincroShippyPro.ClavePedido(Canal.PARTICULARES, "W-1")));
        verify(jdbc).query(eq(COLUMNAS + " WHERE ORIGEN = 'SHIPPYPRO' AND CERRADA_EN IS NOT NULL AND ((CANAL = ? AND PEDIDO = ?) OR (CANAL = ? AND PEDIDO = ?))"),
                any(RowMapper.class), eq("AMAZON"), eq("402-0000000-0000011"), eq("PARTICULARES"), eq("W-1"));
    }

    @Test void cerradasDeShippyProSinPedidosNoToca() {
        assertTrue(dao.cerradasDeShippyPro(List.of()).isEmpty());
        verifyNoInteractions(jdbc);
    }

    @Test void reabrirBorraLaHoraDeCierreYLosDatosDelEnvioYApuntaCuandoEnTandasDe500() {
        List<Integer> ids = new ArrayList<>();
        for (int i = 1; i <= 501; i++) ids.add(i);
        dao.reabrir(ids, AHORA);
        ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc, times(2)).update(startsWith("UPDATE Pedido_fila SET CERRADA_EN = NULL, ENVIO_FECHA = NULL, TRANSPORTISTA = NULL, SEGUIMIENTO = NULL,"
                + " REABIERTA_EN = ? WHERE CERRADA_EN IS NOT NULL AND ID_FILA IN (?"), args.capture());
        assertEquals(501, args.getAllValues().get(0).length, "la hora y 500 ids");
        assertEquals(Timestamp.valueOf(AHORA), args.getAllValues().get(0)[0]);
        assertEquals(2, args.getAllValues().get(1).length, "la hora y 1 id");
    }

    @Test void reabrirSinIdsNoToca() {
        dao.reabrir(List.of(), AHORA);
        verifyNoInteractions(jdbc);
    }

    @Test void ultimaLecturaSinResultadosNoPreguntaYDaVacio() {
        assertTrue(dao.ultimaLectura(Set.of()).isEmpty());
        verifyNoInteractions(jdbc);
    }

    @Test void ponerImeiVaEnReadCommitted() throws Exception {
        var m = FilasDAO.class.getMethod("ponerImei", int.class, String.class, Integer.class, LocalDateTime.class);
        var t = m.getAnnotation(org.springframework.transaction.annotation.Transactional.class);
        assertNotNull(t);
        assertEquals(org.springframework.transaction.annotation.Isolation.READ_COMMITTED, t.isolation());
    }

    @Test void ultimaLecturaFiltraPorResultados() {
        dao.ultimaLectura(Set.of("OK"));
        verify(jdbc).query(contains("FROM Pedido_lectura WHERE RESULTADO IN (?) ORDER BY ID_LECTURA DESC LIMIT 1"), any(RowMapper.class), eq("OK"));
    }

    @Test void podarLecturasBorraLasAnterioresALaFecha() {
        dao.podarLecturas(AHORA);
        verify(jdbc).update("DELETE FROM Pedido_lectura WHERE INICIO < ?", Timestamp.valueOf(AHORA));
    }

    // ---------------------------------------------------------------- carga del historico

    private static FilaPedido historica(int n) {
        return new FilaPedido(0, LocalDate.of(2026, 3, 1), "x".repeat(200), "i13128AzulA", "CANCELADO", n == 0 ? null : "402-0000000-000000" + n,
                n == 0 ? null : LocalDate.of(2026, 2, 28), "HISTORICO", null, null, LocalDate.of(2026, 3, 1).atStartOfDay());
    }

    @Test void borrarHistoricoBorraSoloLasFilasDeEseOrigen() {
        when(jdbc.update(anyString())).thenReturn(7);
        assertEquals(7, dao.borrarHistorico());
        verify(jdbc).update("DELETE FROM Pedido_fila WHERE ORIGEN = 'HISTORICO'");
        verifyNoMoreInteractions(jdbc);
    }

    @Test void insertarHistoricoVaEnOrdenEnLotesDe500CerradasAMedianocheYCortaLosTextos() {
        List<FilaPedido> filas = new ArrayList<>();
        for (int i = 0; i < 1201; i++) filas.add(historica(i % 9));
        dao.insertarHistorico(filas);
        ArgumentCaptor<List<Object[]>> lote = ArgumentCaptor.forClass(List.class);
        verify(jdbc, times(3)).batchUpdate(eq("INSERT INTO Pedido_fila (FECHA, PLATAFORMA, SKU, IMEI, PEDIDO, FECHA_COMPRA, ORIGEN,"
                + " CANAL, POSICION, CERRADA_EN) VALUES (?, ?, ?, ?, ?, ?, 'HISTORICO', NULL, NULL, ?)"), lote.capture());
        assertEquals(List.of(500, 500, 201), lote.getAllValues().stream().map(List::size).toList());
        assertArrayEquals(new Object[]{java.sql.Date.valueOf("2026-03-01"), "x".repeat(150), "i13128AzulA", "CANCELADO", null, null,
                Timestamp.valueOf("2026-03-01 00:00:00")}, lote.getAllValues().get(0).get(0));
        assertArrayEquals(new Object[]{java.sql.Date.valueOf("2026-03-01"), "x".repeat(150), "i13128AzulA", "CANCELADO",
                "402-0000000-0000001", java.sql.Date.valueOf("2026-02-28"), Timestamp.valueOf("2026-03-01 00:00:00")},
                lote.getAllValues().get(0).get(1), "el orden del lote es el de la lista");
    }

    @Test void insertarHistoricoSinFilasNoToca() {
        dao.insertarHistorico(List.of());
        verifyNoInteractions(jdbc);
    }

    @Test void todasDeShippyProBloqueadasLasPideEnOrdenDeHojaConFOR_UPDATE() {
        dao.todasDeShippyProBloqueadas();
        verify(jdbc).query(eq(COLUMNAS + " WHERE ORIGEN = 'SHIPPYPRO' ORDER BY FECHA, ID_FILA FOR UPDATE"), any(RowMapper.class));
    }

    @Test void traspasarImeiSoloEscribeSiLaFilaNoTeniaImeiYApuntaElCambioSinUsuario() {
        when(jdbc.update(startsWith("UPDATE Pedido_fila SET IMEI = ? WHERE ID_FILA = ? AND ORIGEN = 'SHIPPYPRO' AND (IMEI IS NULL OR IMEI = '')"),
                any(Object[].class))).thenReturn(1);
        assertTrue(dao.traspasarImei(5, "350000000000001", AHORA));
        verify(jdbc).update(contains("UPDATE Pedido_fila SET IMEI"), eq("350000000000001"), eq(5));
        verify(jdbc).update(eq("INSERT INTO Pedido_fila_cambio (ID_FILA, ID_USU, FECHA, IMEI_ANTERIOR, IMEI_NUEVO) VALUES (?, ?, ?, ?, ?)"),
                eq(5), isNull(), eq(Timestamp.valueOf(AHORA)), isNull(), eq("350000000000001"));
    }

    @Test void traspasarImeiSiLaFilaYaTeniaImeiNoApuntaNada() {
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(0);
        assertFalse(dao.traspasarImei(5, "350000000000001", AHORA));
        verify(jdbc, never()).update(startsWith("INSERT INTO Pedido_fila_cambio"), any(Object[].class));
    }

    @Test void contarCambiosDeHistoricoCuentaLosApuntesDeFilasHistorico() {
        when(jdbc.queryForObject(anyString(), eq(Integer.class))).thenReturn(3);
        assertEquals(3, dao.contarCambiosDeHistorico());
        verify(jdbc).queryForObject("SELECT COUNT(*) FROM Pedido_fila_cambio c JOIN Pedido_fila f ON f.ID_FILA = c.ID_FILA"
                + " WHERE f.ORIGEN = 'HISTORICO'", Integer.class);
    }
}
