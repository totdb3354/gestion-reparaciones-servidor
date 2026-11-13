package com.reparaciones.servidor.pedidos;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** POST /api/pedidos/sincronizados: validacion, normalizacion del numero de pedido y recuentos. */
class SincronizadosControllerTest {

    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");
    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 11, 13, 10, 0);
    private static final String I1 = "350000000000001";
    private static final String I2 = "350000000000002";
    private final FilasDAO dao = mock(FilasDAO.class);
    private final SincronizadosController controller = new SincronizadosController(dao, Clock.fixed(AHORA.atZone(MADRID).toInstant(), MADRID));

    private static SincronizadosController.PedidoImeis p(String pedido, String... imeis) {
        return new SincronizadosController.PedidoImeis(pedido, java.util.Arrays.asList(imeis));
    }

    private int estado(String canal, List<SincronizadosController.PedidoImeis> pedidos) {
        return assertThrows(ResponseStatusException.class,
                () -> controller.sincronizar(new SincronizadosController.Peticion(canal, pedidos))).getStatusCode().value();
    }

    @Test void devuelveLosRecuentosDelDaoYLeDaLaHoraDeMadrid() {
        when(dao.sincronizar(any(), any(), any())).thenReturn(new FilasDAO.InformeSincronizacion(2, 1, 3));
        var r = controller.sincronizar(new SincronizadosController.Peticion("PARTICULARES", List.of(p("37352", I1, I2))));
        assertEquals(new SincronizadosController.Respuesta(2, 1, 3), r);
        verify(dao).sincronizar(Canal.PARTICULARES, List.of(new FilasDAO.PedidoSincronizado("37352", Set.of(I1, I2))), AHORA);
    }

    @Test void losTresCanalesWebValen() {
        when(dao.sincronizar(any(), any(), any())).thenReturn(new FilasDAO.InformeSincronizacion(0, 0, 0));
        for (String c : List.of("DISTRIBUIDORES", "PARTICULARES", "BACKIPHONE"))
            controller.sincronizar(new SincronizadosController.Peticion(c, List.of()));
        verify(dao, times(3)).sincronizar(any(), any(), any());
    }

    @Test void unCanalQueNoEsWebOQueNoExisteEs400() {
        for (String malo : new String[]{"AMAZON", "AMAZON_STOCKROOM", "NOEXISTE", "particulares", "", null})
            assertEquals(400, estado(malo, List.of(p("1", I1))), String.valueOf(malo));
        verifyNoInteractions(dao);
    }

    @Test void unImeiQueNoSon15CifrasEs400() {
        for (String malo : new String[]{"123", "35000000000000A", "3500000000000011", "", null})
            assertEquals(400, estado("PARTICULARES", List.of(p("1", I1, malo))), String.valueOf(malo));
        verifyNoInteractions(dao);
    }

    @Test void pedidosOImeisAusentesOSinNumeroSon400() {
        assertEquals(400, estado("PARTICULARES", null));
        assertEquals(400, estado("PARTICULARES", List.of(new SincronizadosController.PedidoImeis("1", null))));
        assertEquals(400, estado("PARTICULARES", List.of(p("  ", I1))));
        assertEquals(400, estado("PARTICULARES", List.of(new SincronizadosController.PedidoImeis(null, List.of(I1)))));
        verifyNoInteractions(dao);
    }

    @Test void elTopeEs500PedidosY50ImeisPorPedido() {
        when(dao.sincronizar(any(), any(), any())).thenReturn(new FilasDAO.InformeSincronizacion(0, 0, 0));
        List<SincronizadosController.PedidoImeis> quinientos = new ArrayList<>();
        for (int i = 0; i < 500; i++) quinientos.add(p("P" + i, I1));
        controller.sincronizar(new SincronizadosController.Peticion("PARTICULARES", quinientos));
        List<SincronizadosController.PedidoImeis> demasiados = new ArrayList<>(quinientos);
        demasiados.add(p("P500", I1));
        assertEquals(400, estado("PARTICULARES", demasiados));

        List<String> cincuenta = new ArrayList<>();
        for (int i = 0; i < 50; i++) cincuenta.add(String.format("35000000000%04d", i));
        controller.sincronizar(new SincronizadosController.Peticion("PARTICULARES", List.of(new SincronizadosController.PedidoImeis("1", cincuenta))));
        cincuenta.add(I1);
        assertEquals(400, estado("PARTICULARES", List.of(new SincronizadosController.PedidoImeis("1", cincuenta))));
        verify(dao, times(2)).sincronizar(any(), any(), any());
    }

    @Test void elNumeroDePedidoSeNormalizaComoLoGuardaLaLecturaDeShippyPro() {
        when(dao.sincronizar(any(), any(), any())).thenReturn(new FilasDAO.InformeSincronizacion(0, 0, 0));
        String largo = "9".repeat(80);
        controller.sincronizar(new SincronizadosController.Peticion("PARTICULARES",
                List.of(p("  37352 ", I1), p("37352", I2), p(largo, I1))));
        verify(dao).sincronizar(eq(Canal.PARTICULARES), eq(List.of(
                new FilasDAO.PedidoSincronizado("37352", Set.of(I1, I2)),
                new FilasDAO.PedidoSincronizado("9".repeat(AnchosPedidos.PEDIDO), Set.of(I1)))), eq(AHORA));
    }

    @Test void losImeiConEspaciosSeRecortan() {
        when(dao.sincronizar(any(), any(), any())).thenReturn(new FilasDAO.InformeSincronizacion(0, 0, 0));
        controller.sincronizar(new SincronizadosController.Peticion("PARTICULARES", List.of(p("1", " " + I1 + " "))));
        verify(dao).sincronizar(Canal.PARTICULARES, List.of(new FilasDAO.PedidoSincronizado("1", Set.of(I1))), AHORA);
    }
}
