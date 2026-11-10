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
    private static final String COLUMNAS = "SELECT ID_FILA, FECHA, PLATAFORMA, SKU, IMEI, PEDIDO, FECHA_COMPRA, ORIGEN, CANAL, POSICION, ENVIADA_EN"
            + " FROM Pedido_fila";
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final FilasDAO dao = new FilasDAO(jdbc);

    private static FilaPedido fila(int id, String plataforma, String imei) {
        return new FilaPedido(id, LocalDate.of(2026, 11, 10), plataforma, "i13128AzulA", imei, "402-0000000-0000001",
                LocalDate.of(2026, 11, 9), "SHIPPYPRO", Canal.AMAZON, 1, null);
    }

    @Test void laFilaEstaVivaSoloSiNoEstaEnviada() {
        assertTrue(fila(1, "AMZ", null).viva());
        assertFalse(new FilaPedido(1, null, "AMZ", null, null, null, null, "SHIPPYPRO", null, null, AHORA).viva());
    }

    @Test void vivasDeShippyProPideLasVivasDeEseOrigen() {
        dao.vivasDeShippyPro();
        verify(jdbc).query(startsWith(COLUMNAS + " WHERE ORIGEN = 'SHIPPYPRO' AND ENVIADA_EN IS NULL"), any(RowMapper.class));
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
        verify(jdbc, times(2)).update(startsWith("UPDATE Pedido_fila SET ENVIADA_EN = ? WHERE ENVIADA_EN IS NULL AND ID_FILA IN (?"), args.capture());
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
        verify(jdbc).query(eq(COLUMNAS + " WHERE (FECHA BETWEEN ? AND ?) OR ENVIADA_EN IS NULL ORDER BY FECHA, ID_FILA"),
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

    @Test void vivasConImeiBuscaPorLosImeiDeLaTanda() {
        dao.vivasConImei(List.of("350000000000001", "350000000000002"));
        verify(jdbc).query(eq(COLUMNAS + " WHERE ENVIADA_EN IS NULL AND IMEI IN (?, ?) ORDER BY FECHA, ID_FILA"),
                any(RowMapper.class), eq("350000000000001"), eq("350000000000002"));
    }

    private static final String SQL_BLOQUEO = "SELECT IMEI, ENVIADA_EN, ORIGEN FROM Pedido_fila WHERE ID_FILA = ? FOR UPDATE";

    /** El mapeador del bloqueo recibe un ResultSet: se simula con la fila indicada. */
    @SuppressWarnings("unchecked")
    private void filaBloqueada(String imei, Timestamp enviadaEn, String origen) throws Exception {
        when(jdbc.query(eq(SQL_BLOQUEO), any(RowMapper.class), eq(5))).thenAnswer(inv -> {
            java.sql.ResultSet rs = mock(java.sql.ResultSet.class);
            when(rs.getString("IMEI")).thenReturn(imei);
            when(rs.getTimestamp("ENVIADA_EN")).thenReturn(enviadaEn);
            when(rs.getString("ORIGEN")).thenReturn(origen);
            return new ArrayList<>(List.of(inv.<RowMapper<Object>>getArgument(1).mapRow(rs, 0)));
        });
    }

    @Test void ponerImeiBloqueaLeeElAnteriorActualizaYApuntaElCambio() throws Exception {
        filaBloqueada("350000000000001", null, "SHIPPYPRO");
        assertEquals(FilasDAO.Resultado.PUESTO, dao.ponerImei(5, "350000000000002", 9, AHORA));
        var orden = inOrder(jdbc);
        orden.verify(jdbc).query(eq(SQL_BLOQUEO), any(RowMapper.class), eq(5));
        orden.verify(jdbc).update("UPDATE Pedido_fila SET IMEI = ? WHERE ID_FILA = ?", "350000000000002", 5);
        orden.verify(jdbc).update("INSERT INTO Pedido_fila_cambio (ID_FILA, ID_USU, FECHA, IMEI_ANTERIOR, IMEI_NUEVO) VALUES (?, ?, ?, ?, ?)",
                5, 9, Timestamp.valueOf(AHORA), "350000000000001", "350000000000002");
    }

    @Test void ponerImeiCortaYAdmiteAnteriorNuloYUsuarioNulo() throws Exception {
        filaBloqueada(null, null, "SHIPPYPRO");
        dao.ponerImei(5, "9".repeat(80), null, AHORA);
        verify(jdbc).update("UPDATE Pedido_fila SET IMEI = ? WHERE ID_FILA = ?", "9".repeat(60), 5);
        verify(jdbc).update(startsWith("INSERT INTO Pedido_fila_cambio"), eq(5), isNull(), eq(Timestamp.valueOf(AHORA)), isNull(), eq("9".repeat(60)));
    }

    @Test void ponerImeiDeUnaFilaQueNoExisteNoEscribeNada() {
        assertEquals(FilasDAO.Resultado.NO_EXISTE, dao.ponerImei(5, "350000000000002", 9, AHORA));
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    @Test void ponerImeiDeUnaFilaEnviadaOHistoricaNoEscribeNadaYDiceCerrada() throws Exception {
        filaBloqueada("350000000000001", Timestamp.valueOf(AHORA.minusHours(1)), "SHIPPYPRO");
        assertEquals(FilasDAO.Resultado.CERRADA, dao.ponerImei(5, "350000000000002", 9, AHORA));
        filaBloqueada("texto de la hoja", null, "HISTORICO");
        assertEquals(FilasDAO.Resultado.CERRADA, dao.ponerImei(5, "350000000000002", 9, AHORA));
        verify(jdbc, never()).update(anyString(), any(Object[].class));
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

    @Test void ultimaLecturaFiltraPorResultados() {
        dao.ultimaLectura(Set.of("OK"));
        verify(jdbc).query(contains("FROM Pedido_lectura WHERE RESULTADO IN (?) ORDER BY ID_LECTURA DESC LIMIT 1"), any(RowMapper.class), eq("OK"));
    }

    @Test void podarLecturasBorraLasAnterioresALaFecha() {
        dao.podarLecturas(AHORA);
        verify(jdbc).update("DELETE FROM Pedido_lectura WHERE INICIO < ?", Timestamp.valueOf(AHORA));
    }
}
