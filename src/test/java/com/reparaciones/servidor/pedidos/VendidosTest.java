package com.reparaciones.servidor.pedidos;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** La ventana en dias de Madrid del controlador y la consulta unica (con el mas reciente por IMEI) del DAO. */
@SuppressWarnings("unchecked")
class VendidosTest {

    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");
    /** 11/11 a las 00:30 de Madrid: ya es el dia 11 en Madrid aunque en UTC todavia sea el 10. */
    private static final Clock RELOJ = Clock.fixed(LocalDateTime.of(2026, 11, 11, 0, 30).atZone(MADRID).toInstant(), MADRID);

    private final FilasDAO dao = mock(FilasDAO.class);
    private final VendidosController controller = new VendidosController(dao, RELOJ);

    @Test void laVentanaEmpiezaALaMedianocheDeMadridDeHoyMenosDiasMenosUno() {
        controller.vendidos(1, null);
        verify(dao).vendidosDesde(LocalDateTime.of(2026, 11, 11, 0, 0), null, 5000);
        controller.vendidos(7, null);
        verify(dao).vendidosDesde(LocalDateTime.of(2026, 11, 5, 0, 0), null, 5000);
        controller.vendidos(31, null);
        verify(dao).vendidosDesde(LocalDateTime.of(2026, 10, 12, 0, 0), null, 5000);
    }

    @Test void fueraDeRangoNoLlegaAlDao() {
        for (int dias : new int[]{0, 32, -3})
            org.junit.jupiter.api.Assertions.assertThrows(org.springframework.web.server.ResponseStatusException.class,
                    () -> controller.vendidos(dias, null));
        org.mockito.Mockito.verifyNoInteractions(dao);
    }

    private static ResultSet fila(String imei, String sku, int hora) throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getString("IMEI")).thenReturn(imei);
        when(rs.getString("SKU")).thenReturn(sku);
        when(rs.getString("CANAL")).thenReturn("AMAZON");
        when(rs.getString("PEDIDO")).thenReturn("402-0000000-0000001");
        when(rs.getTimestamp("FECHA_IMEI")).thenReturn(Timestamp.valueOf(LocalDateTime.of(2026, 11, 10, hora, 0)));
        return rs;
    }

    @Test void elDaoHaceUnaConsultaConTopeYDejaUnaSolaFilaPorImeiLaMasReciente() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.query(any(String.class), any(RowMapper.class), any(Timestamp.class), any(Timestamp.class), any(), any(), anyInt())).thenAnswer(inv -> {
            RowMapper<Vendido> mapa = inv.getArgument(1);
            // La consulta ya viene del cambio mas reciente al mas antiguo.
            return List.of(mapa.mapRow(fila("350000000000001", "i13128AzulA", 12), 0),
                    mapa.mapRow(fila("350000000000002", "i13128NegroA", 11), 1),
                    mapa.mapRow(fila("350000000000001", "viejo", 9), 2));
        });
        List<Vendido> r = new FilasDAO(jdbc).vendidosDesde(LocalDateTime.of(2026, 11, 5, 0, 0), null, 5000);

        assertEquals(2, r.size());
        assertEquals("350000000000001", r.get(0).imei());
        assertEquals("i13128AzulA", r.get(0).sku(), "gana la fila del cambio mas reciente");
        assertEquals(LocalDateTime.of(2026, 11, 10, 12, 0), r.get(0).fechaImei());
        assertEquals("350000000000002", r.get(1).imei());
        verify(jdbc).query(eq(FilasDAO.SQL_VENDIDOS), any(RowMapper.class), eq(Timestamp.valueOf(LocalDateTime.of(2026, 11, 5, 0, 0))),
                eq(Timestamp.valueOf(LocalDateTime.of(2026, 11, 5, 0, 0))), isNull(), isNull(), eq(5000));
        org.mockito.Mockito.verifyNoMoreInteractions(jdbc);
    }

    @Test void elCanalSePasaAlDaoYUnoDesconocidoEsUn400() {
        controller.vendidos(7, "DISTRIBUIDORES");
        verify(dao).vendidosDesde(LocalDateTime.of(2026, 11, 5, 0, 0), Canal.DISTRIBUIDORES, 5000);
        for (String malo : new String[]{"NOEXISTE", "distribuidores", "Amazon"})
            org.junit.jupiter.api.Assertions.assertThrows(org.springframework.web.server.ResponseStatusException.class,
                    () -> controller.vendidos(7, malo));
        org.mockito.Mockito.verifyNoMoreInteractions(dao);
    }

    @Test void elDaoFiltraPorCanalEnLaConsultaYDespuesQuitaRepetidos() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.query(any(String.class), any(RowMapper.class), any(Timestamp.class), any(Timestamp.class), any(), any(), anyInt())).thenAnswer(inv -> {
            RowMapper<Vendido> mapa = inv.getArgument(1);
            return List.of(mapa.mapRow(fila("350000000000003", "a", 12), 0), mapa.mapRow(fila("350000000000003", "b", 10), 1));
        });
        Timestamp desde = Timestamp.valueOf(LocalDateTime.of(2026, 11, 5, 0, 0));
        List<Vendido> r = new FilasDAO(jdbc).vendidosDesde(desde.toLocalDateTime(), Canal.DISTRIBUIDORES, 5000);
        assertEquals(1, r.size());
        assertEquals("a", r.get(0).sku());
        verify(jdbc).query(eq(FilasDAO.SQL_VENDIDOS), any(RowMapper.class), eq(desde), eq(desde), eq("DISTRIBUIDORES"), eq("DISTRIBUIDORES"), eq(5000));
    }

    private static final Timestamp DESDE = Timestamp.valueOf(LocalDateTime.of(2026, 11, 5, 0, 0));

    private static ResultSet reservada(String imei, String sku, String canal, int hora) throws Exception {
        ResultSet rs = fila(imei, sku, hora);
        when(rs.getString("CANAL")).thenReturn(canal);
        return rs;
    }

    @Test void conElAjusteEncendidoSirveTambienLosReservadosMezclados() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.query(eq(FilasDAO.SQL_VENDIDOS), any(RowMapper.class), any(Timestamp.class), any(Timestamp.class), any(), any(), anyInt())).thenAnswer(inv -> {
            RowMapper<Vendido> mapa = inv.getArgument(1);
            return List.of(mapa.mapRow(fila("350000000000001", "a", 11), 0), mapa.mapRow(fila("350000000000002", "b", 9), 1));
        });
        when(jdbc.query(eq(FilasDAO.SQL_VENDIDOS_RESERVADOS), any(RowMapper.class), any(Timestamp.class), any(), any(), anyInt())).thenAnswer(inv -> {
            RowMapper<Vendido> mapa = inv.getArgument(1);
            return List.of(mapa.mapRow(reservada("350000000000003", "r", "AMAZON", 12), 0), mapa.mapRow(reservada("350000000000002", "r2", "AMAZON", 10), 1));
        });
        List<Vendido> r = new FilasDAO(jdbc, true).vendidosDesde(DESDE.toLocalDateTime(), Canal.AMAZON, 5000);
        assertEquals(List.of("350000000000003", "350000000000001", "350000000000002"), r.stream().map(Vendido::imei).toList());
        assertEquals("r2", r.get(2).sku(), "el IMEI repetido se queda con la fecha mas reciente (la reserva de las 10, no la de las 9)");
        verify(jdbc).query(eq(FilasDAO.SQL_VENDIDOS), any(RowMapper.class), eq(DESDE), eq(DESDE), eq("AMAZON"), eq("AMAZON"), eq(5000));
        verify(jdbc).query(eq(FilasDAO.SQL_VENDIDOS_RESERVADOS), any(RowMapper.class), eq(DESDE), eq("AMAZON"), eq("AMAZON"), eq(5000));
    }

    @Test void conElAjusteEncendidoElTopeSeAplicaTrasMezclar() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.query(eq(FilasDAO.SQL_VENDIDOS), any(RowMapper.class), any(Timestamp.class), any(Timestamp.class), any(), any(), anyInt())).thenAnswer(inv -> {
            RowMapper<Vendido> mapa = inv.getArgument(1);
            return List.of(mapa.mapRow(fila("350000000000001", "a", 11), 0));
        });
        when(jdbc.query(eq(FilasDAO.SQL_VENDIDOS_RESERVADOS), any(RowMapper.class), any(Timestamp.class), any(), any(), anyInt())).thenAnswer(inv -> {
            RowMapper<Vendido> mapa = inv.getArgument(1);
            return List.of(mapa.mapRow(reservada("350000000000003", "r", "AMAZON", 12), 0));
        });
        List<Vendido> r = new FilasDAO(jdbc, true).vendidosDesde(DESDE.toLocalDateTime(), null, 1);
        assertEquals(List.of("350000000000003"), r.stream().map(Vendido::imei).toList());
    }

    @Test void conElAjusteApagadoNoSeConsultanLosReservados() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        new FilasDAO(jdbc, false).vendidosDesde(DESDE.toLocalDateTime(), null, 5000);
        verify(jdbc).query(eq(FilasDAO.SQL_VENDIDOS), any(RowMapper.class), eq(DESDE), eq(DESDE), isNull(), isNull(), eq(5000));
        org.mockito.Mockito.verifyNoMoreInteractions(jdbc);
    }

    @Test void laConsultaDeReservadosPideLoQueDiceLaSpec() {
        String sql = FilasDAO.SQL_VENDIDOS_RESERVADOS;
        assertTrue(sql.contains("f.ORIGEN = 'SHIPPYPRO' AND f.CERRADA_EN IS NULL AND f.MARCA IS NULL AND (f.IMEI IS NULL OR f.IMEI = '')"));
        assertTrue(sql.contains("f.RESERVADA_EN >= ?"));
        assertTrue(sql.contains("f.RESERVADA_EN AS FECHA_IMEI"));
        assertTrue(sql.contains("f.ASOCIADA_EN IS NULL THEN NULL ELSE f.PEDIDO END"), "Amazon sin asociar: pedido nulo");
        assertTrue(sql.contains("AND (? IS NULL OR f.CANAL = ?)"));
        assertTrue(sql.endsWith("LIMIT ?"));
    }

    @Test void laConsultaPideLoQueDiceLaSpec() {
        String sql = FilasDAO.SQL_VENDIDOS;
        assertTrue(!sql.contains("PLATAFORMA"), "la plataforma no se lee");
        assertTrue(sql.contains("AND (? IS NULL OR f.CANAL = ?)"), "canal opcional, antes del tope");
        assertTrue(sql.contains("f.ORIGEN = 'SHIPPYPRO'"), "el historico no entra");
        assertTrue(sql.contains("(u.ID_FILA IS NOT NULL AND (f.CERRADA_EN IS NULL OR f.ENVIO_FECHA IS NOT NULL OR u.ULTIMO > f.CERRADA_EN))"),
                "pendiente, enviada o cerrada sin envio con IMEI posterior al cierre, siempre con un apunte en la ventana");
        assertTrue(sql.contains("f.IMEI REGEXP '^[0-9]{15}$'"), "15 cifras exactas; sin IMEI no sale");
        assertTrue(sql.contains("MAX(FECHA) AS ULTIMO FROM Pedido_fila_cambio WHERE TIPO IN ('IMEI', 'MARCA_QUITADA') AND FECHA >= ? GROUP BY ID_FILA"),
                "el ultimo cambio de IMEI o retirada de marca de la fila, no Pedido_fila.FECHA");
        assertTrue(sql.contains("ORDER BY FECHA_IMEI DESC, f.ID_FILA DESC LIMIT ?"));
        assertTrue(!sql.contains("f.FECHA"), "no se mira la fecha del pedido");
    }

    @Test void ningunaFilaConMarcaSeSirveYLaRetiradaDeLaMarcaCuentaComoFechaNueva() {
        String sql = FilasDAO.SQL_VENDIDOS;
        // Cualquier marca (con o sin IMEI) deja la fila fuera: la condicion va en el WHERE, no depende de la marca concreta.
        // Tambien la fila web juntada: el filtro de la marca va antes de los dos casos y los cubre a los dos.
        assertTrue(sql.contains("WHERE f.ORIGEN = 'SHIPPYPRO' AND f.MARCA IS NULL AND ((u.ID_FILA IS NOT NULL AND (f.CERRADA_EN IS NULL"), sql);
        assertTrue(!sql.contains("f.MARCA ="), "ninguna marca concreta: todas fuera");
        // El MAX de los apuntes IMEI y MARCA_QUITADA es la mas reciente de las dos fechas (fechaImei y ventana de dias).
        assertTrue(sql.contains("TIPO IN ('IMEI', 'MARCA_QUITADA')"), sql);
        assertEquals(5, sql.chars().filter(c -> c == '?').count(), "ventana (dos veces), canal (dos veces) y tope");
    }

    @Test void laConsultaSirveLasNoPendientesSoloSiElUltimoImeiEsPosteriorAlCierre() {
        String sql = FilasDAO.SQL_VENDIDOS;
        // Pendiente o enviada: entra como siempre.
        assertTrue(sql.contains("f.CERRADA_EN IS NULL OR f.ENVIO_FECHA IS NOT NULL OR u.ULTIMO > f.CERRADA_EN"), sql);
        // Estrictamente posterior: un IMEI apuntado antes de cerrarse (pedido cancelado) o en el mismo instante no sale.
        assertTrue(!sql.contains(">= f.CERRADA_EN") && !sql.contains("<"), sql);
        // Mismo origen, canal, formato de IMEI, ventana (subconsulta) y orden de siempre: sigue siendo una sola consulta.
        assertTrue(sql.contains("f.ORIGEN = 'SHIPPYPRO'") && sql.contains("f.IMEI REGEXP '^[0-9]{15}$'")
                && sql.contains("WHERE TIPO IN ('IMEI', 'MARCA_QUITADA') AND FECHA >= ? GROUP BY ID_FILA") && sql.contains("(? IS NULL OR f.CANAL = ?)"), sql);
    }

    @Test void enAmazonSinAsociarElPedidoVaNuloYElImeiSeSirveIgual() {
        String sql = FilasDAO.SQL_VENDIDOS;
        assertTrue(sql.contains("CASE WHEN f.CANAL IN ('AMAZON', 'AMAZON_STOCKROOM') AND f.ASOCIADA_EN IS NULL THEN NULL ELSE f.PEDIDO END AS PEDIDO"), sql);
        // La asociacion no filtra filas: solo cambia lo que se ve del pedido.
        assertTrue(!sql.contains("ASOCIADA_EN IS NOT NULL") && sql.indexOf("ASOCIADA_EN") < sql.indexOf(" FROM Pedido_fila f"), sql);
        assertEquals(5, sql.chars().filter(c -> c == '?').count());
    }

    /** La fila web juntada (spec §8): de una web, ya no pendiente, sin envio propio y sincronizada por el plugin. */
    private static final String JUNTADA = "f.CANAL IN ('DISTRIBUIDORES', 'PARTICULARES', 'BACKIPHONE') AND f.CERRADA_EN IS NOT NULL"
            + " AND f.ENVIO_FECHA IS NULL AND f.SINCRONIZADA_EN IS NOT NULL";

    @Test void laFilaWebJuntadaSaleSiLaWebLaSincronizoAunqueElImeiSeaDeAntesDelCierre() {
        String sql = FilasDAO.SQL_VENDIDOS;
        assertEquals(JUNTADA, FilasDAO.JUNTADA_SINCRONIZADA);
        // Segundo caso del WHERE: juntada con un apunte en la ventana o sincronizada dentro de la ventana; no mira el cierre.
        assertTrue(sql.contains(" OR (" + JUNTADA + " AND (u.ID_FILA IS NOT NULL OR f.SINCRONIZADA_EN >= ?)))"), sql);
        // Amazon no esta en la lista de canales: no cambia. Sin sincronizar (SINCRONIZADA_EN nulo) no entra en el caso.
        assertTrue(!JUNTADA.contains("AMAZON"), JUNTADA);
        // LEFT JOIN: la juntada sin apunte en la ventana (IMEI antiguo) tiene que poder entrar por la sincronizacion.
        assertTrue(sql.contains(" FROM Pedido_fila f LEFT JOIN (SELECT ID_FILA, MAX(FECHA) AS ULTIMO FROM Pedido_fila_cambio"), sql);
        // La subconsulta sigue acotada por la ventana (no recorre el historial entero para las filas normales).
        assertTrue(sql.contains("WHERE TIPO IN ('IMEI', 'MARCA_QUITADA') AND FECHA >= ? GROUP BY ID_FILA) u ON u.ID_FILA = f.ID_FILA"), sql);
    }

    @Test void laFechaDeLaJuntadaEsLaMasRecienteEntreElUltimoApunteYLaSincronizacionYLasDemasSiguenIgual() {
        String sql = FilasDAO.SQL_VENDIDOS;
        // Solo la juntada mira SINCRONIZADA_EN; las demas filas siguen con el ultimo apunte (una fila vendida y sincronizada despues no vuelve).
        assertTrue(sql.contains("CASE WHEN " + JUNTADA + " THEN GREATEST(COALESCE(u.ULTIMO, f.SINCRONIZADA_EN), f.SINCRONIZADA_EN)"
                + " ELSE u.ULTIMO END AS FECHA_IMEI FROM Pedido_fila f"), sql);
        // Orden y dedup con la fecha nueva.
        assertTrue(sql.endsWith(" ORDER BY FECHA_IMEI DESC, f.ID_FILA DESC LIMIT ?"), sql);
        // La ventana va primero en la subconsulta y despues en la sincronizacion; luego canal y tope.
        int ventana1 = sql.indexOf("FECHA >= ?"), ventana2 = sql.indexOf("f.SINCRONIZADA_EN >= ?"), canal = sql.indexOf("(? IS NULL OR f.CANAL = ?)");
        assertTrue(ventana1 > 0 && ventana1 < ventana2 && ventana2 < canal, sql);
    }

    @Test void elDaoLeeLaFechaDeLaJuntadaComoFechaImei() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        ResultSet juntada = mock(ResultSet.class);
        when(juntada.getString("IMEI")).thenReturn("009900000000011");
        when(juntada.getString("SKU")).thenReturn("i13128AzulA");
        when(juntada.getString("CANAL")).thenReturn("PARTICULARES");
        when(juntada.getString("PEDIDO")).thenReturn("0099001");
        when(juntada.getTimestamp("FECHA_IMEI")).thenReturn(Timestamp.valueOf(LocalDateTime.of(2026, 11, 18, 17, 5)));
        when(jdbc.query(any(String.class), any(RowMapper.class), any(Timestamp.class), any(Timestamp.class), any(), any(), anyInt())).thenAnswer(inv -> {
            RowMapper<Vendido> mapa = inv.getArgument(1);
            return List.of(mapa.mapRow(juntada, 0));
        });
        List<Vendido> r = new FilasDAO(jdbc).vendidosDesde(LocalDateTime.of(2026, 11, 12, 0, 0), Canal.PARTICULARES, 5000);
        assertEquals(1, r.size());
        assertEquals(new Vendido("009900000000011", "i13128AzulA", "PARTICULARES", "0099001", LocalDateTime.of(2026, 11, 18, 17, 5)), r.get(0));
        Timestamp desde = Timestamp.valueOf(LocalDateTime.of(2026, 11, 12, 0, 0));
        verify(jdbc).query(eq(FilasDAO.SQL_VENDIDOS), any(RowMapper.class), eq(desde), eq(desde), eq("PARTICULARES"), eq("PARTICULARES"), eq(5000));
    }

    @Test void elDaoLeeElPedidoQueDaLaConsulta() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        ResultSet sinAsociar = fila("350000000000005", "i13128AzulA", 12);
        when(sinAsociar.getString("PEDIDO")).thenReturn(null);
        when(jdbc.query(any(String.class), any(RowMapper.class), any(Timestamp.class), any(Timestamp.class), any(), any(), anyInt())).thenAnswer(inv -> {
            RowMapper<Vendido> mapa = inv.getArgument(1);
            return List.of(mapa.mapRow(sinAsociar, 0));
        });
        List<Vendido> r = new FilasDAO(jdbc).vendidosDesde(LocalDateTime.of(2026, 11, 5, 0, 0), null, 5000);
        assertEquals("350000000000005", r.get(0).imei());
        org.junit.jupiter.api.Assertions.assertNull(r.get(0).pedido());
    }
}
