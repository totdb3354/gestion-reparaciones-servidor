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
        controller.vendidos(1);
        verify(dao).vendidosDesde(LocalDateTime.of(2026, 11, 11, 0, 0), 5000);
        controller.vendidos(7);
        verify(dao).vendidosDesde(LocalDateTime.of(2026, 11, 5, 0, 0), 5000);
        controller.vendidos(31);
        verify(dao).vendidosDesde(LocalDateTime.of(2026, 10, 12, 0, 0), 5000);
    }

    @Test void fueraDeRangoNoLlegaAlDao() {
        for (int dias : new int[]{0, 32, -3})
            org.junit.jupiter.api.Assertions.assertThrows(org.springframework.web.server.ResponseStatusException.class,
                    () -> controller.vendidos(dias));
        org.mockito.Mockito.verifyNoInteractions(dao);
    }

    private static ResultSet fila(String imei, String sku, int hora) throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getString("IMEI")).thenReturn(imei);
        when(rs.getString("SKU")).thenReturn(sku);
        when(rs.getString("PLATAFORMA")).thenReturn("AMZ");
        when(rs.getString("CANAL")).thenReturn("AMAZON");
        when(rs.getString("PEDIDO")).thenReturn("402-0000000-0000001");
        when(rs.getTimestamp("FECHA_IMEI")).thenReturn(Timestamp.valueOf(LocalDateTime.of(2026, 11, 10, hora, 0)));
        return rs;
    }

    @Test void elDaoHaceUnaConsultaConTopeYDejaUnaSolaFilaPorImeiLaMasReciente() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.query(any(String.class), any(RowMapper.class), any(Timestamp.class), anyInt())).thenAnswer(inv -> {
            RowMapper<Vendido> mapa = inv.getArgument(1);
            // La consulta ya viene del cambio mas reciente al mas antiguo.
            return List.of(mapa.mapRow(fila("350000000000001", "i13128AzulA", 12), 0),
                    mapa.mapRow(fila("350000000000002", "i13128NegroA", 11), 1),
                    mapa.mapRow(fila("350000000000001", "viejo", 9), 2));
        });
        List<Vendido> r = new FilasDAO(jdbc).vendidosDesde(LocalDateTime.of(2026, 11, 5, 0, 0), 5000);

        assertEquals(2, r.size());
        assertEquals("350000000000001", r.get(0).imei());
        assertEquals("i13128AzulA", r.get(0).sku(), "gana la fila del cambio mas reciente");
        assertEquals(LocalDateTime.of(2026, 11, 10, 12, 0), r.get(0).fechaImei());
        assertEquals("350000000000002", r.get(1).imei());
        verify(jdbc).query(eq(FilasDAO.SQL_VENDIDOS), any(RowMapper.class), eq(Timestamp.valueOf(LocalDateTime.of(2026, 11, 5, 0, 0))), eq(5000));
        org.mockito.Mockito.verifyNoMoreInteractions(jdbc);
    }

    @Test void laConsultaPideLoQueDiceLaSpec() {
        String sql = FilasDAO.SQL_VENDIDOS;
        assertTrue(sql.contains("f.ORIGEN = 'SHIPPYPRO'"), "el historico no entra");
        assertTrue(sql.contains("(f.CERRADA_EN IS NULL OR f.ENVIO_FECHA IS NOT NULL)"), "pendiente o enviada; no las cerradas sin envio");
        assertTrue(sql.contains("f.IMEI REGEXP '^[0-9]{15}$'"), "15 cifras exactas; sin IMEI no sale");
        assertTrue(sql.contains("MAX(FECHA) AS FECHA_IMEI FROM Pedido_fila_cambio WHERE FECHA >= ? GROUP BY ID_FILA"),
                "el ultimo cambio de IMEI de la fila, no Pedido_fila.FECHA");
        assertTrue(sql.contains("ORDER BY u.FECHA_IMEI DESC, f.ID_FILA DESC LIMIT ?"));
        assertTrue(!sql.contains("f.FECHA"), "no se mira la fecha del pedido");
    }
}
