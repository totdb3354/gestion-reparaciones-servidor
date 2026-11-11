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

    private static FilaPedido cerrada(int id, Canal canal, String pedido, int posicion) {
        return new FilaPedido(id, HOY, "AMZ", "i13128AzulA", "350000000000001", pedido, HOY, "SHIPPYPRO", canal, posicion,
                HOY.atStartOfDay());
    }

    private static SincroShippyPro.Plan plan(List<PedidoShippyPro> pendientes, List<FilaPedido> vivas) {
        return SincroShippyPro.de(pendientes, CANALES, ACEPTA, vivas, List.of(), HOY);
    }

    private static SincroShippyPro.Plan plan(List<PedidoShippyPro> pendientes, List<FilaPedido> vivas, List<FilaPedido> cerradas) {
        return SincroShippyPro.de(pendientes, CANALES, ACEPTA, vivas, cerradas, HOY);
    }

    @Test void unaFilaCerradaDeUnPedidoPendienteSeReabreYNoSeInserta() {
        SincroShippyPro.Plan p = plan(List.of(pedido("402-0000000-0000011", "100", null, art("i13128AzulA", 1))), List.of(),
                List.of(cerrada(4, Canal.AMAZON, "402-0000000-0000011", 1)));
        assertEquals(List.of(4), p.reabrir());
        assertTrue(p.insertar().isEmpty());
        assertTrue(p.cerrar().isEmpty());
    }

    @Test void unaPosicionNuevaDeUnPedidoConFilaCerradaSeInsertaYLaCerradaSeReabre() {
        SincroShippyPro.Plan p = plan(List.of(pedido("402-0000000-0000011", "100", null, art("i13128AzulA", 2))), List.of(),
                List.of(cerrada(4, Canal.AMAZON, "402-0000000-0000011", 1)));
        assertEquals(List.of(4), p.reabrir());
        assertEquals(List.of(2), p.insertar().stream().map(FilaPedido::posicion).toList());
    }

    @Test void unaFilaCerradaDeOtroCanalOPedidoNoSeReabre() {
        SincroShippyPro.Plan p = plan(List.of(pedido("402-0000000-0000011", "100", null, art("i13128AzulA", 1))), List.of(),
                List.of(cerrada(4, Canal.PARTICULARES, "402-0000000-0000011", 1), cerrada(5, Canal.AMAZON, "402-0000000-0000012", 1)));
        assertTrue(p.reabrir().isEmpty());
        assertEquals(1, p.insertar().size());
    }

    // ---------------------------------------------------------------- envios (spec 6 bis)

    private static ClienteShippyPro.EnvioShippyPro envio(String numero, String marketplace, int dia, String seguimiento) {
        return new ClienteShippyPro.EnvioShippyPro(numero, marketplace, LocalDateTime.of(2026, 11, dia, 12, 0), "UPSv2", seguimiento);
    }

    private static FilaPedido conEnvio(int id, Canal canal, String pedido) {
        return new FilaPedido(id, HOY, "AMZ", "i13128AzulA", null, pedido, HOY, "SHIPPYPRO", canal, 1, HOY.atStartOfDay(),
                HOY.atStartOfDay(), "UPSv2", "1Z0000000000000009");
    }

    @Test void seMarcanLasFilasVivasYLasCerradasSinEnvioDeUnPedidoEnviado() {
        List<SincroShippyPro.MarcaEnvio> m = SincroShippyPro.marcarEnviadas(
                List.of(envio("402-0000000-0000011", "100", 9, "1Z0000000000000001")), CANALES, Set.of(),
                List.of(viva(1, Canal.AMAZON, "402-0000000-0000011", 1), cerrada(2, Canal.AMAZON, "402-0000000-0000011", 2)));
        assertEquals(List.of(
                new SincroShippyPro.MarcaEnvio(1, LocalDateTime.of(2026, 11, 9, 12, 0), "UPSv2", "1Z0000000000000001", null, null),
                new SincroShippyPro.MarcaEnvio(2, LocalDateTime.of(2026, 11, 9, 12, 0), "UPSv2", "1Z0000000000000001", null, null)), m);
    }

    private static ClienteShippyPro.EnvioShippyPro envioImpreso(String numero, int dia, Long id, LocalDateTime impresaEn) {
        return new ClienteShippyPro.EnvioShippyPro(numero, "100", LocalDateTime.of(2026, 11, dia, 12, 0), "UPSv2", "x", id, impresaEn);
    }

    private static FilaPedido enviadaCon(int id, String pedido, Long envioId, LocalDateTime impresaEn) {
        return new FilaPedido(id, HOY, "AMZ", "i13128AzulA", null, pedido, HOY, "SHIPPYPRO", Canal.AMAZON, 1, HOY.atStartOfDay(),
                LocalDateTime.of(2026, 11, 9, 12, 0), "UPSv2", "x", null, envioId, impresaEn);
    }

    @Test void alMarcarUnEnvioSeGuardanElIdYLaImpresion() {
        LocalDateTime imp = LocalDateTime.of(2026, 11, 9, 13, 0);
        List<SincroShippyPro.MarcaEnvio> m = SincroShippyPro.marcarEnviadas(
                List.of(envioImpreso("402-0000000-0000011", 9, 777L, imp), envioImpreso("402-0000000-0000012", 9, 778L, null)),
                CANALES, Set.of(), List.of(viva(1, Canal.AMAZON, "402-0000000-0000011", 1),
                        viva(2, Canal.AMAZON, "402-0000000-0000012", 1)));
        assertEquals(777L, m.get(0).envioId());
        assertEquals(imp, m.get(0).impresaEn());
        assertEquals(778L, m.get(1).envioId());
        assertNull(m.get(1).impresaEn());
    }

    @Test void unaFilaYaEnviadaSinImprimirPasaAImpresaEnUnaVueltaPosterior() {
        LocalDateTime imp = LocalDateTime.of(2026, 11, 9, 15, 0);
        List<SincroShippyPro.MarcaEtiqueta> m = SincroShippyPro.completarEtiquetas(
                List.of(envioImpreso("402-0000000-0000011", 9, 777L, imp)), CANALES, Set.of(),
                List.of(enviadaCon(1, "402-0000000-0000011", 777L, null)));
        assertEquals(List.of(new SincroShippyPro.MarcaEtiqueta(1, null, imp)), m);
    }

    @Test void unaFilaEnviadaSinIdDeEnvioLoRecibeAunqueLaEtiquetaNoEsteImpresa() {
        List<SincroShippyPro.MarcaEtiqueta> m = SincroShippyPro.completarEtiquetas(
                List.of(envioImpreso("402-0000000-0000011", 9, 777L, null)), CANALES, Set.of(),
                List.of(enviadaCon(1, "402-0000000-0000011", null, null)));
        assertEquals(List.of(new SincroShippyPro.MarcaEtiqueta(1, 777L, null)), m);
    }

    @Test void loYaImpresoNoSeTocaNiSeMezclaConOtraExpedicion() {
        LocalDateTime antes = LocalDateTime.of(2026, 11, 9, 14, 0);
        // ya tiene id e impresion: nada que completar
        assertTrue(SincroShippyPro.completarEtiquetas(
                List.of(envioImpreso("402-0000000-0000011", 9, 777L, antes.plusHours(1))), CANALES, Set.of(),
                List.of(enviadaCon(1, "402-0000000-0000011", 777L, antes))).isEmpty());
        // ShippyPro ya no la da por impresa (is_printed 0): no se borra nada
        assertTrue(SincroShippyPro.completarEtiquetas(
                List.of(envioImpreso("402-0000000-0000011", 9, 777L, null)), CANALES, Set.of(),
                List.of(enviadaCon(1, "402-0000000-0000011", 777L, antes))).isEmpty());
        // la fila guarda el id de otra expedicion: no se le pone la impresion de esta
        assertTrue(SincroShippyPro.completarEtiquetas(
                List.of(envioImpreso("402-0000000-0000011", 10, 999L, antes)), CANALES, Set.of(),
                List.of(enviadaCon(1, "402-0000000-0000011", 777L, null))).isEmpty());
    }

    @Test void completarEtiquetasIgnoraFilasSinEnvioPendientesYEnviosDeOtroCanalOAnterioresALaReapertura() {
        LocalDateTime imp = LocalDateTime.of(2026, 11, 9, 15, 0);
        List<ClienteShippyPro.EnvioShippyPro> envios = List.of(envioImpreso("402-0000000-0000011", 9, 777L, imp),
                envioImpreso("402-0000000-0000012", 9, 778L, imp));
        Set<SincroShippyPro.ClavePedido> pendientes = Set.of(new SincroShippyPro.ClavePedido(Canal.AMAZON, "402-0000000-0000012"));
        assertTrue(SincroShippyPro.completarEtiquetas(envios, CANALES, pendientes, List.of(
                viva(1, Canal.AMAZON, "402-0000000-0000011", 1), // sin envio: es de marcarEnviadas
                enviadaCon(2, "402-0000000-0000012", null, null), // sigue pendiente
                enviadaCon(3, "402-0000000-0000099", null, null), // sin envio leido
                new FilaPedido(4, HOY, "AMZ", "i13128AzulA", null, "402-0000000-0000011", HOY, "SHIPPYPRO", Canal.PARTICULARES, 1,
                        HOY.atStartOfDay(), LocalDateTime.of(2026, 11, 9, 12, 0), "UPSv2", "x", null, null, null), // otro canal
                new FilaPedido(5, HOY, "AMZ", "i13128AzulA", null, "402-0000000-0000011", HOY, "SHIPPYPRO", Canal.AMAZON, 1,
                        HOY.atStartOfDay(), LocalDateTime.of(2026, 11, 9, 12, 0), "UPSv2", "x",
                        LocalDateTime.of(2026, 11, 9, 13, 0), null, null))).isEmpty()); // envio anterior a la reapertura
    }

    @Test void noSeMarcaUnaFilaQueYaTieneEnvioNiUnPedidoQueSigueEntreLosPendientes() {
        Set<SincroShippyPro.ClavePedido> pendientes = Set.of(new SincroShippyPro.ClavePedido(Canal.AMAZON, "402-0000000-0000012"));
        List<SincroShippyPro.MarcaEnvio> m = SincroShippyPro.marcarEnviadas(
                List.of(envio("402-0000000-0000011", "100", 9, "a"), envio("402-0000000-0000012", "100", 9, "b")), CANALES,
                pendientes, List.of(conEnvio(1, Canal.AMAZON, "402-0000000-0000011"),
                        viva(2, Canal.AMAZON, "402-0000000-0000012", 1)));
        assertTrue(m.isEmpty());
    }

    @Test void conVariasExpedicionesDelMismoPedidoGanaLaMasReciente() {
        List<SincroShippyPro.MarcaEnvio> m = SincroShippyPro.marcarEnviadas(
                List.of(envio("402-0000000-0000011", "100", 8, "vieja"), envio("402-0000000-0000011", "100", 9, "nueva"),
                        envio("402-0000000-0000011", "100", 7, "antigua")), CANALES, Set.of(),
                List.of(viva(1, Canal.AMAZON, "402-0000000-0000011", 1)));
        assertEquals("nueva", m.get(0).seguimiento());
    }

    @Test void unEnvioDeUnMarketplaceSinCanalOSinNumeroSeIgnoraYElCanalDistingueElPedido() {
        List<SincroShippyPro.MarcaEnvio> m = SincroShippyPro.marcarEnviadas(
                List.of(envio("402-0000000-0000011", "999", 9, "a"), envio("", "100", 9, "b"),
                        envio("402-0000000-0000012", "200", 9, "c")), CANALES, Set.of(),
                List.of(viva(1, Canal.AMAZON, "402-0000000-0000011", 1), viva(2, Canal.AMAZON, "402-0000000-0000012", 1),
                        viva(3, Canal.PARTICULARES, "402-0000000-0000012", 1)));
        assertEquals(List.of(3), m.stream().map(SincroShippyPro.MarcaEnvio::fila).toList());
    }

    private static FilaPedido reabierta(int id, String pedido, LocalDateTime reabiertaEn) {
        return new FilaPedido(id, HOY, "AMZ", "i13128AzulA", null, pedido, HOY, "SHIPPYPRO", Canal.AMAZON, 1, HOY.atStartOfDay(),
                null, null, null, reabiertaEn);
    }

    @Test void unaFilaReabiertaNoAceptaUnEnvioAnteriorALaReaperturaPeroSiUnoPosterior() {
        FilaPedido f = reabierta(1, "402-0000000-0000011", LocalDateTime.of(2026, 11, 9, 13, 0));
        // envio del dia 9 a las 12:00: anterior a la reapertura (13:00); el del dia 10, posterior
        assertTrue(SincroShippyPro.marcarEnviadas(List.of(envio("402-0000000-0000011", "100", 9, "viejo")), CANALES, Set.of(),
                List.of(f)).isEmpty());
        List<SincroShippyPro.MarcaEnvio> m = SincroShippyPro.marcarEnviadas(
                List.of(envio("402-0000000-0000011", "100", 9, "viejo"), envio("402-0000000-0000011", "100", 10, "nuevo")),
                CANALES, Set.of(), List.of(f));
        assertEquals(List.of("nuevo"), m.stream().map(SincroShippyPro.MarcaEnvio::seguimiento).toList());
        // justo a la hora de la reapertura tampoco vale
        assertTrue(SincroShippyPro.marcarEnviadas(List.of(envio("402-0000000-0000011", "100", 9, "x")), CANALES, Set.of(),
                List.of(reabierta(1, "402-0000000-0000011", LocalDateTime.of(2026, 11, 9, 12, 0)))).isEmpty());
    }

    @Test void unPedidoConVariasPosicionesDaElMismoEnvioATodasSusFilas() {
        List<SincroShippyPro.MarcaEnvio> m = SincroShippyPro.marcarEnviadas(
                List.of(envio("402-0000000-0000011", "100", 9, "1Z0000000000000001")), CANALES, Set.of(),
                List.of(viva(1, Canal.AMAZON, "402-0000000-0000011", 1), viva(2, Canal.AMAZON, "402-0000000-0000011", 2),
                        viva(3, Canal.AMAZON, "402-0000000-0000011", 3)));
        assertEquals(List.of(1, 2, 3), m.stream().map(SincroShippyPro.MarcaEnvio::fila).toList());
        assertEquals(1, m.stream().map(SincroShippyPro.MarcaEnvio::seguimiento).distinct().count());
    }

    @Test void elMarketplaceCeroDeLosEnviosManualesSeIgnoraSiNoTieneCanal() {
        assertTrue(SincroShippyPro.marcarEnviadas(List.of(envio("402-0000000-0000011", "0", 9, "a")), CANALES, Set.of(),
                List.of(viva(1, Canal.AMAZON, "402-0000000-0000011", 1))).isEmpty());
    }

    @Test void clavesEnviadasSonLasDeLosPedidosConCanalYNumero() {
        assertEquals(Set.of(new SincroShippyPro.ClavePedido(Canal.AMAZON, "402-0000000-0000011")),
                SincroShippyPro.clavesEnviadas(List.of(envio(" 402-0000000-0000011 ", "100", 9, "a"),
                        envio("402-0000000-0000012", "999", 9, "b")), CANALES));
    }

    @Test void clavesPendientesNormalizaElNumeroYSaltaLosDeCanalDesconocido() {
        assertEquals(Set.of(new SincroShippyPro.ClavePedido(Canal.AMAZON, "402-0000000-0000011")),
                SincroShippyPro.clavesPendientes(List.of(
                        pedido(" 402-0000000-0000011 ", "100", null, art("i13128AzulA", 1)),
                        pedido("402-0000000-0000012", "999", null, art("i13128AzulA", 1)),
                        pedido(" ", "100", null, art("i13128AzulA", 1))), CANALES));
    }

    @Test void unaLineaConMuchasUnidadesSeCortaA50() {
        SincroShippyPro.Plan p = plan(List.of(pedido("402-0000000-0000011", "100", null, art("i13128AzulA", 1_000_000))), List.of());
        assertEquals(SincroShippyPro.MAX_UNIDADES, p.insertar().size());
        assertEquals(50, SincroShippyPro.MAX_UNIDADES);
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

    @Test void unNumeroConEspaciosCoincideConLaFilaVivaYNoInsertaNiCierra() {
        SincroShippyPro.Plan p = plan(List.of(pedido("  402-0000000-0000011 ", "100", null, art("i13128AzulA", 1))),
                List.of(viva(7, Canal.AMAZON, "402-0000000-0000011", 1)));
        assertTrue(p.insertar().isEmpty());
        assertTrue(p.cerrar().isEmpty());
    }

    @Test void elNumeroSeGuardaSinEspaciosYCortadoAlAnchoDeLaColumna() {
        String largo = "4".repeat(AnchosPedidos.PEDIDO + 10);
        FilaPedido f = plan(List.of(pedido(" " + largo + " ", "100", null, art("i13128AzulA", 1))), List.of()).insertar().get(0);
        assertEquals("4".repeat(AnchosPedidos.PEDIDO), f.pedido());
    }

    @Test void elMismoPedidoDosVecesContinuaLasPosiciones() {
        SincroShippyPro.Plan p = plan(List.of(
                pedido("402-0000000-0000011", "100", null, art("i13128AzulA", 2)),
                pedido("402-0000000-0000011", "100", null, art("i13128AzulA", 1))), List.of());
        assertEquals(List.of(1, 2, 3), p.insertar().stream().map(FilaPedido::posicion).toList());
    }

    @Test void unPedidoConTodosLosSkuDesconocidosNoCierraSusFilasVivas() {
        SincroShippyPro.Plan p = plan(List.of(pedido("402-0000000-0000011", "100", null, art("i99999RosaA", 1))),
                List.of(viva(7, Canal.AMAZON, "402-0000000-0000011", 1)));
        assertTrue(p.cerrar().isEmpty());
        assertTrue(p.insertar().isEmpty());
    }

    @Test void unSkuDesconocidoEntreConocidosNoDesplazaLasPosiciones() {
        SincroShippyPro.Plan p = plan(List.of(pedido("402-0000000-0000011", "100", null,
                art("i13128AzulA", 1), art("i99999RosaA", 1), art("i13128AzulA", 1))), List.of());
        assertEquals(List.of(1, 3), p.insertar().stream().map(FilaPedido::posicion).toList());
    }
}
