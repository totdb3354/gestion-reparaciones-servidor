package com.reparaciones.servidor.pedidos;

import com.reparaciones.servidor.nsys.EstadoMovil;
import com.reparaciones.servidor.nsys.Importacion;
import com.reparaciones.servidor.nsys.ImportadorNsys;
import com.reparaciones.servidor.nsys.LoQueVale;
import com.reparaciones.servidor.nsys.MovilNsys;
import com.reparaciones.servidor.nsys.NsysDAO;
import com.reparaciones.servidor.nsys.TallerNsys;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class VistaFilasTest {

    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");
    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 11, 10, 10, 0);
    private static final String IMEI1 = "350000000000001";
    private static final String IMEI2 = "350000000000002";
    private final FilasDAO dao = mock(FilasDAO.class);
    private final NsysDAO nsys = mock(NsysDAO.class);
    private final TallerNsys taller = mock(TallerNsys.class);
    private final CalendarioLaboral calendario = mock(CalendarioLaboral.class);
    private final VistaFilas vista = new VistaFilas(dao, nsys, taller, Clock.fixed(AHORA.atZone(MADRID).toInstant(), MADRID));

    private static FilaPedido fila(int id, String imei, LocalDateTime cerradaEn) {
        return new FilaPedido(id, LocalDate.of(2026, 11, 5), "AMZ", "i13128AzulA", imei, "402-0000000-000000" + id,
                LocalDate.of(2026, 11, 4), "SHIPPYPRO", Canal.AMAZON, 1, cerradaEn);
    }

    /** Una fila enviada con la etiqueta ya impresa. */
    private static FilaPedido enviadaImpresa(int id, String imei) {
        FilaPedido f = enviada(id, imei);
        return new FilaPedido(f.id(), f.fecha(), f.plataforma(), f.sku(), f.imei(), f.pedido(), f.fechaCompra(), f.origen(), f.canal(),
                f.posicion(), f.cerradaEn(), f.envioFecha(), f.transportista(), f.seguimiento(), null, 9L, AHORA.minusDays(1));
    }

    /** Una fila que ShippyPro dio por enviada (cerrada con envio). */
    private static FilaPedido enviada(int id, String imei) {
        return new FilaPedido(id, LocalDate.of(2026, 11, 5), "AMZ", "i13128AzulA", imei, "402-0000000-000000" + id,
                LocalDate.of(2026, 11, 4), "SHIPPYPRO", Canal.AMAZON, 1, AHORA.minusDays(1), AHORA.minusDays(2), "UPSv2",
                "1Z0000000000000001");
    }

    private static MovilNsys vendido(String imei) {
        return new MovilNsys(imei, "BT-9001", "Apple iPhone 13 128GB Blue", "A", "Released", "CAJA LISTOS", "Proveedor A",
                LocalDate.of(2026, 9, 1), null, "i13128AzulA", "13", "13", "128GB", "Azul", "A", false, "Listos", "Listos",
                EstadoMovil.VENDIDO, null, null);
    }

    private static MovilNsys movil(String imei) {
        return new MovilNsys(imei, "BT-9001", "Apple iPhone 13 128GB Blue", "A", "Available", "CAJA LISTOS", "Proveedor A",
                LocalDate.of(2026, 9, 1), null, "i13128AzulA", "13", "13", "128GB", "Azul", "A", false, "Listos", "Listos",
                EstadoMovil.STOCK, null, null);
    }

    private void nsysConUnMovilQueCuadra(MovilNsys... moviles) {
        when(nsys.porImeis(any())).thenReturn(List.of(moviles));
        when(nsys.trabajosAbiertos(anyCollection())).thenReturn(Map.of());
        when(taller.valedor(any())).thenReturn(x -> new LoQueVale.Valor("Azul", "A", "i13128AzulA", null, null, null, false));
        when(nsys.productosPorSkuDeCanal(any())).thenReturn(List.of(
                AceptaSkuTest.producto("Azul", "A", "i13128AzulA", "i13128AzulA", null, null)));
    }

    @Test void losAvisosSoloEnLasPendientesYNoPendientesConImei() {
        FilaPedido noPendiente = fila(3, IMEI2, AHORA.minusDays(2));
        when(dao.delPeriodo(any(), any())).thenReturn(List.of(
                fila(1, IMEI1, null), fila(2, null, null), noPendiente, enviadaImpresa(5, IMEI1),
                new FilaPedido(4, LocalDate.of(2025, 1, 1), "Web", "texto", "sin imei", null, null, "HISTORICO", null, null,
                        AHORA.minusYears(1))));
        when(dao.shippyProConImei(any(), any())).thenReturn(List.of(fila(1, IMEI1, null), noPendiente));
        nsysConUnMovilQueCuadra(movil(IMEI1), movil(IMEI2));

        List<FilaVista> filas = vista.filas(null, null, null).filas();

        assertEquals(5, filas.size());
        assertEquals("OK", filas.get(0).nivel());
        assertEquals("Listos", filas.get(0).caja());
        assertEquals("i13128AzulA", filas.get(0).skuMovil());
        assertNull(filas.get(1).nivel(), "sin IMEI no hay aviso");
        // una fila que ya no esta pendiente (sin envio) sigue llevando aviso
        assertEquals("OK", filas.get(2).nivel());
        // 0.9.46.2: la enviada impresa tambien lleva aviso; aqui su IMEI esta tambien en la pendiente 1: «en otro pedido».
        assertEquals("MAL", filas.get(3).nivel());
        assertEquals("EN_OTRA_FILA", filas.get(3).motivo());
        for (int i : new int[]{4}) {
            FilaVista f = filas.get(i);
            assertNull(f.nivel(), "la fila " + f.idFila() + " no lleva aviso");
            assertNull(f.motivo());
            assertNull(f.skuMovil());
            assertNull(f.caja());
        }
        verify(nsys).porImeis(Set.of(IMEI1, IMEI2));
    }

    private static FilaPedido conValidacion(FilaPedido f, String validadaImei) {
        return new FilaPedido(f.id(), f.fecha(), f.plataforma(), f.sku(), f.imei(), f.pedido(), f.fechaCompra(), f.origen(), f.canal(),
                f.posicion(), f.cerradaEn(), null, null, null, null, null, null, null, null, null, null, null, null, null, null,
                validadaImei, AHORA.minusHours(2), "super");
    }

    /** El color del movil no es el del SKU: COLOR. */
    private void nsysConUnMovilDeOtroColor(MovilNsys m) {
        when(nsys.porImeis(any())).thenReturn(List.of(m));
        when(nsys.trabajosAbiertos(anyCollection())).thenReturn(Map.of());
        when(taller.valedor(any())).thenReturn(x -> new LoQueVale.Valor("Rojo", "A", "i13128RojoA", null, null, null, false));
        when(nsys.productosPorSkuDeCanal(any())).thenReturn(List.of(
                AceptaSkuTest.producto("Azul", "A", "i13128AzulA", "i13128AzulA", null, null)));
    }

    @Test void unaValidacionDeEsteImeiPasaElAvisoDeSkuAOkYGuardaElMotivo() {
        when(dao.delPeriodo(any(), any())).thenReturn(List.of(fila(1, IMEI1, null), conValidacion(fila(2, IMEI2, null), IMEI2)));
        when(dao.shippyProConImei(any(), any())).thenReturn(List.of());
        nsysConUnMovilDeOtroColor(movil(IMEI1));
        when(nsys.porImeis(any())).thenReturn(List.of(movil(IMEI1), movil(IMEI2)));

        List<FilaVista> f = vista.filas(null, null, null).filas();

        assertEquals("MAL", f.get(0).nivel());
        assertEquals("COLOR", f.get(0).motivo());
        assertNull(f.get(0).motivoValidado());
        assertNull(f.get(0).validadaEn());
        assertEquals("OK", f.get(1).nivel());
        assertNull(f.get(1).motivo());
        assertEquals("COLOR", f.get(1).motivoValidado());
        assertEquals(AHORA.minusHours(2), f.get(1).validadaEn());
        assertEquals("super", f.get(1).validadaPor());
        assertEquals("i13128RojoA", f.get(1).skuMovil());
        assertEquals("Listos", f.get(1).caja());
    }

    @Test void laFilaVistaTraeElDetalleYUnaValidadaConservaLaSim() {
        MovilNsys esimRojo = new MovilNsys(IMEI2, "BT-9001", "Apple iPhone 13 128GB Blue", "A", "Available", "CAJA LISTOS", "Proveedor A",
                LocalDate.of(2026, 9, 1), null, "i13128AzulA", "13", "13", "128GB", "Azul", "A", true, "Listos", "Listos",
                EstadoMovil.STOCK, null, null);
        when(dao.delPeriodo(any(), any())).thenReturn(List.of(fila(1, IMEI1, null), conValidacion(fila(2, IMEI2, null), IMEI2)));
        when(dao.shippyProConImei(any(), any())).thenReturn(List.of());
        nsysConUnMovilDeOtroColor(movil(IMEI1));
        when(nsys.porImeis(any())).thenReturn(List.of(movil(IMEI1), esimRojo));

        List<FilaVista> f = vista.filas(null, null, null).filas();

        assertEquals("COLOR", f.get(0).motivo());
        assertEquals("Rojo", f.get(0).detalle().dato());
        assertEquals("OK", f.get(1).nivel());
        assertEquals("COLOR", f.get(1).motivoValidado());
        assertEquals("ESIM", f.get(1).detalle().sim(), "la SIM se sigue viendo aunque el SKU esté validado");
        assertNull(f.get(1).detalle().dato());
    }

    @Test void unaValidacionDeOtroImeiSeIgnora() {
        when(dao.delPeriodo(any(), any())).thenReturn(List.of(conValidacion(fila(2, IMEI2, null), IMEI1)));
        when(dao.shippyProConImei(any(), any())).thenReturn(List.of());
        nsysConUnMovilDeOtroColor(movil(IMEI2));

        FilaVista f = vista.filas(null, null, null).filas().get(0);

        assertEquals("MAL", f.nivel());
        assertEquals("COLOR", f.motivo());
        assertNull(f.motivoValidado());
        assertNull(f.validadaEn());
        assertNull(f.validadaPor());
    }

    @Test void unaValidacionNoValeSiElAvisoYaNoEsDeSku() {
        // El IMEI esta ahora en otra fila: EN_OTRA_FILA no se valida.
        FilaPedido validada = conValidacion(fila(2, IMEI2, null), IMEI2);
        when(dao.delPeriodo(any(), any())).thenReturn(List.of(validada));
        when(dao.shippyProConImei(any(), any())).thenReturn(List.of(validada, fila(9, IMEI2, null)));
        nsysConUnMovilDeOtroColor(movil(IMEI2));

        FilaVista f = vista.filas(null, null, null).filas().get(0);

        assertEquals("MAL", f.nivel());
        assertEquals("EN_OTRA_FILA", f.motivo());
        assertNull(f.motivoValidado());
        assertNull(f.validadaEn());
    }

    @Test void laFilaVistaTraeLaAsociacionYElPedidoAunqueNoEsteAsociada() {
        FilaPedido base = fila(1, null, null);
        FilaPedido asociada = new FilaPedido(2, base.fecha(), "AMZ", base.sku(), null, "402-0000000-0000002", base.fechaCompra(), "SHIPPYPRO",
                Canal.AMAZON, 1, null, null, null, null, null, null, null, null, null, null, null, null, null, AHORA.minusHours(1), "LIBRO");
        when(dao.delPeriodo(any(), any())).thenReturn(List.of(base, asociada));
        List<FilaVista> f = vista.filas(null, null, null).filas();
        assertNull(f.get(0).asociadaEn());
        assertNull(f.get(0).asociadaOrigen());
        assertEquals("402-0000000-0000001", f.get(0).pedido(), "el pedido sigue viniendo: la web decide que enseña");
        assertEquals(AHORA.minusHours(1), f.get(1).asociadaEn());
        assertEquals("LIBRO", f.get(1).asociadaOrigen());
    }

    @Test void elEstadoLaEdicionYLosDatosDelEnvioDeCadaFila() {
        when(dao.delPeriodo(any(), any())).thenReturn(List.of(
                fila(1, null, null), fila(2, null, AHORA.minusDays(2)), enviada(3, null),
                new FilaPedido(4, LocalDate.of(2025, 1, 1), "Web", "texto", null, null, null, "HISTORICO", null, null,
                        AHORA.minusYears(1))));

        List<FilaVista> f = vista.filas(null, null, null).filas();

        assertEquals(List.of("PENDIENTE", "NO_PENDIENTE", "ENVIADA", "HISTORICO"), f.stream().map(FilaVista::estado).toList());
        assertEquals(List.of(true, true, true, false), f.stream().map(FilaVista::editable).toList());
        assertEquals(AHORA.minusDays(2), f.get(2).envioFecha());
        assertEquals("UPSv2", f.get(2).transportista());
        assertEquals("1Z0000000000000001", f.get(2).seguimiento());
        for (int i : new int[]{0, 1, 3}) {
            assertNull(f.get(i).envioFecha());
            assertNull(f.get(i).transportista());
            assertNull(f.get(i).seguimiento());
        }
    }

    @Test void laImpresionYSiTieneEtiquetaSalenEnLaVista() {
        LocalDateTime impresa = AHORA.minusDays(1);
        FilaPedido conEtiqueta = new FilaPedido(3, LocalDate.of(2026, 11, 5), "AMZ", "i13128AzulA", null, "402-0000000-0000003",
                LocalDate.of(2026, 11, 4), "SHIPPYPRO", Canal.AMAZON, 1, AHORA.minusDays(2), AHORA.minusDays(2), "UPSv2", "x", null,
                555L, impresa);
        FilaPedido sinImprimir = new FilaPedido(4, LocalDate.of(2026, 11, 5), "AMZ", "i13128AzulA", null, "402-0000000-0000004",
                LocalDate.of(2026, 11, 4), "SHIPPYPRO", Canal.AMAZON, 1, AHORA.minusDays(2), AHORA.minusDays(2), "UPSv2", "x", null,
                556L, null);
        when(dao.delPeriodo(any(), any())).thenReturn(List.of(conEtiqueta, sinImprimir, fila(1, null, null)));

        List<FilaVista> f = vista.filas(null, null, null).filas();

        assertEquals(impresa, f.get(0).impresaEn());
        assertTrue(f.get(0).tieneEtiqueta());
        assertNull(f.get(1).impresaEn());
        assertTrue(f.get(1).tieneEtiqueta());
        assertNull(f.get(2).impresaEn());
        assertFalse(f.get(2).tieneEtiqueta());
        assertEquals("ENVIADA", f.get(0).estado(), "el estado no cambia");
    }

    @Test void unaFilaEnviadaSinImprimirConImeiLlevaAvisoYUnaImpresaTambien() {
        FilaPedido sinImprimir = enviada(3, IMEI1);
        FilaPedido impresa = new FilaPedido(5, LocalDate.of(2026, 11, 5), "AMZ", "i13128AzulA", IMEI2, "402-0000000-0000005",
                LocalDate.of(2026, 11, 4), "SHIPPYPRO", Canal.AMAZON, 1, AHORA.minusDays(1), AHORA.minusDays(2), "UPSv2", "x", null,
                9L, AHORA.minusDays(1));
        when(dao.delPeriodo(any(), any())).thenReturn(List.of(sinImprimir, impresa));
        when(dao.shippyProConImei(any(), any())).thenReturn(List.of(sinImprimir));
        nsysConUnMovilQueCuadra(movil(IMEI1), movil(IMEI2));

        List<FilaVista> f = vista.filas(null, null, null).filas();

        assertEquals("ENVIADA", f.get(0).estado());
        assertNotNull(f.get(0).nivel());
        assertEquals("ENVIADA", f.get(1).estado());
        assertEquals("OK", f.get(1).nivel(), "0.9.46.2: la impresa tambien lleva aviso (solo informa)");
        assertNull(f.get(1).motivo());
        assertEquals("i13128AzulA", f.get(1).skuMovil());
        // una sola carga para todas las filas, sin consultas por fila
        verify(nsys, atMost(2)).porImeis(any()); // el aviso y los ya puestos (enviadas), ambos por lotes
        verify(dao, times(1)).shippyProConImei(any(), any());
    }

    /** 0.9.46.2: una enviada impresa tambien lleva aviso (solo informa de lo que salio; la web no la cuenta ni deja validarla). */
    @Test void unaFilaEnviadaImpresaConImeiTambienLlevaAviso() {
        when(dao.delPeriodo(any(), any())).thenReturn(List.of(new FilaPedido(3, LocalDate.of(2026, 11, 5), "AMZ", "i13128AzulA",
                IMEI1, "402-0000000-0000003", LocalDate.of(2026, 11, 4), "SHIPPYPRO", Canal.AMAZON, 1, AHORA.minusDays(1),
                AHORA.minusDays(2), "UPSv2", "x", null, 9L, AHORA.minusDays(1))));
        nsysConUnMovilQueCuadra(movil(IMEI1));
        FilaVista f = vista.filas(null, null, null).filas().get(0);
        assertEquals("ENVIADA", f.estado());
        assertEquals(IMEI1, f.imei());
        assertEquals("OK", f.nivel());
    }

    @Test void unImeiPuestoEnUnaFilaEnviadaCuyoMovilNoEstaVendidoAvisaEnOtraFila() {
        FilaPedido propia = fila(1, IMEI1, null);
        when(dao.delPeriodo(any(), any())).thenReturn(List.of(propia));
        when(dao.shippyProConImei(any(), any())).thenReturn(List.of(propia, enviada(9, IMEI1)));
        nsysConUnMovilQueCuadra(movil(IMEI1));

        FilaVista f = vista.filas(null, null, null).filas().get(0);

        assertEquals("EN_OTRA_FILA", f.motivo());
        verify(dao).shippyProConImei(Set.of(IMEI1), LocalDate.of(2026, 9, 11));
    }

    @Test void unImeiPuestoEnUnaFilaEnviadaCuyoMovilNsysYaEstaVendidoNoCuenta() {
        FilaPedido propia = fila(1, IMEI1, null);
        when(dao.delPeriodo(any(), any())).thenReturn(List.of(propia));
        when(dao.shippyProConImei(any(), any())).thenReturn(List.of(propia, enviada(9, IMEI1)));
        nsysConUnMovilQueCuadra(movil(IMEI1));
        // porImeis responde para la fila con aviso y para la comprobacion de las enviadas (ya vendido)
        when(nsys.porImeis(any())).thenReturn(List.of(vendido(IMEI1)));

        FilaVista f = vista.filas(null, null, null).filas().get(0);

        assertNotEquals("EN_OTRA_FILA", f.motivo());
    }

    @Test void unaFilaNoPendienteConElMismoImeiYaCuentaComoOtraFila() {
        FilaPedido propia = fila(1, IMEI1, null);
        when(dao.delPeriodo(any(), any())).thenReturn(List.of(propia));
        when(dao.shippyProConImei(any(), any())).thenReturn(List.of(propia, fila(8, IMEI1, AHORA.minusDays(3))));
        nsysConUnMovilQueCuadra(movil(IMEI1));

        assertEquals("EN_OTRA_FILA", vista.filas(null, null, null).filas().get(0).motivo());
    }

    @Test void laPropiaFilaFueraDeLosSesentaDiasNoLaHaceDuplicada() {
        // Una fila no pendiente muy antigua no sale de la consulta de los ultimos 60 dias: sola, no es «otra fila».
        FilaPedido antigua = fila(1, IMEI1, AHORA.minusDays(100));
        when(dao.delPeriodo(any(), any())).thenReturn(List.of(antigua));
        when(dao.shippyProConImei(any(), any())).thenReturn(List.of());
        nsysConUnMovilQueCuadra(movil(IMEI1));

        assertNotEquals("EN_OTRA_FILA", vista.filas(null, null, null).filas().get(0).motivo());
    }

    @Test void sinFilasConImeiNoSeConsultaNsys() {
        when(dao.delPeriodo(any(), any())).thenReturn(List.of(fila(1, null, null)));
        assertEquals(1, vista.filas(null, null, null).filas().size());
        verifyNoInteractions(nsys, taller);
    }

    @Test void enOtraFilaVivaCuentaTodasLasVivasConEseImeiAunqueNoEstenEnLaPagina() {
        when(dao.buscar(eq("0001"), anyInt())).thenReturn(List.of(fila(1, IMEI1, null)));
        when(dao.shippyProConImei(any(), any())).thenReturn(List.of(fila(1, IMEI1, null), fila(9, IMEI1, null)));
        nsysConUnMovilQueCuadra(movil(IMEI1));

        FilaVista f = vista.filas(null, null, "0001").filas().get(0);

        assertEquals("MAL", f.nivel());
        assertEquals("EN_OTRA_FILA", f.motivo());
    }

    @Test void dosFilasVivasConElMismoImeiSeAvisanLasDos() {
        when(dao.delPeriodo(any(), any())).thenReturn(List.of(fila(1, IMEI1, null), fila(2, IMEI1, null)));
        when(dao.shippyProConImei(any(), any())).thenReturn(List.of(fila(1, IMEI1, null), fila(2, IMEI1, null)));
        nsysConUnMovilQueCuadra(movil(IMEI1));

        List<FilaVista> filas = vista.filas(null, null, null).filas();

        assertEquals("EN_OTRA_FILA", filas.get(0).motivo());
        assertEquals("EN_OTRA_FILA", filas.get(1).motivo());
    }

    @Test void unImeiQueNoEstaEnNsysEsMal() {
        when(dao.delPeriodo(any(), any())).thenReturn(List.of(fila(1, IMEI2, null)));
        when(dao.shippyProConImei(any(), any())).thenReturn(List.of(fila(1, IMEI2, null)));
        nsysConUnMovilQueCuadra(movil(IMEI1));

        FilaVista f = vista.filas(null, null, null).filas().get(0);

        assertEquals("MAL", f.nivel());
        assertEquals("NO_EN_NSYS", f.motivo());
        assertNull(f.caja());
    }

    @Test void elPeriodoPorDefectoEsElUltimoMesHastaHoy() {
        when(dao.delPeriodo(any(), any())).thenReturn(List.of());

        ListaFilas lista = vista.filas(null, null, "  ");

        verify(dao).delPeriodo(LocalDate.of(2026, 10, 10), LocalDate.of(2026, 11, 10));
        verify(dao, never()).buscar(any(), anyInt());
        assertFalse(lista.hayMas());
    }

    @Test void elPeriodoDadoSeRespeta() {
        when(dao.delPeriodo(any(), any())).thenReturn(List.of());
        vista.filas(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31), null);
        verify(dao).delPeriodo(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31));
    }

    @Test void elBuscadorSinPasarElTopeNoDiceQueHayMas() {
        when(dao.buscar("amz", 500)).thenReturn(List.of(fila(1, null, null), fila(2, null, null)));
        ListaFilas lista = vista.filas(null, null, " amz ");
        assertEquals(2, lista.filas().size());
        assertFalse(lista.hayMas());
    }

    @Test void conMasDelTopeSeQuedaLaMasAntiguaFueraYDiceQueHayMas() {
        List<FilaPedido> muchas = new ArrayList<>();
        for (int i = 1; i <= 501; i++) muchas.add(fila(i, null, null)); // en orden de hoja: la 1 es la mas antigua
        when(dao.buscar("amz", 500)).thenReturn(muchas);

        ListaFilas lista = vista.filas(null, null, "amz");

        assertTrue(lista.hayMas());
        assertEquals(500, lista.filas().size());
        assertEquals(2, lista.filas().get(0).idFila());
        assertEquals(501, lista.filas().get(499).idFila());
    }

    // ---------------------------------------------------------------- recomendados

    @Test void recomendadosDeUnaFilaQueNoExisteEsVacio() {
        when(dao.porId(7)).thenReturn(Optional.empty());
        assertTrue(vista.recomendados(7).isEmpty());
    }

    @Test void recomendadosVaciosSiLaFilaEsDelHistorico() {
        when(dao.porId(7)).thenReturn(Optional.of(new FilaPedido(7, LocalDate.of(2025, 1, 1), "Web", "i13128AzulA", null, null,
                null, "HISTORICO", Canal.AMAZON, null, AHORA.minusYears(1))));
        assertEquals(List.of(), vista.recomendados(7).orElseThrow());
        verifyNoInteractions(nsys);
    }

    @Test void unaFilaEnviadaOQueYaNoEstaPendienteSiDaCandidatos() {
        when(nsys.productosPorSkuDeCanal(any())).thenReturn(List.of(
                AceptaSkuTest.producto("Azul", "A", "i13128AzulA", "i13128AzulA", null, null)));
        when(nsys.enEmpresa()).thenReturn(List.of(movil(IMEI1)));
        when(nsys.trabajosAbiertos()).thenReturn(Map.of());
        when(taller.valedorDeTodos()).thenReturn(x -> new LoQueVale.Valor("Azul", "A", "i13128AzulA", null, null, null, false));
        when(dao.shippyProConImeiDesde(any())).thenReturn(List.of());
        for (FilaPedido f : List.of(enviada(7, null), fila(8, null, AHORA.minusDays(1)))) {
            when(dao.porId(f.id())).thenReturn(Optional.of(f));
            assertEquals(List.of(IMEI1), vista.recomendados(f.id()).orElseThrow().stream().map(Recomendador.Candidato::imei).toList());
        }
    }

    @Test void recomendadosVaciosSiElMaestroNoConoceElSku() {
        when(dao.porId(7)).thenReturn(Optional.of(fila(7, null, null)));
        when(nsys.productosPorSkuDeCanal(any())).thenReturn(List.of());
        assertEquals(List.of(), vista.recomendados(7).orElseThrow());
        verify(nsys, never()).enEmpresa();
    }

    @Test void recomendadosNoOfrecenLosYaPuestosEnOtraFilaPeroSiElDeLaPropiaFila() {
        FilaPedido propia = fila(7, IMEI1, null);
        when(dao.porId(7)).thenReturn(Optional.of(propia));
        when(dao.shippyProConImeiDesde(LocalDate.of(2026, 9, 11))).thenReturn(List.of(propia, fila(8, IMEI2, null)));
        when(nsys.productosPorSkuDeCanal(any())).thenReturn(List.of(
                AceptaSkuTest.producto("Azul", "A", "i13128AzulA", "i13128AzulA", null, null)));
        when(nsys.enEmpresa()).thenReturn(List.of(movil(IMEI1), movil(IMEI2), movil("350000000000003")));
        when(nsys.trabajosAbiertos()).thenReturn(Map.of());
        when(taller.valedorDeTodos()).thenReturn(x -> new LoQueVale.Valor("Azul", "A", "i13128AzulA", null, null, null, false));

        List<Recomendador.Candidato> r = vista.recomendados(7).orElseThrow();

        assertEquals(List.of(IMEI1, "350000000000003"), r.stream().map(Recomendador.Candidato::imei).toList());
    }

    @Test void recomendadosNoOfrecenLosReservadosParaOtraFilaYPonenPrimeroElDeLaPropia() {
        FilaPedido f = fila(7, null, null);
        FilaPedido propia = new FilaPedido(f.id(), f.fecha(), f.plataforma(), f.sku(), f.imei(), f.pedido(), f.fechaCompra(), f.origen(),
                f.canal(), f.posicion(), f.cerradaEn(), f.envioFecha(), f.transportista(), f.seguimiento(), f.reabiertaEn(), f.envioId(),
                f.impresaEn(), f.faltaEnviarEn(), f.sincronizadaEn(), f.marca(), f.marcaNota(), f.marcaEn(), f.marcaPor(), f.asociadaEn(),
                f.asociadaOrigen(), f.validadaImei(), f.validadaEn(), f.validadaPor(), f.fechaSalida(), f.pais(), IMEI2, AHORA, null);
        when(dao.porId(7)).thenReturn(Optional.of(propia));
        when(dao.shippyProConImeiDesde(any())).thenReturn(List.of());
        when(dao.reservasVivas()).thenReturn(Map.of(7, IMEI2, 8, IMEI1));
        when(nsys.productosPorSkuDeCanal(any())).thenReturn(List.of(
                AceptaSkuTest.producto("Azul", "A", "i13128AzulA", "i13128AzulA", null, null)));
        when(nsys.enEmpresa()).thenReturn(List.of(movil(IMEI1), movil("350000000000003"), movil(IMEI2)));
        when(nsys.trabajosAbiertos()).thenReturn(Map.of());
        when(taller.valedorDeTodos()).thenReturn(x -> new LoQueVale.Valor("Azul", "A", "i13128AzulA", null, null, null, false));

        List<Recomendador.Candidato> r = vista.recomendados(7).orElseThrow();

        // IMEI1 está reservado para la fila 8: no sale (Imad 25/11). IMEI2 es la reserva de esta fila: el primero, marcado.
        assertEquals(List.of(IMEI2, "350000000000003"), r.stream().map(Recomendador.Candidato::imei).toList());
        assertTrue(r.get(0).reservado());
    }

    @Test void recomendadosCuentanComoPuestosLasEnviadasConMovilNoVendidoYNoLasVendidas() {
        FilaPedido propia = fila(7, null, null);
        when(dao.porId(7)).thenReturn(Optional.of(propia));
        when(dao.shippyProConImeiDesde(any())).thenReturn(List.of(enviada(8, IMEI1), enviada(9, IMEI2),
                fila(10, "350000000000004", AHORA.minusDays(1))));
        when(nsys.porImeis(any())).thenReturn(List.of(movil(IMEI1), vendido(IMEI2)));
        when(nsys.productosPorSkuDeCanal(any())).thenReturn(List.of(
                AceptaSkuTest.producto("Azul", "A", "i13128AzulA", "i13128AzulA", null, null)));
        when(nsys.enEmpresa()).thenReturn(List.of(movil(IMEI1), movil(IMEI2), movil("350000000000003"), movil("350000000000004")));
        when(nsys.trabajosAbiertos()).thenReturn(Map.of());
        when(taller.valedorDeTodos()).thenReturn(x -> new LoQueVale.Valor("Azul", "A", "i13128AzulA", null, null, null, false));

        List<Recomendador.Candidato> r = vista.recomendados(7).orElseThrow();

        assertEquals(List.of(IMEI2, "350000000000003"), r.stream().map(Recomendador.Candidato::imei).toList());
    }

    // ---------------------------------------------------------------- fuentes

    @Test void lasFuentesShippyProYNsysEnEseOrden() {
        when(dao.ultimaLectura(eq(Set.of(LecturaFilas.OK)))).thenReturn(Optional.of(
                new LecturaGuardada(1, AHORA.minusMinutes(3), AHORA.minusMinutes(3), "OK", 12, "i99RaroA", null)));
        when(dao.ultimaLectura(argThat(s -> s.size() > 1))).thenReturn(Optional.of(
                new LecturaGuardada(2, AHORA.minusMinutes(1), AHORA.minusMinutes(1), "ERROR", 0, null,
                        "java.io.IOException: con un id interno")));
        when(nsys.ultimaImportacion(eq(ImportadorNsys.ACTUAL), eq(Set.of(ImportadorNsys.OK)))).thenReturn(Optional.of(
                new Importacion(5, "Items.xlsx", AHORA.minusMinutes(20), "OK", 300, null)));
        when(nsys.ultimaImportacion(eq(ImportadorNsys.ACTUAL), argThat(s -> s.contains(ImportadorNsys.ERROR))))
                .thenReturn(Optional.empty());

        List<FuenteVista> f = vista.fuentes();

        assertEquals(List.of("SHIPPYPRO", "NSYS"), f.stream().map(FuenteVista::fuente).toList());
        FuenteVista s = f.get(0);
        assertEquals(AHORA.minusMinutes(3), s.ultimaBuena());
        assertEquals("ERROR", s.resultado());
        assertEquals(VistaFilas.MOTIVO_ERROR, s.motivo(), "un ERROR no saca su texto");
        assertEquals(12, s.filas());
        assertEquals("i99RaroA", s.skuDesconocidos());
        assertEquals("OK", s.nivel());
        FuenteVista n = f.get(1);
        assertEquals(AHORA.minusMinutes(20), n.ultimaBuena());
        assertEquals("OK", n.resultado());
        assertEquals(300, n.filas());
        assertNull(n.motivo());
        assertEquals("OK", n.nivel());
    }

    @Test void unaLecturaRechazadaEnseniaSuMensajeYSinLecturasHaySinDatos() {
        when(dao.ultimaLectura(any())).thenReturn(Optional.empty());
        when(nsys.ultimaImportacion(any(), any())).thenReturn(Optional.empty());

        List<FuenteVista> vacias = vista.fuentes();

        assertEquals("SIN_DATOS", vacias.get(0).nivel());
        assertNull(vacias.get(0).resultado());
        assertEquals(0, vacias.get(0).filas());
        assertEquals("SIN_DATOS", vacias.get(1).nivel());

        when(dao.ultimaLectura(argThat(s -> s != null && s.contains(LecturaFilas.RECHAZADA)))).thenReturn(Optional.of(
                new LecturaGuardada(3, AHORA, AHORA, "RECHAZADA", 0, null, "Falta el ajuste pedidos.shippypro.canales")));
        when(nsys.ultimaImportacion(any(), argThat(s -> s != null && s.contains(ImportadorNsys.ERROR)))).thenReturn(Optional.of(
                new Importacion(6, "Items.xlsx", AHORA, "ERROR", 0, "java.lang.IllegalStateException: id de fichero")));

        List<FuenteVista> f = vista.fuentes();

        assertEquals("RECHAZADA", f.get(0).resultado());
        assertEquals(VistaFilas.MOTIVO_RECHAZADA, f.get(0).motivo(), "el mensaje guardado no sale: puede nombrar un ajuste");
        assertEquals("ERROR", f.get(1).resultado());
        assertEquals(VistaFilas.MOTIVO_NSYS_ERROR, f.get(1).motivo());

        when(nsys.ultimaImportacion(any(), argThat(s -> s != null && s.contains(ImportadorNsys.ERROR)))).thenReturn(Optional.of(
                new Importacion(7, "Items.xlsx", AHORA, "RECHAZADO", 0, "Faltan columnas: IMEI")));
        assertEquals(VistaFilas.MOTIVO_NSYS_RECHAZADO, vista.fuentes().get(1).motivo());
    }

    // ---------------------------------------------------------------- canal

    private static FilaPedido historico(int id, String plataforma) {
        return new FilaPedido(id, LocalDate.of(2025, 1, 1), plataforma, "texto", null, null, null, "HISTORICO", null, null,
                AHORA.minusYears(1));
    }

    @Test void elCanalDeUnaFilaDeShippyProEsElSuyo() {
        when(dao.delPeriodo(any(), any())).thenReturn(List.of(fila(1, null, null)));
        assertEquals("AMAZON", vista.filas(null, null, null).filas().get(0).canal());
    }

    @Test void elCanalDeUnaFilaDeShippyProSinCanalEsNulo() {
        when(dao.delPeriodo(any(), any())).thenReturn(List.of(new FilaPedido(1, LocalDate.of(2026, 11, 5), "Web", "x", null, null,
                null, "SHIPPYPRO", null, 1, null)));
        assertNull(vista.filas(null, null, null).filas().get(0).canal());
    }

    @Test void elCanalDelHistoricoSeDeduceDeLaPlataformaSoloParaLasDosCuentasDeAmazon() {
        when(dao.delPeriodo(any(), any())).thenReturn(List.of(historico(1, "AMZ"), historico(2, "  amz "), historico(3, "FNSK"),
                historico(4, "fsnk"), historico(5, "Cliente Web A"), historico(6, "INCIDENCIA 12"), historico(7, "NP"),
                historico(8, ""), historico(9, null)));
        List<String> canales = new ArrayList<>();
        for (FilaVista f : vista.filas(null, null, null).filas()) canales.add(f.canal());
        assertEquals(java.util.Arrays.asList("AMAZON", "AMAZON", "AMAZON_STOCKROOM", "AMAZON_STOCKROOM", null, null, null, null, null),
                canales);
    }

    private void ultimaLectura(String resultado, String mensaje) {
        LecturaGuardada l = new LecturaGuardada(7, AHORA, AHORA, resultado, 3, null, mensaje);
        when(dao.ultimaLectura(eq(Set.of(LecturaFilas.OK)))).thenReturn(Optional.of(l));
        when(dao.ultimaLectura(argThat(s -> s != null && s.size() > 1))).thenReturn(Optional.of(l));
        when(nsys.ultimaImportacion(any(), any())).thenReturn(Optional.empty());
    }

    @Test void unaLecturaBuenaQueSaltoElCierreEnseniaUnAvisoFijoSinNumerosDePedido() {
        ultimaLectura("OK", LecturaFilas.AVISO_DESCARTADOS + " (no se cierra nada): 402-0000000-0000099");

        FuenteVista s = vista.fuentes().get(0);

        assertEquals("OK", s.resultado());
        assertNull(s.motivo());
        assertEquals(VistaFilas.AVISO_DESCARTADOS, s.aviso());
        assertFalse(s.aviso().contains("402-"));
    }

    @Test void unaLecturaBuenaSinEnviosEnseniaSuAvisoYUnaNormalONoLlevaNinguno() {
        ultimaLectura("OK", "2 filas reabiertas; " + LecturaFilas.AVISO_SIN_ENVIOS);
        assertEquals(VistaFilas.AVISO_SIN_ENVIOS, vista.fuentes().get(0).aviso());

        ultimaLectura("OK", "2 filas reabiertas");
        assertNull(vista.fuentes().get(0).aviso());
        ultimaLectura("OK", null);
        assertNull(vista.fuentes().get(0).aviso());
        assertNull(vista.fuentes().get(1).aviso(), "NSYS no lleva aviso");
    }

    @Test void conErrorHablaElMotivoYNoElAvisoDeUnaLecturaAnterior() {
        ultimaLectura("ERROR", LecturaFilas.AVISO_DESCARTADOS + " (no se cierra nada): 402-0000000-0000099");

        FuenteVista s = vista.fuentes().get(0);

        assertEquals(VistaFilas.MOTIVO_ERROR, s.motivo());
        assertNull(s.aviso());
    }

    @Test void laFechaDeSalidaYElPaisDeLaFilaPasanALaVista() {
        FilaPedido b = fila(1, null, null);
        FilaPedido conSalida = new FilaPedido(1, b.fecha(), b.plataforma(), b.sku(), null, b.pedido(), b.fechaCompra(), "SHIPPYPRO",
                Canal.AMAZON, 1, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null,
                null, LocalDate.of(2026, 11, 19), "DE");
        when(dao.delPeriodo(any(), any())).thenReturn(List.of(conSalida, fila(2, null, null)));
        List<FilaVista> f = vista.filas(null, null, null).filas();
        assertEquals(LocalDate.of(2026, 11, 19), f.get(0).fechaSalida());
        assertEquals("DE", f.get(0).pais());
        assertNull(f.get(1).fechaSalida());
        assertNull(f.get(1).pais());
    }

    /** {@code entrada} es la fecha del pedido en ShippyPro (FECHA_COMPRA, la que usa el limite); la fila la vio el ERP un dia despues
     *  (FECHA), para comprobar que «desde» sale de la fecha del pedido y no de FECHA. */
    private static FilaPedido conSalida(int id, LocalDate entrada, Canal canal, String origen, LocalDate salida) {
        FilaPedido b = fila(id, null, null);
        return new FilaPedido(id, entrada.plusDays(1), b.plataforma(), b.sku(), null, "0099-" + id, entrada, origen,
                canal, 1, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null,
                null, salida, "ES");
    }

    @Test void enLasWebsLaVistaLlevaElPrimerDiaEnQueSaleHoyYEnAmazonYElHistoricoNo() {
        when(calendario.festivos()).thenReturn(Set.of());
        VistaFilas conCalendario = new VistaFilas(dao, nsys, taller, Clock.fixed(AHORA.atZone(MADRID).toInstant(), MADRID), calendario);
        when(dao.delPeriodo(any(), any())).thenReturn(List.of(
                conSalida(1, LocalDate.of(2026, 11, 7), Canal.DISTRIBUIDORES, "SHIPPYPRO", LocalDate.of(2026, 11, 10)), // sabado
                conSalida(2, LocalDate.of(2026, 11, 5), Canal.PARTICULARES, "SHIPPYPRO", LocalDate.of(2026, 11, 6)),   // jueves
                conSalida(3, LocalDate.of(2026, 11, 7), Canal.AMAZON, "SHIPPYPRO", LocalDate.of(2026, 11, 10)),
                conSalida(4, LocalDate.of(2026, 11, 7), Canal.DISTRIBUIDORES, "SHIPPYPRO", null),
                conSalida(5, LocalDate.of(2026, 11, 7), null, "HISTORICO", null)));
        List<FilaVista> f = conCalendario.filas(null, null, null).filas();
        assertEquals(LocalDate.of(2026, 11, 9), f.get(0).salidaDesde());
        assertEquals(LocalDate.of(2026, 11, 5), f.get(1).salidaDesde());
        assertNull(f.get(2).salidaDesde());
        assertNull(f.get(3).salidaDesde(), "sin fecha de salida, sin desde");
        assertNull(f.get(4).salidaDesde());
    }

    @Test void sinCalendarioLaVistaNoFallaYCuentaSinFestivos() {
        when(dao.delPeriodo(any(), any())).thenReturn(List.of(
                conSalida(1, LocalDate.of(2026, 11, 7), Canal.DISTRIBUIDORES, "SHIPPYPRO", LocalDate.of(2026, 11, 10))));
        assertEquals(LocalDate.of(2026, 11, 9), vista.filas(null, null, null).filas().get(0).salidaDesde());
    }

    @Test void laVistaLlevaLaReservaDeImeiYNulosSinReserva() {
        FilaPedido base = conSalida(1, LocalDate.of(2026, 11, 7), Canal.DISTRIBUIDORES, "SHIPPYPRO", null);
        FilaPedido reservada = new FilaPedido(base.id(), base.fecha(), base.plataforma(), base.sku(), null, "0099-1", base.fechaCompra(),
                base.origen(), base.canal(), 1, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, "ES", "356000000000001", LocalDateTime.of(2026, 11, 24, 10, 0), null);
        when(dao.delPeriodo(any(), any())).thenReturn(List.of(reservada, conSalida(2, LocalDate.of(2026, 11, 7), Canal.DISTRIBUIDORES, "SHIPPYPRO", null)));
        List<FilaVista> f = vista.filas(null, null, null).filas();
        assertEquals("356000000000001", f.get(0).imeiReservado());
        assertEquals(LocalDateTime.of(2026, 11, 24, 10, 0), f.get(0).reservadaEn());
        assertNull(f.get(1).imeiReservado());
        assertNull(f.get(1).reservadaEn());
    }
}
