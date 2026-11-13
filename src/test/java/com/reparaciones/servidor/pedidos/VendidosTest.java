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
        when(jdbc.query(any(String.class), any(RowMapper.class), any(Timestamp.class), any(), any(), anyInt())).thenAnswer(inv -> {
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
        verify(jdbc).query(eq(FilasDAO.SQL_VENDIDOS), any(RowMapper.class), eq(Timestamp.valueOf(LocalDateTime.of(2026, 11, 5, 0, 0))), isNull(), isNull(), eq(5000));
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
        when(jdbc.query(any(String.class), any(RowMapper.class), any(Timestamp.class), any(), any(), anyInt())).thenAnswer(inv -> {
            RowMapper<Vendido> mapa = inv.getArgument(1);
            return List.of(mapa.mapRow(fila("350000000000003", "a", 12), 0), mapa.mapRow(fila("350000000000003", "b", 10), 1));
        });
        Timestamp desde = Timestamp.valueOf(LocalDateTime.of(2026, 11, 5, 0, 0));
        List<Vendido> r = new FilasDAO(jdbc).vendidosDesde(desde.toLocalDateTime(), Canal.DISTRIBUIDORES, 5000);
        assertEquals(1, r.size());
        assertEquals("a", r.get(0).sku());
        verify(jdbc).query(eq(FilasDAO.SQL_VENDIDOS), any(RowMapper.class), eq(desde), eq("DISTRIBUIDORES"), eq("DISTRIBUIDORES"), eq(5000));
    }

    @Test void laConsultaPideLoQueDiceLaSpec() {
        String sql = FilasDAO.SQL_VENDIDOS;
        assertTrue(!sql.contains("PLATAFORMA"), "la plataforma no se lee");
        assertTrue(sql.contains("AND (? IS NULL OR f.CANAL = ?)"), "canal opcional, antes del tope");
        assertTrue(sql.contains("f.ORIGEN = 'SHIPPYPRO'"), "el historico no entra");
        assertTrue(sql.contains("(f.CERRADA_EN IS NULL OR f.ENVIO_FECHA IS NOT NULL OR u.FECHA_IMEI > f.CERRADA_EN)"), "pendiente, enviada o cerrada sin envio con IMEI posterior al cierre");
        assertTrue(sql.contains("f.IMEI REGEXP '^[0-9]{15}$'"), "15 cifras exactas; sin IMEI no sale");
        assertTrue(sql.contains("MAX(FECHA) AS FECHA_IMEI FROM Pedido_fila_cambio WHERE TIPO = 'IMEI' AND FECHA >= ? GROUP BY ID_FILA"),
                "el ultimo cambio de IMEI de la fila, no Pedido_fila.FECHA");
        assertTrue(sql.contains("ORDER BY u.FECHA_IMEI DESC, f.ID_FILA DESC LIMIT ?"));
        assertTrue(!sql.contains("f.FECHA"), "no se mira la fecha del pedido");
    }

    @Test void laConsultaSirveLasNoPendientesSoloSiElUltimoImeiEsPosteriorAlCierre() {
        String sql = FilasDAO.SQL_VENDIDOS;
        // Pendiente o enviada: entra como siempre.
        assertTrue(sql.contains("f.CERRADA_EN IS NULL OR f.ENVIO_FECHA IS NOT NULL OR u.FECHA_IMEI > f.CERRADA_EN"), sql);
        // Estrictamente posterior: un IMEI apuntado antes de cerrarse (pedido cancelado) o en el mismo instante no sale.
        assertTrue(!sql.contains(">= f.CERRADA_EN") && !sql.contains("<"), sql);
        // Mismo origen, canal, formato de IMEI, ventana (subconsulta) y orden de siempre: sigue siendo una sola consulta.
        assertTrue(sql.contains("f.ORIGEN = 'SHIPPYPRO'") && sql.contains("f.IMEI REGEXP '^[0-9]{15}$'")
                && sql.contains("WHERE TIPO = 'IMEI' AND FECHA >= ? GROUP BY ID_FILA") && sql.contains("(? IS NULL OR f.CANAL = ?)"), sql);
    }
}
