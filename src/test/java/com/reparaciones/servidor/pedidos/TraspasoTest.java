package com.reparaciones.servidor.pedidos;

import com.reparaciones.servidor.pedidos.LectorHistorico.FilaHoja;
import com.reparaciones.servidor.pedidos.Traspaso.Plan;
import com.reparaciones.servidor.pedidos.Traspaso.Traslado;
import com.reparaciones.servidor.pedidos.Traspaso.Via;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TraspasoTest {

    private static final LocalDate HOY = LocalDate.of(2026, 11, 11);
    private static final String SKU = "i13128AzulA";
    private static final String OTRO_SKU = "i14256NegroA";
    private static final String IMEI1 = "350000000000001";
    private static final String IMEI2 = "350000000000002";
    private static final String IMEI3 = "350000000000003";
    private static final String PED1 = "402-0000000-0000001";

    private static int n = 1;

    private static FilaHoja hoja(LocalDate fecha, String plataforma, String sku, String imei, String pedido) {
        return new FilaHoja(n++, fecha, plataforma, sku, imei, pedido, null);
    }

    private static FilaPedido erp(int id, Canal canal, String plataforma, String sku, String imei, String pedido, int pos,
                                  LocalDate fecha, boolean viva) {
        return new FilaPedido(id, fecha, plataforma, sku, imei, pedido, null, "SHIPPYPRO", canal, pos,
                viva ? null : fecha.atTime(12, 0));
    }

    private static FilaPedido amazon(int id, String sku, String imei, String pedido, int pos) {
        return erp(id, Canal.AMAZON, "AMZ", sku, imei, pedido, pos, HOY.minusDays(1), true);
    }

    private static List<Traslado> traslados(Plan p) {
        return p.traslados();
    }

    // ------------------------------------------------------------------ A: por numero de pedido

    @Test void aPoneElImeiEnLaFilaDelMismoPedidoYSkuYNoCargaLaFilaComoHistorico() {
        Plan p = Traspaso.planear(List.of(hoja(HOY.minusDays(1), "AMZ", SKU, IMEI1, PED1)),
                List.of(amazon(10, SKU, null, PED1, 1)), HOY);
        assertEquals(List.of(new Traslado(10, IMEI1, Via.A)), traslados(p));
        assertTrue(p.historico().isEmpty());
        assertEquals(1, p.recuentos().a());
    }

    @Test void aConVariasFilasDelMismoPedidoYSkuLasVaLlenandoEnOrden() {
        Plan p = Traspaso.planear(List.of(hoja(HOY, "AMZ", SKU, IMEI1, PED1), hoja(HOY, "AMZ", SKU, IMEI2, PED1)),
                List.of(amazon(10, SKU, null, PED1, 1), amazon(11, SKU, null, PED1, 2)), HOY);
        assertEquals(List.of(new Traslado(10, IMEI1, Via.A), new Traslado(11, IMEI2, Via.A)), traslados(p));
        assertEquals(2, p.recuentos().a());
        assertTrue(p.historico().isEmpty());
    }

    @Test void aPrefiereLaFilaConElMismoSkuAunqueOtraVayaAntesYSiNingunoCoincideUsaLaPrimeraSinImei() {
        Plan p = Traspaso.planear(List.of(hoja(HOY, "AMZ", SKU, IMEI1, PED1), hoja(HOY, "AMZ", "i99999NadaA", IMEI2, PED1)),
                List.of(amazon(10, OTRO_SKU, null, PED1, 1), amazon(11, SKU, null, PED1, 2), amazon(12, OTRO_SKU, null, PED1, 3)), HOY);
        assertEquals(List.of(new Traslado(11, IMEI1, Via.A), new Traslado(10, IMEI2, Via.A)), traslados(p));
    }

    @Test void aComparaElSkuComoElMaestroUltramarinoEsAzul() {
        Plan p = Traspaso.planear(List.of(hoja(HOY, "AMZ", "i13128UltramarinoA", IMEI1, PED1)),
                List.of(amazon(10, OTRO_SKU, null, PED1, 1), amazon(11, "i13128AzulA", null, PED1, 2)), HOY);
        assertEquals(List.of(new Traslado(11, IMEI1, Via.A)), traslados(p));
    }

    @Test void aFuncionaConFilasCerradasYDeStockroomPeroNoConLasDeLasWebsNiLasDelHistorico() {
        List<FilaPedido> erp = List.of(
                erp(10, Canal.AMAZON_STOCKROOM, "FNSK", SKU, null, PED1, 1, HOY.minusDays(3), false),
                erp(11, Canal.PARTICULARES, "Cliente Web A", SKU, null, "402-0000000-0000002", 1, HOY, true),
                new FilaPedido(12, HOY, "AMZ", SKU, null, "402-0000000-0000003", null, "HISTORICO", null, null, HOY.atStartOfDay()));
        Plan p = Traspaso.planear(List.of(hoja(HOY, "FNSK", SKU, IMEI1, PED1), hoja(HOY, "AMZ", SKU, IMEI2, "402-0000000-0000002"),
                hoja(HOY, "AMZ", SKU, IMEI3, "402-0000000-0000003")), erp, HOY);
        assertEquals(List.of(new Traslado(10, IMEI1, Via.A)), traslados(p));
        assertEquals(2, p.historico().size(), "los pedidos de web o del historico no son de la regla A");
    }

    @Test void aElNumeroDePedidoSeRecortaPeroNoSeCambiaMayusculas() {
        Plan p = Traspaso.planear(List.of(hoja(HOY, "AMZ", SKU, IMEI1, "  " + PED1 + " ")), List.of(amazon(10, SKU, null, PED1, 1)), HOY);
        assertEquals(1, p.recuentos().a());
    }

    @Test void aSiLaFilaDeLaHojaNoTieneUnImeiValidoNoSeTraspasaNiSeCargaComoHistorico() {
        Plan p = Traspaso.planear(List.of(hoja(HOY, "AMZ", SKU, "CANCELADO", PED1), hoja(HOY, "AMZ", SKU, null, PED1),
                hoja(HOY, "AMZ", SKU, "1234", PED1)), List.of(amazon(10, SKU, null, PED1, 1)), HOY);
        assertTrue(traslados(p).isEmpty());
        assertTrue(p.historico().isEmpty());
        assertEquals(3, p.recuentos().casadasSinImei());
    }

    @Test void aSiNoHayFilaDelErpConEseNumeroSeCargaComoHistoricoNormalSinContarNada() {
        Plan p = Traspaso.planear(List.of(hoja(HOY, "AMZ", SKU, IMEI1, "402-0000000-0000009")), List.of(amazon(10, SKU, null, PED1, 1)), HOY);
        assertEquals(1, p.historico().size());
        assertEquals(new Traspaso.Recuentos(0, 0, 0, 0, 0, 0, 0), p.recuentos());
    }

    @Test void aLaFilaDestinoQueYaTieneElMismoImeiEsYaPuestoYNoSeCarga() {
        Plan p = Traspaso.planear(List.of(hoja(HOY, "AMZ", SKU, IMEI1, PED1)),
                List.of(amazon(10, SKU, IMEI1, PED1, 1), amazon(11, SKU, null, PED1, 2)), HOY);
        assertTrue(traslados(p).isEmpty(), "no se pone el mismo IMEI en la otra fila del pedido");
        assertTrue(p.historico().isEmpty());
        assertEquals(1, p.recuentos().yaPuesto());
    }

    @Test void aSiTodasLasFilasDelPedidoTienenOtroImeiEsConflictoYNoSePisaNiSeCarga() {
        Plan p = Traspaso.planear(List.of(hoja(HOY, "AMZ", SKU, IMEI1, PED1)),
                List.of(amazon(10, SKU, IMEI2, PED1, 1), amazon(11, SKU, IMEI3, PED1, 2)), HOY);
        assertTrue(traslados(p).isEmpty());
        assertTrue(p.historico().isEmpty());
        assertEquals(1, p.recuentos().conflicto());
    }

    @Test void aUnMismoImeiDosVecesEnLaHojaSoloSePoneUnaVez() {
        Plan p = Traspaso.planear(List.of(hoja(HOY, "AMZ", SKU, IMEI1, PED1), hoja(HOY, "AMZ", SKU, IMEI1, PED1)),
                List.of(amazon(10, SKU, null, PED1, 1), amazon(11, SKU, null, PED1, 2)), HOY);
        assertEquals(List.of(new Traslado(10, IMEI1, Via.A)), traslados(p));
        assertEquals(1, p.recuentos().yaPuesto());
    }

    @Test void aSiHayMasFilasDeLaHojaQueDelErpLasQueSobranSonConflicto() {
        Plan p = Traspaso.planear(List.of(hoja(HOY, "AMZ", SKU, IMEI1, PED1), hoja(HOY, "AMZ", SKU, IMEI2, PED1)),
                List.of(amazon(10, SKU, null, PED1, 1)), HOY);
        assertEquals(List.of(new Traslado(10, IMEI1, Via.A)), traslados(p));
        assertEquals(1, p.recuentos().conflicto());
        assertTrue(p.historico().isEmpty());
    }

    @Test void aNoTieneLimiteDeDias() {
        Plan p = Traspaso.planear(List.of(hoja(HOY.minusDays(200), "AMZ", SKU, IMEI1, PED1)), List.of(amazon(10, SKU, null, PED1, 1)), HOY);
        assertEquals(1, p.recuentos().a());
    }

    // ------------------------------------------------------------------ B: Amazon sin numero

    @Test void bRepartePorElMasAntiguoYCuandoSeAcabanLasVivasElRestoVaAlHistoricoComoSinPareja() {
        List<FilaPedido> erp = List.of(
                erp(21, Canal.AMAZON, "AMZ", SKU, null, "402-0000000-0000002", 1, HOY.minusDays(2), true),
                erp(20, Canal.AMAZON, "AMZ", SKU, null, "402-0000000-0000001", 1, HOY.minusDays(2), true),
                erp(22, Canal.AMAZON, "AMZ", SKU, null, "402-0000000-0000003", 1, HOY.minusDays(5), true));
        Plan p = Traspaso.planear(List.of(hoja(HOY.minusDays(6), "AMZ", SKU, IMEI1, null), hoja(HOY.minusDays(6), "AMZ", SKU, IMEI2, null),
                hoja(HOY.minusDays(5), "AMZ", SKU, IMEI3, null), hoja(HOY.minusDays(5), "AMZ", SKU, "350000000000004", null)), erp, HOY);
        // Orden de las filas vivas: (fecha, id) = 22, 20, 21; la cuarta fila de la hoja se queda sin pareja.
        assertEquals(List.of(new Traslado(22, IMEI1, Via.B), new Traslado(20, IMEI2, Via.B), new Traslado(21, IMEI3, Via.B)), traslados(p));
        assertEquals(1, p.historico().size());
        assertEquals("350000000000004", p.historico().get(0).imei());
        assertEquals(3, p.recuentos().b());
        assertEquals(1, p.recuentos().sinPareja());
    }

    @Test void bSoloCuentaElCanalYElSkuDeLaFilaYSoloLasPendientes() {
        List<FilaPedido> erp = List.of(
                erp(20, Canal.AMAZON_STOCKROOM, "FNSK", SKU, null, PED1, 1, HOY, true),
                erp(21, Canal.AMAZON, "AMZ", OTRO_SKU, null, "402-0000000-0000002", 1, HOY, true),
                erp(22, Canal.AMAZON, "AMZ", SKU, null, "402-0000000-0000003", 1, HOY.minusDays(1), false),
                erp(23, Canal.AMAZON, "AMZ", SKU, null, "402-0000000-0000004", 1, HOY, true));
        Plan p = Traspaso.planear(List.of(hoja(HOY, "AMZ", SKU, IMEI1, null)), erp, HOY);
        assertEquals(List.of(new Traslado(23, IMEI1, Via.B)), traslados(p));
    }

    @Test void bFnskYFsnkSonStockroomYSeEscribenComoSea() {
        List<FilaPedido> erp = List.of(erp(20, Canal.AMAZON_STOCKROOM, "FNSK", SKU, null, PED1, 1, HOY, true),
                erp(21, Canal.AMAZON_STOCKROOM, "FNSK", SKU, null, "402-0000000-0000002", 1, HOY, true));
        Plan p = Traspaso.planear(List.of(hoja(HOY, " fnsk ", SKU, IMEI1, null), hoja(HOY, "FSNK", SKU, IMEI2, "  ")), erp, HOY);
        assertEquals(List.of(new Traslado(20, IMEI1, Via.B), new Traslado(21, IMEI2, Via.B)), traslados(p));
    }

    @Test void bExigeImeiValidoYFechaDeLosUltimos14Dias() {
        List<FilaPedido> erp = List.of(amazon(20, SKU, null, PED1, 1));
        Plan p = Traspaso.planear(List.of(hoja(HOY.minusDays(15), "AMZ", SKU, IMEI1, null), hoja(HOY, "AMZ", SKU, "NO HAY", null),
                hoja(HOY, "AMZ", SKU, null, null)), erp, HOY);
        assertTrue(traslados(p).isEmpty());
        assertEquals(3, p.historico().size());
        assertEquals(0, p.recuentos().sinPareja(), "no se intento emparejar: no es «sin pareja»");

        Plan limite = Traspaso.planear(List.of(hoja(HOY.minusDays(14), "AMZ", SKU, IMEI1, null)), erp, HOY);
        assertEquals(1, limite.recuentos().b(), "el dia 14 todavia vale");
    }

    @Test void bSiYaHayUnaFilaDelCanalYSkuConEseImeiEsYaPuestoYNoSePoneEnOtra() {
        List<FilaPedido> erp = List.of(amazon(20, SKU, IMEI1, PED1, 1), amazon(21, SKU, null, "402-0000000-0000002", 1));
        Plan p = Traspaso.planear(List.of(hoja(HOY, "AMZ", SKU, IMEI1, null)), erp, HOY);
        assertTrue(traslados(p).isEmpty());
        assertTrue(p.historico().isEmpty());
        assertEquals(1, p.recuentos().yaPuesto());
    }

    @Test void bSiTodasLasPendientesTienenOtroImeiNoHayPareja() {
        Plan p = Traspaso.planear(List.of(hoja(HOY, "AMZ", SKU, IMEI1, null)), List.of(amazon(20, SKU, IMEI2, PED1, 1)), HOY);
        assertTrue(traslados(p).isEmpty());
        assertEquals(1, p.historico().size());
        assertEquals(1, p.recuentos().sinPareja());
    }

    // ------------------------------------------------------------------ C: webs

    private static FilaPedido web(int id, Canal canal, String plataforma, String sku, String imei, LocalDate fecha) {
        return erp(id, canal, plataforma, sku, imei, "W-" + id, 1, fecha, true);
    }

    @Test void cCasaPorNombreDePlataformaSinMayusculasTildesNiEspaciosRepetidosYPorSku() {
        List<FilaPedido> erp = List.of(
                web(30, Canal.PARTICULARES, "Cliente  Web Á", SKU, null, HOY),
                web(31, Canal.DISTRIBUIDORES, "Cliente Web B", SKU, null, HOY));
        Plan p = Traspaso.planear(List.of(hoja(HOY, " cliente web a ", SKU, IMEI1, null)), erp, HOY);
        assertEquals(List.of(new Traslado(30, IMEI1, Via.C)), traslados(p));
        assertEquals(1, p.recuentos().c());
        assertTrue(p.historico().isEmpty());
    }

    @Test void cValeParaLosTresCanalesWebYReparteDeMasAntiguaAMasNueva() {
        List<FilaPedido> erp = List.of(web(31, Canal.BACKIPHONE, "Cliente Web A", SKU, null, HOY),
                web(30, Canal.DISTRIBUIDORES, "Cliente Web A", SKU, null, HOY.minusDays(1)),
                web(32, Canal.PARTICULARES, "Cliente Web A", SKU, null, HOY.minusDays(1)));
        Plan p = Traspaso.planear(List.of(hoja(HOY, "Cliente Web A", SKU, IMEI1, null), hoja(HOY, "Cliente Web A", SKU, IMEI2, null),
                hoja(HOY, "Cliente Web A", SKU, IMEI3, null), hoja(HOY, "Cliente Web A", SKU, "350000000000004", null)), erp, HOY);
        assertEquals(List.of(new Traslado(30, IMEI1, Via.C), new Traslado(32, IMEI2, Via.C), new Traslado(31, IMEI3, Via.C)), traslados(p));
        assertEquals(1, p.recuentos().sinPareja());
        assertEquals(1, p.historico().size());
    }

    @Test void cNoAplicaANingunaDeAmazonNiIncidenciasNiNpNiPlataformaVacia() {
        List<FilaPedido> erp = List.of(web(30, Canal.PARTICULARES, "INCIDENCIA 1", SKU, null, HOY),
                web(31, Canal.PARTICULARES, "NP", SKU, null, HOY), web(32, Canal.PARTICULARES, "", SKU, null, HOY),
                web(33, Canal.PARTICULARES, "AMZ", SKU, null, HOY));
        Plan p = Traspaso.planear(List.of(hoja(HOY, "INCIDENCIA 1", SKU, IMEI1, null), hoja(HOY, "np", SKU, IMEI2, null),
                hoja(HOY, "", SKU, IMEI3, null), hoja(HOY, "incidencia amz", SKU, "350000000000004", null)), erp, HOY);
        assertTrue(traslados(p).isEmpty());
        assertEquals(4, p.historico().size());
        assertEquals(0, p.recuentos().sinPareja());
    }

    @Test void cExigeImeiValidoYLosUltimos14Dias() {
        List<FilaPedido> erp = List.of(web(30, Canal.PARTICULARES, "Cliente Web A", SKU, null, HOY),
                web(31, Canal.PARTICULARES, "Cliente Web A", SKU, null, HOY));
        Plan p = Traspaso.planear(List.of(hoja(HOY.minusDays(15), "Cliente Web A", SKU, IMEI1, null),
                hoja(HOY, "Cliente Web A", SKU, "CANCELADO", null)), erp, HOY);
        assertTrue(traslados(p).isEmpty());
        assertEquals(2, p.historico().size());
    }

    @Test void cSiLaFilaDestinoYaTieneEseImeiEsYaPuesto() {
        List<FilaPedido> erp = List.of(web(30, Canal.PARTICULARES, "Cliente Web A", SKU, IMEI1, HOY),
                web(31, Canal.PARTICULARES, "Cliente Web A", SKU, null, HOY));
        Plan p = Traspaso.planear(List.of(hoja(HOY, "Cliente Web A", SKU, IMEI1, null)), erp, HOY);
        assertTrue(traslados(p).isEmpty());
        assertEquals(1, p.recuentos().yaPuesto());
        assertTrue(p.historico().isEmpty());
    }

    @Test void cNoMiraElNumeroDePedidoYAManda_SiElNumeroCasaConUnaFilaAmazon() {
        // Sin fila de Amazon con ese numero, una web con «Pedido Amazon» escrito sigue siendo de C (la regla no lo mira).
        Plan p = Traspaso.planear(List.of(hoja(HOY, "Cliente Web A", SKU, IMEI1, PED1)),
                List.of(web(30, Canal.PARTICULARES, "Cliente Web A", SKU, null, HOY)), HOY);
        assertEquals(List.of(new Traslado(30, IMEI1, Via.C)), traslados(p));
        // Con una fila de Amazon con ese numero, manda A.
        Plan a = Traspaso.planear(List.of(hoja(HOY, "Cliente Web A", SKU, IMEI1, PED1)),
                List.of(web(30, Canal.PARTICULARES, "Cliente Web A", SKU, null, HOY), amazon(10, SKU, null, PED1, 1)), HOY);
        assertEquals(List.of(new Traslado(10, IMEI1, Via.A)), traslados(a));
    }

    // ------------------------------------------------------------------ comunes

    @Test void unImeiNuncaSePoneEnMasDeUnaFilaNiUnaFilaRecibeDos() {
        List<FilaPedido> erp = List.of(amazon(20, SKU, null, PED1, 1), amazon(21, SKU, null, "402-0000000-0000002", 1));
        Plan p = Traspaso.planear(List.of(hoja(HOY, "AMZ", SKU, IMEI1, null), hoja(HOY, "AMZ", SKU, IMEI1, null)), erp, HOY);
        assertEquals(1, traslados(p).size());
        assertEquals(1, p.recuentos().yaPuesto());
    }

    @Test void lasFilasQueSeCarganComoHistoricoConservanElOrdenDeLaHoja() {
        Plan p = Traspaso.planear(List.of(hoja(HOY, "X", SKU, IMEI1, null), hoja(HOY, "AMZ", SKU, IMEI2, PED1), hoja(HOY, "Y", SKU, IMEI3, null)),
                List.of(amazon(10, SKU, null, PED1, 1)), HOY);
        assertEquals(List.of(IMEI1, IMEI3), p.historico().stream().map(FilaHoja::imei).toList());
    }

    @Test void sinFilasDelErpTodoVaAlHistorico() {
        List<FilaHoja> filas = new ArrayList<>();
        for (int i = 0; i < 5; i++) filas.add(hoja(HOY, "AMZ", SKU, IMEI1, PED1));
        Plan p = Traspaso.planear(filas, List.of(), HOY);
        assertEquals(5, p.historico().size());
        assertEquals(0, p.recuentos().sinPareja());
    }

    // ------------------------------------------------------------------ idempotencia

    @Test void repetirElTraspasoConElResultadoDeLaPrimeraVezNoTraspasaNadaYCargaElMismoHistorico() {
        List<FilaHoja> hoja = List.of(hoja(HOY, "AMZ", SKU, IMEI1, PED1), hoja(HOY, "AMZ", SKU, IMEI2, null),
                hoja(HOY, "Cliente Web A", SKU, IMEI3, null), hoja(HOY, "AMZ", SKU, "350000000000004", null), hoja(HOY, "Z", SKU, "CANCELADO", null));
        List<FilaPedido> erp = new ArrayList<>(List.of(amazon(10, SKU, null, PED1, 1), amazon(11, SKU, null, "402-0000000-0000002", 1),
                web(12, Canal.PARTICULARES, "Cliente Web A", SKU, null, HOY)));

        Plan primera = Traspaso.planear(hoja, erp, HOY);
        assertEquals(3, primera.traslados().size());

        // Se aplica el traspaso a las filas del ERP, como hace el DAO.
        List<FilaPedido> despues = new ArrayList<>();
        for (FilaPedido f : erp) {
            String imei = primera.traslados().stream().filter(t -> t.idFila() == f.id()).map(Traslado::imei).findFirst().orElse(f.imei());
            despues.add(new FilaPedido(f.id(), f.fecha(), f.plataforma(), f.sku(), imei, f.pedido(), null, f.origen(), f.canal(), f.posicion(), f.cerradaEn()));
        }
        Plan segunda = Traspaso.planear(hoja, despues, HOY);

        assertTrue(segunda.traslados().isEmpty());
        assertEquals(primera.historico(), segunda.historico());
        assertEquals(3, segunda.recuentos().yaPuesto());
        assertEquals(primera.recuentos().sinPareja(), segunda.recuentos().sinPareja());
    }
}
