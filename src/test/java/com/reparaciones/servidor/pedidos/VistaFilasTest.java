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
    private final VistaFilas vista = new VistaFilas(dao, nsys, taller, Clock.fixed(AHORA.atZone(MADRID).toInstant(), MADRID));

    private static FilaPedido fila(int id, String imei, LocalDateTime enviadaEn) {
        return new FilaPedido(id, LocalDate.of(2026, 11, 5), "AMZ", "i13128AzulA", imei, "402-0000000-000000" + id,
                LocalDate.of(2026, 11, 4), "SHIPPYPRO", Canal.AMAZON, 1, enviadaEn);
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

    @Test void losAvisosSoloEnLasVivasConImei() {
        when(dao.delPeriodo(any(), any())).thenReturn(List.of(
                fila(1, IMEI1, null), fila(2, null, null), fila(3, IMEI1, AHORA.minusDays(2)),
                new FilaPedido(4, LocalDate.of(2025, 1, 1), "Web", "texto", "sin imei", null, null, "HISTORICO", null, null,
                        AHORA.minusYears(1))));
        when(dao.vivasConImei(any())).thenReturn(List.of(fila(1, IMEI1, null)));
        nsysConUnMovilQueCuadra(movil(IMEI1));

        List<FilaVista> filas = vista.filas(null, null, null).filas();

        assertEquals(4, filas.size());
        assertEquals("OK", filas.get(0).nivel());
        assertEquals("Listos", filas.get(0).caja());
        assertEquals("i13128AzulA", filas.get(0).skuMovil());
        assertFalse(filas.get(0).enviada());
        for (int i = 1; i < 4; i++) {
            FilaVista f = filas.get(i);
            assertNull(f.nivel(), "la fila " + f.idFila() + " no lleva aviso");
            assertNull(f.motivo());
            assertNull(f.skuMovil());
            assertNull(f.caja());
        }
        assertTrue(filas.get(2).enviada());
        assertTrue(filas.get(3).enviada());
        verify(nsys).porImeis(Set.of(IMEI1));
    }

    @Test void sinFilasConImeiNoSeConsultaNsys() {
        when(dao.delPeriodo(any(), any())).thenReturn(List.of(fila(1, null, null)));
        assertEquals(1, vista.filas(null, null, null).filas().size());
        verifyNoInteractions(nsys, taller);
    }

    @Test void enOtraFilaVivaCuentaTodasLasVivasConEseImeiAunqueNoEstenEnLaPagina() {
        when(dao.buscar(eq("0001"), anyInt())).thenReturn(List.of(fila(1, IMEI1, null)));
        when(dao.vivasConImei(any())).thenReturn(List.of(fila(1, IMEI1, null), fila(9, IMEI1, null)));
        nsysConUnMovilQueCuadra(movil(IMEI1));

        FilaVista f = vista.filas(null, null, "0001").filas().get(0);

        assertEquals("MAL", f.nivel());
        assertEquals("EN_OTRA_FILA", f.motivo());
    }

    @Test void dosFilasVivasConElMismoImeiSeAvisanLasDos() {
        when(dao.delPeriodo(any(), any())).thenReturn(List.of(fila(1, IMEI1, null), fila(2, IMEI1, null)));
        when(dao.vivasConImei(any())).thenReturn(List.of(fila(1, IMEI1, null), fila(2, IMEI1, null)));
        nsysConUnMovilQueCuadra(movil(IMEI1));

        List<FilaVista> filas = vista.filas(null, null, null).filas();

        assertEquals("EN_OTRA_FILA", filas.get(0).motivo());
        assertEquals("EN_OTRA_FILA", filas.get(1).motivo());
    }

    @Test void unImeiQueNoEstaEnNsysEsMal() {
        when(dao.delPeriodo(any(), any())).thenReturn(List.of(fila(1, IMEI2, null)));
        when(dao.vivasConImei(any())).thenReturn(List.of(fila(1, IMEI2, null)));
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

    @Test void recomendadosVaciosSiLaFilaEstaEnviada() {
        when(dao.porId(7)).thenReturn(Optional.of(fila(7, null, AHORA.minusDays(1))));
        assertEquals(List.of(), vista.recomendados(7).orElseThrow());
        verifyNoInteractions(nsys);
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
        when(dao.vivasDeShippyPro()).thenReturn(List.of(propia, fila(8, IMEI2, null), fila(9, null, null)));
        when(nsys.productosPorSkuDeCanal(any())).thenReturn(List.of(
                AceptaSkuTest.producto("Azul", "A", "i13128AzulA", "i13128AzulA", null, null)));
        when(nsys.enEmpresa()).thenReturn(List.of(movil(IMEI1), movil(IMEI2), movil("350000000000003")));
        when(nsys.trabajosAbiertos()).thenReturn(Map.of());
        when(taller.valedorDeTodos()).thenReturn(x -> new LoQueVale.Valor("Azul", "A", "i13128AzulA", null, null, null, false));

        List<Recomendador.Candidato> r = vista.recomendados(7).orElseThrow();

        assertEquals(List.of(IMEI1, "350000000000003"), r.stream().map(Recomendador.Candidato::imei).toList());
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
}
