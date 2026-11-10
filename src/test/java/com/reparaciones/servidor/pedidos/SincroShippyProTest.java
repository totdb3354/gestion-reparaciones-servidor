package com.reparaciones.servidor.pedidos;

import com.reparaciones.servidor.nsys.ProductoMaestro;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class SincroShippyProTest {

    private static final LocalDate HOY = LocalDate.of(2026, 11, 10);
    private static final Map<String, Canal> CANALES = Map.of("100", Canal.AMAZON, "200", Canal.PARTICULARES,
            "300", Canal.AMAZON_STOCKROOM);
    private static final List<ProductoMaestro> MAESTRO = List.of(
            AceptaSkuTest.producto("Azul", "A", "i13128AzulA", "i13128AzulA", "i13128AzulAP", "bk-azul-a"));
    private static final AceptaSku ACEPTA = new AceptaSku(MAESTRO);

    private static PedidoShippyPro pedido(String numero, String marketplace, String cliente, PedidoShippyPro.Articulo... arts) {
        return new PedidoShippyPro(numero, marketplace, LocalDateTime.of(2026, 11, 9, 18, 30), cliente, "ES", List.of(arts));
    }

    private static PedidoShippyPro.Articulo art(String sku, int cantidad) {
        return new PedidoShippyPro.Articulo(sku, cantidad);
    }

    private static FilaPedido viva(int id, Canal canal, String pedido, int posicion) {
        return new FilaPedido(id, HOY, "AMZ", "i13128AzulA", null, pedido, HOY, "SHIPPYPRO", canal, posicion, null);
    }

    private static SincroShippyPro.Plan plan(List<PedidoShippyPro> pendientes, List<FilaPedido> vivas) {
        return SincroShippyPro.de(pendientes, CANALES, ACEPTA, vivas, HOY);
    }

    @Test void unMarketplaceFueraDelAjusteSeIgnora() {
        SincroShippyPro.Plan p = plan(List.of(pedido("402-0000000-0000011", "999", "Cliente Web A", art("i13128AzulA", 1))), List.of());
        assertTrue(p.insertar().isEmpty());
        assertTrue(p.desconocidos().isEmpty());
    }

    @Test void unSkuQueElMaestroNoConoceNoCreaFilaYSiPareceTelefonoVaADesconocidos() {
        SincroShippyPro.Plan p = plan(List.of(pedido("402-0000000-0000011", "100", null,
                art("i99999RosaA", 1), art("PORTES", 1))), List.of());
        assertTrue(p.insertar().isEmpty());
        assertEquals(Set.of("i99999RosaA"), p.desconocidos());
    }

    @Test void unaCantidadNDaNFilasConPosicionesPorPedidoEnElOrdenQueLlegan() {
        SincroShippyPro.Plan p = plan(List.of(pedido("402-0000000-0000011", "100", null,
                art("i13128AzulA", 2), art("i13128AzulA", 1))), List.of());
        assertEquals(List.of(1, 2, 3), p.insertar().stream().map(FilaPedido::posicion).toList());
    }

    @Test void unaFilaVivaConLaMismaClaveNoSeInsertaNiSeCierra() {
        SincroShippyPro.Plan p = plan(List.of(pedido("402-0000000-0000011", "100", null, art("i13128AzulA", 2))),
                List.of(viva(7, Canal.AMAZON, "402-0000000-0000011", 1)));
        assertEquals(List.of(2), p.insertar().stream().map(FilaPedido::posicion).toList());
        assertTrue(p.cerrar().isEmpty());
    }

    @Test void unaFilaVivaDeUnPedidoQueYaNoEstaPendienteSeCierra() {
        SincroShippyPro.Plan p = plan(List.of(pedido("402-0000000-0000012", "100", null, art("i13128AzulA", 1))),
                List.of(viva(7, Canal.AMAZON, "402-0000000-0000011", 1), viva(8, Canal.AMAZON, "402-0000000-0000012", 1)));
        assertEquals(List.of(7), p.cerrar());
        assertTrue(p.insertar().isEmpty());
    }

    @Test void laFilaNuevaLlevaFechaDeHoyFechaDeCompraOrigenYElSkuTalCualLlega() {
        SincroShippyPro.Plan p = plan(List.of(pedido("402-0000000-0000011", "100", null, art("I13128AZULA", 1)),
                pedido("402-0000000-0000013", "200", " Cliente Web A ", art("i13128AzulAP", 1))), List.of());
        FilaPedido f = p.insertar().get(0);
        assertEquals(HOY, f.fecha());
        assertEquals(LocalDate.of(2026, 11, 9), f.fechaCompra());
        assertEquals("SHIPPYPRO", f.origen());
        assertEquals("I13128AZULA", f.sku());
        assertEquals(Canal.AMAZON, f.canal());
        assertEquals("402-0000000-0000011", f.pedido());
        assertNull(f.imei());
        assertEquals("Cliente Web A", p.insertar().get(1).plataforma());
    }

    @Test void laPlataformaEsAmzFnskOElClienteOWeb() {
        assertEquals("AMZ", SincroShippyPro.plataforma(Canal.AMAZON, "Cliente Web A"));
        assertEquals("FNSK", SincroShippyPro.plataforma(Canal.AMAZON_STOCKROOM, null));
        assertEquals("Cliente Web A", SincroShippyPro.plataforma(Canal.PARTICULARES, " Cliente Web A "));
        assertEquals("Web", SincroShippyPro.plataforma(Canal.BACKIPHONE, null));
        assertEquals("Web", SincroShippyPro.plataforma(Canal.DISTRIBUIDORES, "  "));
    }

    @Test void unPedidoSinNumeroSeSalta() {
        assertTrue(plan(List.of(pedido(" ", "100", null, art("i13128AzulA", 1))), List.of()).insertar().isEmpty());
    }
}
