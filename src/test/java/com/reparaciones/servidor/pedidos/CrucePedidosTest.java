package com.reparaciones.servidor.pedidos;

import com.reparaciones.servidor.nsys.EstadoMovil;
import com.reparaciones.servidor.nsys.MovilNsys;
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

    @Test void amazonCubreTodosLosPedidosQuePuedeAntesDeDarElMasAvanzadoAlMasAntiguo() {
        Map<String, MovilNsys> moviles = new HashMap<>();
        moviles.put("350000000000011", movil("350000000000011", "Taller · Glass", EstadoMovil.STOCK, LocalDate.of(2026, 9, 1)));
        moviles.put("350000000000012", movil("350000000000012", "Listos", EstadoMovil.STOCK, LocalDate.of(2026, 9, 1)));
        CrucePedidos.Resultado r = cruzar(
                List.of(amazon("402-0000000-0000021", 28, "i13128AzulA", 1), amazon("402-0000000-0000022", 30, "i13128AzulA", 1)),
                List.of(new FilaPedidos2026(10, dia(29), "Amazon", "i13128AzulA", "350000000000011"),
                        new FilaPedidos2026(11, dia(30), "Amazon", "i13128AzulA", "350000000000012")),
                List.of(), moviles);
        assertEquals("350000000000011", pedido(r, "402-0000000-0000021").unidades().get(0).imei(),
                "el de Listos solo puede ir al del día 30: el antiguo se queda el del taller");
        assertEquals("350000000000012", pedido(r, "402-0000000-0000022").unidades().get(0).imei());
        assertTrue(pedido(r, "402-0000000-0000022").unidades().get(0).sinPedidoConcreto());
    }

    @Test void unaFilaTecleadaAntesElMismoDiaDelPedidoLoCubre() {
        CrucePedidos.Resultado r = cruzar(List.of(amazon("402-0000000-0000023", 28, "i13128AzulA", 1)),
                List.of(new FilaPedidos2026(12, LocalDate.of(2026, 10, 28).atTime(8, 0), "Amazon", "i13128AzulA", "350000000000013")),
                List.of(), Map.of());
        assertEquals("350000000000013", pedido(r, "402-0000000-0000023").unidades().get(0).imei());
    }

    @Test void elTallerCuentaComoTallerEnMayusculasOMinusculas() {
        Map<String, MovilNsys> moviles = new HashMap<>();
        moviles.put("350000000000014", movil("350000000000014", "Sin revisar", EstadoMovil.STOCK, LocalDate.of(2026, 9, 1)));
        moviles.put("350000000000015", movil("350000000000015", "TALLER · Glass", EstadoMovil.STOCK, LocalDate.of(2026, 9, 20)));
        CrucePedidos.Resultado r = cruzar(List.of(amazon("402-0000000-0000024", 28, "i13128AzulA", 1)),
                List.of(new FilaPedidos2026(13, dia(29), "Amazon", "i13128AzulA", "350000000000014"),
                        new FilaPedidos2026(14, dia(29), "Amazon", "i13128AzulA", "350000000000015")),
                List.of(), moviles);
        assertEquals("350000000000015", pedido(r, "402-0000000-0000024").unidades().get(0).imei());
    }

    @Test void laPosicionCuentaTambienLosArticulosQueNoConoceElMaestro() {
        CrucePedidos.Resultado r = cruzar(List.of(new PedidoShippyPro("402-0000000-0000025", "100", dia(28), "C", "DE",
                        List.of(new PedidoShippyPro.Articulo("i99999RaroA", 1), new PedidoShippyPro.Articulo("i13128NegroA", 1)))),
                List.of(), List.of(), Map.of());
        assertEquals(List.of(2), pedido(r, "402-0000000-0000025").unidades().stream().map(PedidoLeido.Unidad::posicion).toList());
    }

    @Test void unPedidoRepetidoEnLaListaDeShippyProCuentaUnaVez() {
        PedidoShippyPro p = amazon("402-0000000-0000026", 28, "i13128NegroA", 1);
        CrucePedidos.Resultado r = cruzar(List.of(p, p), List.of(), List.of(), Map.of());
        List<PedidoLeido> leidos = r.pedidos().stream().filter(x -> x.numero().equals("402-0000000-0000026")).toList();
        assertEquals(1, leidos.size());
        assertEquals(List.of(1), leidos.get(0).unidades().stream().map(PedidoLeido.Unidad::posicion).toList());
    }

    @Test void simuladorNumeraTodasLasLineasDelPedidoAunqueNoSeanUnidades() {
        CrucePedidos.Resultado r = cruzar(List.of(), List.of(),
                List.of(new FilaSimulador(2, "6460", "X", LocalDate.of(2026, 10, 27), "TecnoSur", "i13128AzulA", 1, "350000000000016"),
                        new FilaSimulador(3, "6460", "X", LocalDate.of(2026, 10, 27), "TecnoSur", "GLS24", 1, null),
                        new FilaSimulador(4, "6460", "X", LocalDate.of(2026, 10, 27), "TecnoSur", "i99999RaroA", 1, "350000000000017"),
                        new FilaSimulador(5, "6460", "X", LocalDate.of(2026, 10, 27), "TecnoSur", "i13128AzulA", 1, "350000000000016"),
                        new FilaSimulador(6, "6460", "X", LocalDate.of(2026, 10, 27), "TecnoSur", "i13128NegroA", 1, null)),
                Map.of());
        assertEquals(List.of(1, 5), pedido(r, "6460").unidades().stream().map(PedidoLeido.Unidad::posicion).toList());
        assertEquals(Set.of("i99999RaroA"), r.desconocidosSimulador());
    }

    @Test void simuladorUnMovilAbonadoQueSeVuelveAVenderEsOtraUnidadAbierta() {
        CrucePedidos.Resultado r = cruzar(List.of(amazon("402-0000000-0000027", 28, "i13128AzulA", 1)),
                List.of(new FilaPedidos2026(40, dia(29), "Amazon", "i13128AzulA", "350000000000018")),
                List.of(new FilaSimulador(10, "6470", "X", LocalDate.of(2026, 10, 20), "Móviles Levante SL", "i13128AzulA", 1, "350000000000018"),
                        new FilaSimulador(50, "6475", "BB", LocalDate.of(2026, 10, 22), "Móviles Levante SL", "i13128AzulA", -1, "350000000000018"),
                        new FilaSimulador(90, "6490", "X", LocalDate.of(2026, 10, 27), "TecnoSur", "i13128AzulA", 1, "350000000000018")),
                Map.of());
        PedidoLeido.Unidad abonada = pedido(r, "6470").unidades().get(0);
        assertEquals("ABONO", abonada.motivoCierre());
        assertNull(abonada.otroOrderId(), "la reventa no es una repetición de la misma venta");
        PedidoLeido.Unidad reventa = pedido(r, "6490").unidades().get(0);
        assertEquals("350000000000018", reventa.imei());
        assertEquals(90, reventa.imeiRef());
        assertNull(reventa.motivoCierre());
        assertTrue(r.pedidos().stream().noneMatch(p -> p.numero().equals("6475")), "una BB no es un pedido");
        assertNull(pedido(r, "402-0000000-0000027").unidades().get(0).imei(), "el móvil está en una unidad abierta del Simulador");
    }

    @Test void unMovilDelSimuladorCerradoPorAbonoPuedeCubrirUnPedidoDeAmazon() {
        CrucePedidos.Resultado r = cruzar(List.of(amazon("402-0000000-0000028", 28, "i13128AzulA", 1)),
                List.of(new FilaPedidos2026(41, dia(29), "Amazon", "i13128AzulA", "350000000000019")),
                List.of(new FilaSimulador(10, "6470", "X", LocalDate.of(2026, 10, 20), "Móviles Levante SL", "i13128AzulA", 1, "350000000000019"),
                        new FilaSimulador(50, "6475", "BB", LocalDate.of(2026, 10, 22), "Móviles Levante SL", "i13128AzulA", -1, "350000000000019")),
                Map.of());
        assertEquals("ABONO", pedido(r, "6470").unidades().get(0).motivoCierre());
        assertEquals("350000000000019", pedido(r, "402-0000000-0000028").unidades().get(0).imei());
    }

    /** Una venta NO se devuelve con otra línea NO de cantidad -1, no con una BB (medido el 05/11: 18 en un año): una cantidad
     *  negativa es un abono, sea cual sea la línea. */
    @Test void simuladorUnaLineaConCantidadNegativaEsUnAbonoAunqueNoSeaBb() {
        CrucePedidos.Resultado r = cruzar(List.of(), List.of(),
                List.of(new FilaSimulador(10, "6470", "NO", LocalDate.of(2026, 10, 20), "TecnoSur", "i13128AzulA", 1, "350000000000020"),
                        new FilaSimulador(50, "6476", "NO", LocalDate.of(2026, 10, 22), "TecnoSur", "i13128AzulA", -1, "350000000000020"),
                        new FilaSimulador(90, "6491", "R1", LocalDate.of(2026, 10, 27), "Móviles Levante SL", "i13128AzulA", 1, "350000000000020"),
                        new FilaSimulador(95, "6492", "NO", LocalDate.of(2026, 10, 28), "TecnoSur", "i13128NegroA", -1, "350000000000021")),
                Map.of());
        PedidoLeido.Unidad devuelta = pedido(r, "6470").unidades().get(0);
        assertEquals("ABONO", devuelta.motivoCierre());
        assertNull(devuelta.otroOrderId(), "la devolución no es otra línea de la misma venta");
        assertNull(pedido(r, "6491").unidades().get(0).motivoCierre(), "revendido: otra unidad abierta");
        assertTrue(r.pedidos().stream().noneMatch(p -> p.numero().equals("6476") || p.numero().equals("6492")),
                "una devolución no es un pedido, aunque su venta quede antes de lo leído");
    }

    private static PedidoShippyPro stockroom(String numero, int d, String sku) {
        return new PedidoShippyPro(numero, "200", dia(d), "Comprador " + numero, "DE", List.of(new PedidoShippyPro.Articulo(sku, 1)));
    }

    @Test void unaFilaAmzCubreUnPedidoDeAmazon() {
        CrucePedidos.Resultado r = cruzar(List.of(amazon("402-0000000-0000041", 28, "i13128AzulA", 1)),
                List.of(new FilaPedidos2026(60, dia(29), "AMZ", "i13128AzulA", "350000000000041")), List.of(), Map.of());
        PedidoLeido.Unidad u = pedido(r, "402-0000000-0000041").unidades().get(0);
        assertEquals("350000000000041", u.imei());
        assertTrue(u.sinPedidoConcreto());
    }

    @Test void unaFilaFnskCubreStockroomYNoAmazon() {
        CrucePedidos.Resultado r = cruzar(List.of(stockroom("402-0000000-0000042", 28, "i13128AzulA"),
                        amazon("402-0000000-0000043", 28, "i13128AzulA", 1)),
                List.of(new FilaPedidos2026(61, dia(29), "FNSK", "i13128AzulA", "350000000000042")), List.of(), Map.of());
        assertEquals("350000000000042", pedido(r, "402-0000000-0000042").unidades().get(0).imei());
        assertTrue(pedido(r, "402-0000000-0000042").unidades().get(0).sinPedidoConcreto());
        assertNull(pedido(r, "402-0000000-0000043").unidades().get(0).imei());
    }

    @Test void unaFilaAmzNoCubreUnPedidoDeStockroom() {
        CrucePedidos.Resultado r = cruzar(List.of(stockroom("402-0000000-0000044", 28, "i13128AzulA")),
                List.of(new FilaPedidos2026(62, dia(29), "AMZ", "i13128AzulA", "350000000000044")), List.of(), Map.of());
        assertNull(pedido(r, "402-0000000-0000044").unidades().get(0).imei());
    }

    @Test void fsnkEnMinusculasCubreStockroom() {
        CrucePedidos.Resultado r = cruzar(List.of(stockroom("402-0000000-0000045", 28, "i13128AzulA")),
                List.of(new FilaPedidos2026(63, dia(29), "fsnk", "i13128AzulA", "350000000000045")), List.of(), Map.of());
        assertEquals("350000000000045", pedido(r, "402-0000000-0000045").unidades().get(0).imei());
    }

    @Test void conUnPedidoDeCadaCanalDelMismoSkuCadaUnoSeQuedaLaSuya() {
        CrucePedidos.Resultado r = cruzar(List.of(amazon("402-0000000-0000046", 28, "i13128AzulA", 1),
                        stockroom("402-0000000-0000047", 28, "i13128AzulA")),
                List.of(new FilaPedidos2026(64, dia(29), "FSNK", "i13128AzulA", "350000000000047"),
                        new FilaPedidos2026(65, dia(29), "AMZ", "i13128AzulA", "350000000000046")), List.of(), Map.of());
        assertEquals("350000000000046", pedido(r, "402-0000000-0000046").unidades().get(0).imei());
        assertEquals("350000000000047", pedido(r, "402-0000000-0000047").unidades().get(0).imei());
    }

    @Test void unaFilaAmzNoSeLaLlevaUnClienteDeWebAunqueSeLlameAmz() {
        CrucePedidos.Resultado r = cruzar(List.of(
                        new PedidoShippyPro("18850", "300", dia(28), "AMZ", "ES", List.of(new PedidoShippyPro.Articulo("i13128AzulA", 1)))),
                List.of(new FilaPedidos2026(66, dia(29), "AMZ", "i13128AzulA", "350000000000048")), List.of(), Map.of());
        assertNull(pedido(r, "18850").unidades().get(0).imei());
    }

    private static FilaPedidos2026 conNumero(int fila, int d, String plataforma, String sku, String imei, String pedidoAmazon) {
        return new FilaPedidos2026(fila, dia(d), plataforma, sku, imei, pedidoAmazon);
    }

    @Test void unaFilaConElNumeroDeUnPedidoPendienteLoCubreAEseYNoAlMasAntiguo() {
        CrucePedidos.Resultado r = cruzar(List.of(amazon("402-0000000-0000051", 27, "i13128AzulA", 1),
                        amazon("402-0000000-0000052", 28, "i13128AzulA", 1)),
                List.of(conNumero(70, 29, "AMZ", "i13128AzulA", "350000000000051", "402-0000000-0000052")), List.of(), Map.of());
        PedidoLeido.Unidad u = pedido(r, "402-0000000-0000052").unidades().get(0);
        assertEquals("350000000000051", u.imei());
        assertFalse(u.sinPedidoConcreto());
        assertNull(pedido(r, "402-0000000-0000051").unidades().get(0).imei());
    }

    @Test void unaFilaConUnNumeroQueNoEstaPendienteNoCubreNingunPedido() {
        CrucePedidos.Resultado r = cruzar(List.of(amazon("402-0000000-0000053", 28, "i13128AzulA", 1)),
                List.of(conNumero(71, 29, "AMZ", "i13128AzulA", "350000000000053", "402-0000000-0000099")), List.of(), Map.of());
        assertNull(pedido(r, "402-0000000-0000053").unidades().get(0).imei());
    }

    @Test void unaFilaTecleadaAntesDeSuPedidoConSuNumeroLoCubreIgual() {
        CrucePedidos.Resultado r = cruzar(List.of(amazon("402-0000000-0000054", 28, "i13128AzulA", 1)),
                List.of(conNumero(72, 20, "AMZ", "i13128AzulA", "350000000000054", " 402-0000000-0000054 ")), List.of(), Map.of());
        PedidoLeido.Unidad u = pedido(r, "402-0000000-0000054").unidades().get(0);
        assertEquals("350000000000054", u.imei());
        assertFalse(u.sinPedidoConcreto());
    }

    @Test void unaFilaFnskConElNumeroDeUnPedidoDeAmazonNoCubreNada() {
        CrucePedidos.Resultado r = cruzar(List.of(amazon("402-0000000-0000055", 28, "i13128AzulA", 1)),
                List.of(conNumero(73, 29, "FNSK", "i13128AzulA", "350000000000055", "402-0000000-0000055")), List.of(), Map.of());
        assertNull(pedido(r, "402-0000000-0000055").unidades().get(0).imei());
    }

    @Test void conDosUnidadesLaFilaConNumeroSeQuedaLaPrimeraYLaOtraPuedeLlevarseLaSinNumero() {
        CrucePedidos.Resultado r = cruzar(List.of(amazon("402-0000000-0000056", 28, "i13128AzulA", 2)),
                List.of(conNumero(74, 29, "AMZ", "i13128AzulA", "350000000000056", "402-0000000-0000056"),
                        new FilaPedidos2026(75, dia(29), "AMZ", "i13128AzulA", "350000000000057")), List.of(), Map.of());
        List<PedidoLeido.Unidad> us = pedido(r, "402-0000000-0000056").unidades();
        assertEquals("350000000000056", us.get(0).imei());
        assertFalse(us.get(0).sinPedidoConcreto());
        assertEquals("350000000000057", us.get(1).imei());
        assertTrue(us.get(1).sinPedidoConcreto());
    }

    @Test void unaFilaConSuNumeroYUnSkuQueNoEncajaVaALaPrimeraUnidadLibreDeSuPedido() {
        CrucePedidos.Resultado r = cruzar(List.of(amazon("402-0000000-0000058", 28, "i13128AzulA", 1)),
                List.of(conNumero(76, 29, "AMZ", "i13128NegroA", "350000000000058", "402-0000000-0000058")), List.of(), Map.of());
        PedidoLeido.Unidad u = pedido(r, "402-0000000-0000058").unidades().get(0);
        assertEquals("350000000000058", u.imei());
        assertFalse(u.sinPedidoConcreto());
    }

    @Test void conNumeroPrimeroLasFilasCuyoSkuEncajaAunqueVayanDespuesDeUnaQueNoEncaja() {
        PedidoShippyPro dos = new PedidoShippyPro("402-0000000-0000060", "100", dia(28), "Comprador A", "DE",
                List.of(new PedidoShippyPro.Articulo("i13128AzulA", 1), new PedidoShippyPro.Articulo("i13128NegroA", 1)));
        CrucePedidos.Resultado r = cruzar(List.of(dos),
                List.of(conNumero(80, 29, "AMZ", "i13128VerdeA", "350000000000061", "402-0000000-0000060"),
                        conNumero(81, 29, "AMZ", "i13128AzulA", "350000000000062", "402-0000000-0000060")), List.of(), Map.of());
        List<PedidoLeido.Unidad> us = pedido(r, "402-0000000-0000060").unidades();
        assertEquals("350000000000062", us.get(0).imei(), "la unidad AzulA se queda la fila de su SKU");
        assertEquals("350000000000061", us.get(1).imei(), "la otra va a la fila que no encajaba");
        assertFalse(us.get(0).sinPedidoConcreto());
        assertFalse(us.get(1).sinPedidoConcreto());
    }
}
