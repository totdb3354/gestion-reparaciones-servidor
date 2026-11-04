package com.reparaciones.servidor.pedidos;

import com.reparaciones.servidor.nsys.EstadoMovil;
import com.reparaciones.servidor.nsys.MovilNsys;
import com.reparaciones.servidor.nsys.ProductoMaestro;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class CrucePedidosTest {

    private static final Map<String, Canal> CANALES = Map.of("100", Canal.AMAZON, "200", Canal.AMAZON_STOCKROOM,
            "300", Canal.DISTRIBUIDORES);
    private static final AceptaSku ACEPTA = new AceptaSku(List.of(
            AceptaSkuTest.producto("Azul", "A", "i13128AzulA", "i13128AzulA", null, null),
            AceptaSkuTest.producto("Azul", "A-", "i13128AzulA-", "i13128AzulA", null, null),
            AceptaSkuTest.producto("Negro", "A", "i13128NegroA", "i13128NegroA", null, null)));

    private static LocalDateTime dia(int d) {
        return LocalDate.of(2026, 10, d).atTime(10, 0);
    }

    private static PedidoShippyPro amazon(String numero, int d, String sku, int cantidad) {
        return new PedidoShippyPro(numero, "100", dia(d), "Comprador " + numero, "DE", List.of(new PedidoShippyPro.Articulo(sku, cantidad)));
    }

    private static MovilNsys movil(String imei, String ubicacion, EstadoMovil estado, LocalDate recepcion) {
        return new MovilNsys(imei, "BT-9001", "Apple iPhone 13 128GB Blue", "A", "Available", "CAJA", "Proveedor A", recepcion,
                null, "i13128AzulA", "13", "13", "128GB", "Azul", "A", false, ubicacion, ubicacion, estado, null, null);
    }

    private static CrucePedidos.Resultado cruzar(List<PedidoShippyPro> s, List<FilaPedidos2026> p, List<FilaSimulador> m,
                                                 Map<String, MovilNsys> moviles) {
        return CrucePedidos.cruzar(new CrucePedidos.Entrada(s, CANALES, p, m, ACEPTA, moviles));
    }

    private static PedidoLeido pedido(CrucePedidos.Resultado r, String numero) {
        return r.pedidos().stream().filter(x -> x.numero().equals(numero)).findFirst().orElseThrow();
    }

    @Test void amazonDaLosImeisTecleadosALosPedidosMasAntiguosYElMasAvanzadoAlPrimero() {
        Map<String, MovilNsys> moviles = new HashMap<>();
        moviles.put("350000000000001", movil("350000000000001", "Taller · Glass", EstadoMovil.STOCK, LocalDate.of(2026, 9, 1)));
        moviles.put("350000000000002", movil("350000000000002", "Listos", EstadoMovil.STOCK, LocalDate.of(2026, 9, 20)));
        CrucePedidos.Resultado r = cruzar(
                List.of(amazon("402-0000000-0000003", 30, "i13128AzulA", 1), amazon("402-0000000-0000001", 28, "i13128AzulA", 1),
                        amazon("402-0000000-0000002", 29, "I13128AZULA", 1)),
                List.of(new FilaPedidos2026(10, dia(29), "Amazon", "i13128AzulA", "350000000000001"),
                        new FilaPedidos2026(11, dia(30), "amazon", "i13128AzulA-", "350000000000002")),
                List.of(), moviles);

        PedidoLeido.Unidad primero = pedido(r, "402-0000000-0000001").unidades().get(0);
        assertEquals("350000000000002", primero.imei(), "el de Listos va al pedido más antiguo");
        assertTrue(primero.sinPedidoConcreto());
        assertEquals(FuentePedidos.PEDIDOS_2026, primero.imeiFuente());
        assertEquals(11, primero.imeiRef());
        assertEquals("350000000000001", pedido(r, "402-0000000-0000002").unidades().get(0).imei());
        assertNull(pedido(r, "402-0000000-0000003").unidades().get(0).imei(), "dos IMEIs cubren los dos más antiguos");
    }

    @Test void unaFilaAnteriorAlPedidoOConElImeiEnReleasedNoCuenta() {
        Map<String, MovilNsys> moviles = Map.of("350000000000002",
                movil("350000000000002", "Listos", EstadoMovil.VENDIDO, LocalDate.of(2026, 9, 1)));
        CrucePedidos.Resultado r = cruzar(List.of(amazon("402-0000000-0000001", 28, "i13128AzulA", 1)),
                List.of(new FilaPedidos2026(5, dia(27), "Amazon", "i13128AzulA", "350000000000001"),
                        new FilaPedidos2026(6, dia(29), "Amazon", "i13128AzulA", "350000000000002")),
                List.of(), moviles);
        assertNull(pedido(r, "402-0000000-0000001").unidades().get(0).imei());
    }

    @Test void unaCantidadDeDosSonDosUnidadesYUnSkuDesconocidoSeApunta() {
        CrucePedidos.Resultado r = cruzar(List.of(new PedidoShippyPro("402-0000000-0000009", "100", dia(28), "C", "DE",
                        List.of(new PedidoShippyPro.Articulo("i13128NegroA", 2), new PedidoShippyPro.Articulo("i99999RaroA", 1),
                                new PedidoShippyPro.Articulo("FUNDA-1", 1))),
                new PedidoShippyPro("1", "999", dia(28), "Otro", "ES", List.of(new PedidoShippyPro.Articulo("i13128AzulA", 1)))),
                List.of(), List.of(), Map.of());
        assertEquals(List.of(1, 2), pedido(r, "402-0000000-0000009").unidades().stream().map(PedidoLeido.Unidad::posicion).toList());
        assertEquals(Set.of("i99999RaroA"), r.desconocidosShippyPro());
        assertTrue(r.pedidos().stream().noneMatch(p -> p.numero().equals("1")), "un marketplace fuera del ajuste se ignora");
    }

    @Test void laWebSeCruzaPorClienteYSkuAlPedidoAbiertoMasAntiguo() {
        CrucePedidos.Resultado r = cruzar(List.of(
                        new PedidoShippyPro("18840", "300", dia(29), "Tienda Norte SL", "ES", List.of(new PedidoShippyPro.Articulo("i13128AzulA", 1))),
                        new PedidoShippyPro("18832", "300", dia(28), "TIENDA NORTE S.L.", "ES", List.of(new PedidoShippyPro.Articulo("i13128AzulA", 1))),
                        new PedidoShippyPro("18833", "300", dia(28), "Tienda Norte SL", "ES", List.of(new PedidoShippyPro.Articulo("i13128AzulA", 1)))),
                List.of(new FilaPedidos2026(20, dia(30), "tiénda norte sl", "i13128AzulA", "350000000000005")),
                List.of(), Map.of());
        assertEquals("350000000000005", pedido(r, "18833").unidades().get(0).imei());
        assertFalse(pedido(r, "18833").unidades().get(0).sinPedidoConcreto());
        assertNull(pedido(r, "18840").unidades().get(0).imei());
        assertNull(pedido(r, "18832").unidades().get(0).imei(), "otro nombre («S.L.») no es el mismo cliente");
    }

    @Test void simuladorCuentaCadaImeiUnaVezCierraLosAbonadosYNoRepiteEnPedidos2026() {
        Map<String, MovilNsys> moviles = Map.of("350000000000003",
                movil("350000000000003", "Listos", EstadoMovil.VENDIDO, LocalDate.of(2026, 9, 1)));
        CrucePedidos.Resultado r = cruzar(List.of(new PedidoShippyPro("18850", "300", dia(28), "Móviles Levante SL", "ES",
                        List.of(new PedidoShippyPro.Articulo("i13128AzulA", 1)))),
                List.of(new FilaPedidos2026(30, dia(29), "Móviles Levante SL", "i13128AzulA", "350000000000001")),
                List.of(new FilaSimulador(2, "6480", "X", LocalDate.of(2026, 10, 27), "Móviles Levante SL", "i13128AzulA", 1, "350000000000001"),
                        new FilaSimulador(3, "6480", "X", LocalDate.of(2026, 10, 27), "Móviles Levante SL", "i13128NegroA", 1, null),
                        new FilaSimulador(4, "6480", "X", LocalDate.of(2026, 10, 27), "Móviles Levante SL", "GLS24", 1, null),
                        new FilaSimulador(5, "6480", "X", LocalDate.of(2026, 10, 27), "Móviles Levante SL", "i13128AzulA", 1, "350000000000002"),
                        new FilaSimulador(6, "6481", "E", LocalDate.of(2026, 10, 28), "Móviles Levante SL", "i13128AzulA", 1, "350000000000001"),
                        new FilaSimulador(7, "6482", "X", LocalDate.of(2026, 10, 28), "TecnoSur", "i13128AzulA", 1, "350000000000003"),
                        new FilaSimulador(8, "6483", "BB", LocalDate.of(2026, 10, 29), "Móviles Levante SL", "i13128AzulA", -1, "350000000000002")),
                moviles);

        PedidoLeido x = pedido(r, "6480");
        assertEquals(Canal.SIMULADOR, x.canal());
        assertEquals(LocalDate.of(2026, 10, 27).atStartOfDay(), x.fecha());
        assertEquals(3, x.unidades().size(), "GLS24 no es un teléfono");
        assertEquals("6481", x.unidades().get(0).otroOrderId());
        assertEquals(FuentePedidos.SIMULADOR, x.unidades().get(0).imeiFuente());
        assertNull(x.unidades().get(1).imei());
        assertEquals("ABONO", x.unidades().get(2).motivoCierre());
        assertTrue(r.pedidos().stream().noneMatch(p -> p.numero().equals("6481")), "la E solo repite IMEIs: no hay pedido");
        assertEquals("RELEASED", pedido(r, "6482").unidades().get(0).motivoCierre());
        assertNull(pedido(r, "18850").unidades().get(0).imei(), "el IMEI del Simulador no se usa otra vez en PEDIDOS 2026");
        assertTrue(r.desconocidosSimulador().isEmpty(), "GLS24 no parece un teléfono");
    }
}
