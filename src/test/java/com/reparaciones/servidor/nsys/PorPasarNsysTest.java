package com.reparaciones.servidor.nsys;

import com.reparaciones.servidor.dao.LogDAO;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static com.reparaciones.servidor.nsys.DatoTaller.COLOR;
import static com.reparaciones.servidor.nsys.DatoTaller.GRADO;
import static com.reparaciones.servidor.nsys.TallerDePrueba.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** «Por pasar a NSYS» con cuatro tarjetas (spec 0.9.22 §3) con todo mockeado y la hora fija (25/10/2026 12:31 en Madrid). */
class PorPasarNsysTest {

    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");
    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 10, 25, 12, 31);
    private static final String IMEI = "350000000000001";
    private final TallerNsys taller = mock(TallerNsys.class);
    private final TallerDAO tallerDao = mock(TallerDAO.class);
    private final NsysDAO nsys = mock(NsysDAO.class);
    private final HechoDAO hechos = mock(HechoDAO.class);
    private final DescarteDAO descartes = mock(DescarteDAO.class);
    private final LogDAO registro = mock(LogDAO.class);
    private final PorPasarNsys servicio = new PorPasarNsys(taller, tallerDao, nsys, hechos, descartes, registro,
            Clock.fixed(AHORA.atZone(MADRID).toInstant(), MADRID));

    private static HttpStatusCode estado(Executable accion) {
        return assertThrows(ResponseStatusException.class, accion).getStatusCode();
    }

    private static TallerNsys.MovilPorPasar soloGrado(String imei) {
        return new TallerNsys.MovilPorPasar(movil(imei, "Azul", "B"), new LoQueVale.Valor("Azul", "A100", null, null, "B", null, true),
                List.of(vigente(2, imei, GRADO, "A100", "B", AHORA.minusDays(1))), null);
    }

    private static AvisoTaller sinGrado() {
        return new AvisoTaller(5, IMEI, "R20261024_19", AvisoTaller.Tipo.SIN_GRADO, null, GRADO, "ID", null,
                "chasis chai13blue nuevo", 8, "Técnico 2", AHORA.minusDays(1));
    }

    @Test void lasTarjetasJuntanLoDelTallerLasAltasYLosAvisos() {
        when(taller.movilesPorPasar()).thenReturn(List.of(soloGrado(IMEI)));
        when(nsys.clavesDelEspejo()).thenReturn(Set.of(IMEI, "F2LXABC123"));
        when(nsys.imeisDelTaller()).thenReturn(List.of(IMEI, "f2lxabc123", "350000000000088"));
        when(nsys.altas(List.of("350000000000088"))).thenReturn(List.of(new AltaTaller("350000000000088", "14", "R20261006_57",
                false, "Técnico 2", null, 1, AHORA.minusDays(19), AHORA.minusDays(19))));
        when(tallerDao.avisosPorRevisar()).thenReturn(List.of(sinGrado()));
        when(nsys.porImeis(List.of(IMEI))).thenReturn(List.of(movil(IMEI, "Azul", "B")));
        TarjetasPorPasar t = servicio.tarjetas();
        assertTrue(t.color().isEmpty());
        assertEquals(List.of(IMEI), t.grado().stream().map(FilaGradoNsys::imeiSn).toList());
        assertEquals(List.of("350000000000088"), t.altas().stream().map(FilaAltaNsys::imeiSn).toList(),
                "un número de serie en minúsculas cruza con el del espejo");
        assertEquals("13", t.revisar().get(0).modelo());
    }

    @Test void sinImeisFueraDeNsysNiAvisosNoSeLeeNadaMas() {
        when(nsys.clavesDelEspejo()).thenReturn(Set.of(IMEI));
        when(nsys.imeisDelTaller()).thenReturn(List.of(IMEI));
        TarjetasPorPasar t = servicio.tarjetas();
        assertTrue(t.altas().isEmpty());
        assertTrue(t.revisar().isEmpty());
        verify(nsys, never()).altas(any());
        verify(nsys, never()).porImeis(any());
        verify(descartes, never()).todos();
    }

    @Test void marcarLimpiaLosImeisYPoneLaHoraDeMadrid() {
        servicio.marcar("GRADO", List.of(" 350000000000001 ", IMEI, "350000000000002"), 7);
        verify(hechos).marcar(TarjetaHecho.GRADO, List.of(IMEI, "350000000000002"), 7, AHORA);
    }

    @Test void marcarRechazaLoQueNoVale() {
        assertEquals(HttpStatus.BAD_REQUEST, estado(() -> servicio.marcar("REVISAR", List.of(IMEI), 7)));
        assertEquals(HttpStatus.BAD_REQUEST, estado(() -> servicio.marcar(null, List.of(IMEI), 7)));
        assertEquals(HttpStatus.BAD_REQUEST, estado(() -> servicio.marcar("COLOR", List.of(), 7)));
        assertEquals(HttpStatus.BAD_REQUEST, estado(() -> servicio.marcar("COLOR", null, 7)));
        assertEquals(HttpStatus.BAD_REQUEST, estado(() -> servicio.marcar("COLOR", Arrays.asList(IMEI, " "), 7)));
        List<String> muchos = new ArrayList<>();
        for (int n = 0; n < 501; n++) muchos.add(String.format("3500000000%05d", n));
        assertEquals(HttpStatus.BAD_REQUEST, estado(() -> servicio.marcar("COLOR", muchos, 7)));
        verifyNoInteractions(hechos);
    }

    @Test void marcarRechazaUnImeiDeMasDe40Caracteres() {
        String largo = "9".repeat(41);
        assertEquals(HttpStatus.BAD_REQUEST, estado(() -> servicio.marcar("COLOR", List.of(IMEI, largo), 7)));
        verify(hechos, never()).marcar(any(), any(), anyInt(), any());
    }

    @Test void marcarAdmiteUnImeiDe40CaracteresYLoLimpia() {
        String justo = "9".repeat(40);
        servicio.marcar("COLOR", List.of(" " + justo + " "), 7);
        verify(hechos).marcar(TarjetaHecho.COLOR, List.of(justo), 7, AHORA);
    }

    @Test void deshacerRechazaUnImeiDeMasDe40Caracteres() {
        assertEquals(HttpStatus.BAD_REQUEST, estado(() -> servicio.desmarcar("COLOR", "9".repeat(41))));
        verify(hechos, never()).desmarcar(any(), any());
    }

    @Test void deshacerQuitaLaMarcaDeEsaTarjeta() {
        servicio.desmarcar("ALTA", " 350000000000088 ");
        verify(hechos).desmarcar(TarjetaHecho.ALTA, "350000000000088");
        assertEquals(HttpStatus.BAD_REQUEST, estado(() -> servicio.desmarcar("ALTA", " ")));
        assertEquals(HttpStatus.BAD_REQUEST, estado(() -> servicio.desmarcar("OTRA", IMEI)));
    }

    @Test void revisadoApuntaQuienYCuandoYLoDejaEnElRegistro() {
        when(tallerDao.avisoPorId(5)).thenReturn(Optional.of(sinGrado()));
        when(tallerDao.revisar(5, 7, AHORA)).thenReturn(1);
        servicio.revisado(5, 7);
        verify(registro).insertar(7, "REVISAR_AVISO_TALLER", "IMEI: 350000000000001, SIN_GRADO de R20261024_19", null);
    }

    @Test void revisadoDeUnAvisoQueNoExiste() {
        assertEquals(HttpStatus.NOT_FOUND, estado(() -> servicio.revisado(9, 7)));
    }

    @Test void revisadoDeUnAvisoYaRevisadoNoHaceNada() {
        AvisoTaller a = sinGrado();
        when(tallerDao.avisoPorId(5)).thenReturn(Optional.of(new AvisoTaller(a.id(), a.imeiSn(), a.idRep(), a.tipo(), a.causa(),
                a.dato(), a.de(), a.a(), a.motivo(), a.idUsu(), a.usuario(), a.fecha(), "Supertécnico 1", AHORA.minusMinutes(1))));
        assertDoesNotThrow(() -> servicio.revisado(5, 7));
        verify(tallerDao, never()).revisar(anyInt(), anyInt(), any());
        verify(registro, never()).insertar(anyInt(), any(), any(), any());
    }

    @Test void revisadoDeUnAvisoQueNoEsDeRevisarAManoDa400() {
        AvisoTaller a = sinGrado();
        when(tallerDao.avisoPorId(5)).thenReturn(Optional.of(new AvisoTaller(a.id(), a.imeiSn(), a.idRep(),
                AvisoTaller.Tipo.DESHECHO, AvisoTaller.Causa.BORRADA, a.dato(), a.de(), a.a(), a.motivo(), a.idUsu(), a.usuario(),
                a.fecha())));
        when(tallerDao.revisar(5, 7, AHORA)).thenReturn(0);
        assertEquals(HttpStatus.BAD_REQUEST, estado(() -> servicio.revisado(5, 7)));
        verifyNoInteractions(registro);
    }

    @Test void revisadoSiLoAcabaDeRevisarOtroEntreMediasNoHaceNada() {
        when(tallerDao.avisoPorId(5)).thenReturn(Optional.of(sinGrado()));
        when(tallerDao.revisar(5, 7, AHORA)).thenReturn(0);
        assertDoesNotThrow(() -> servicio.revisado(5, 7));
        verifyNoInteractions(registro);
    }

    /** Con REPEATABLE READ, MariaDB 11.6+ (innodb_snapshot_isolation) rechaza con el error 1020 el UPDATE de un segundo clic que
     *  leyó el aviso antes de que el primero lo revisara (visto en preprod el 26/10); con READ COMMITTED espera y no cambia nada. */
    @Test void revisadoVaEnReadCommittedParaQueUnSegundoClicNoFalle() throws NoSuchMethodException {
        Transactional t = PorPasarNsys.class.getMethod("revisado", int.class, int.class).getAnnotation(Transactional.class);
        assertNotNull(t, "el UPDATE y la línea del registro van en una transacción");
        assertEquals(Isolation.READ_COMMITTED, t.isolation());
    }

    @Test void elRepasoSinMarcasNoLeeNadaMas() {
        servicio.repasarHechos(AHORA);
        verifyNoInteractions(taller, nsys);
        verify(hechos, never()).repasar(any(), any(), any());
    }

    @Test void elRepasoBorraYHaceVolverConLaHoraDeAhora() {
        HechoTaller seFue = new HechoTaller("350000000000009", TarjetaHecho.COLOR, 7, "Supertécnico 1", AHORA.minusHours(2), null);
        HechoTaller sigue = new HechoTaller(IMEI, TarjetaHecho.GRADO, 7, "Supertécnico 1", AHORA.minusHours(2), null);
        when(hechos.todas()).thenReturn(List.of(seFue, sigue));
        when(taller.movilesPorPasar()).thenReturn(List.of(soloGrado(IMEI)));
        servicio.repasarHechos(AHORA.minusMinutes(30));
        verify(hechos).repasar(List.of(seFue), List.of(sigue), AHORA);
    }

    @Test void unRepasoSinNadaQueHacerNoEscribe() {
        when(hechos.todas()).thenReturn(List.of(new HechoTaller(IMEI, TarjetaHecho.GRADO, 7, "Supertécnico 1", AHORA, null)));
        when(taller.movilesPorPasar()).thenReturn(List.of(soloGrado(IMEI)));
        servicio.repasarHechos(AHORA.minusMinutes(30));
        verify(hechos, never()).repasar(any(), any(), any());
    }

    private static TallerNsys.MovilPorPasar deColor(MovilNsys m, ProductoNsys.Poner poner) {
        return new TallerNsys.MovilPorPasar(m, new LoQueVale.Valor("Rosa", "B", null, "Azul", null, null, true),
                List.of(vigente(1, m.imeiSn(), COLOR, "Rosa", "Azul", AHORA.minusDays(1))), poner);
    }

    @Test void laPlantillaDeColorLlevaLoPendienteConProductoYElCosteDeSuLote() throws Exception {
        MovilNsys conProducto = movil(IMEI, "Azul", "B");
        MovilNsys sinProducto = movil("350000000000002", "Azul", "B");
        MovilNsys marcado = movil("350000000000003", "Azul", "B");
        ProductoNsys.Poner rosa = new ProductoNsys.Poner("Apple iPhone 13 128GB Pink", false);
        when(taller.movilesPorPasar()).thenReturn(List.of(deColor(conProducto, rosa), deColor(sinProducto, null),
                deColor(marcado, rosa)));
        when(hechos.todas()).thenReturn(List.of(new HechoTaller("350000000000003", TarjetaHecho.COLOR, 7, "Supertécnico 1",
                AHORA, null)));
        when(nsys.costes(List.of(conProducto.clave()))).thenReturn(Map.of(conProducto.clave(), new BigDecimal("412.35")));
        PorPasarNsys.Plantilla p = servicio.plantillaColor();
        assertEquals("NSYS_color_2026-10-25.xlsx", p.nombre());
        assertEquals(1, p.filas());
        try (XSSFWorkbook libro = new XSSFWorkbook(new ByteArrayInputStream(p.contenido()))) {
            Row fila = libro.getSheetAt(0).getRow(PlantillaNsys.PRIMERA_FILA);
            assertEquals(IMEI, fila.getCell(0).getStringCellValue());
            assertEquals("iPhone 13", fila.getCell(2).getStringCellValue());
            assertEquals("128 GB", fila.getCell(3).getStringCellValue());
            assertEquals("Pink", fila.getCell(4).getStringCellValue());
            assertEquals("B", fila.getCell(5).getStringCellValue(), "el grado de NSYS hoy");
            assertEquals(412.35, fila.getCell(6).getNumericCellValue(), 0.0001);
            assertEquals("Proveedor A", fila.getCell(7).getStringCellValue());
        }
    }

    @Test void laPlantillaDeAltasLlevaLoPendienteConElModeloDelTaller() throws Exception {
        when(nsys.imeisDelTaller()).thenReturn(List.of("350000000000088"));
        when(nsys.altas(List.of("350000000000088"))).thenReturn(List.of(new AltaTaller("350000000000088", "14pro",
                "R20261006_57", false, "Técnico 2", null, 1, AHORA.minusDays(19), AHORA.minusDays(19))));
        PorPasarNsys.Plantilla p = servicio.plantillaAltas();
        assertEquals("NSYS_altas_2026-10-25.xlsx", p.nombre());
        assertEquals(1, p.filas());
        try (XSSFWorkbook libro = new XSSFWorkbook(new ByteArrayInputStream(p.contenido()))) {
            Row fila = libro.getSheetAt(0).getRow(PlantillaNsys.PRIMERA_FILA);
            assertEquals("350000000000088", fila.getCell(0).getStringCellValue());
            assertEquals("iPhone 14 Pro", fila.getCell(2).getStringCellValue());
            assertEquals("AJUSTE", fila.getCell(8).getStringCellValue());
        }
    }

    @Test void sinNadaPendienteLaPlantillaVaVaciaYNoPideCostes() {
        PorPasarNsys.Plantilla p = servicio.plantillaColor();
        assertEquals(0, p.filas());
        verify(nsys, never()).costes(any());
    }

    private static MovilNsys conEstado(MovilNsys m, EstadoMovil estado) {
        return new MovilNsys(m.imeiSn(), m.batch(), m.goods(), m.grade(), m.status(), m.location(), m.supplier(), m.receivingDate(),
                m.soldDate(), m.sku(), m.modelo(), m.modeloMaestro(), m.capacidad(), m.color(), m.gradoDetalle(), m.esEsim(),
                m.ubicacion(), m.ubicacionUltima(), estado, m.vistoPrimero(), m.cambiado());
    }

    /** Un móvil con cambio de color y de grado vigentes, en el estado dado. */
    private static TallerNsys.MovilPorPasar conAmbosCambios(String imei, EstadoMovil estado) {
        return new TallerNsys.MovilPorPasar(conEstado(movil(imei, "Azul", "B"), estado),
                new LoQueVale.Valor("Rosa", "A100", null, "Azul", "B", null, true),
                List.of(vigente(1, imei, COLOR, "Rosa", "Azul", AHORA.minusDays(1)), vigente(2, imei, GRADO, "A100", "B", AHORA.minusDays(1))),
                new ProductoNsys.Poner("Apple iPhone 13 128GB Pink", false));
    }

    @Test void colorYGradoSoloLlevanLosQueNsysTieneEnLaEmpresa() {
        when(taller.movilesPorPasar()).thenReturn(List.of(conAmbosCambios(IMEI, EstadoMovil.STOCK),
                conAmbosCambios("350000000000002", EstadoMovil.VENDIDO)));
        TarjetasPorPasar t = servicio.tarjetas();
        assertEquals(List.of(IMEI), t.color().stream().map(FilaColorNsys::imeiSn).toList());
        assertEquals(List.of(IMEI), t.grado().stream().map(FilaGradoNsys::imeiSn).toList());
    }

    @Test void unaBajaNoSaleYUnoDePedidoSi() {
        when(taller.movilesPorPasar()).thenReturn(List.of(conAmbosCambios(IMEI, EstadoMovil.BAJA),
                conAmbosCambios("350000000000002", EstadoMovil.DE_PEDIDO)));
        TarjetasPorPasar t = servicio.tarjetas();
        assertEquals(List.of("350000000000002"), t.color().stream().map(FilaColorNsys::imeiSn).toList());
        assertEquals(List.of("350000000000002"), t.grado().stream().map(FilaGradoNsys::imeiSn).toList());
    }

    @Test void laPlantillaDeColorNoLlevaLosVendidos() {
        MovilNsys enStock = conAmbosCambios(IMEI, EstadoMovil.STOCK).movil();
        when(taller.movilesPorPasar()).thenReturn(List.of(conAmbosCambios(IMEI, EstadoMovil.STOCK),
                conAmbosCambios("350000000000002", EstadoMovil.VENDIDO)));
        when(nsys.costes(List.of(enStock.clave()))).thenReturn(Map.of(enStock.clave(), new BigDecimal("412.35")));
        assertEquals(1, servicio.plantillaColor().filas());
        verify(nsys).costes(List.of(enStock.clave()));
    }

    @Test void elRepasoBorraLaMarcaDeUnVendidoAunqueSeaPosteriorAlCambio() {
        HechoTaller deVendido = new HechoTaller(IMEI, TarjetaHecho.COLOR, 7, "Supertécnico 1", AHORA.minusHours(2), null);
        when(hechos.todas()).thenReturn(List.of(deVendido));
        when(taller.movilesPorPasar()).thenReturn(List.of(conAmbosCambios(IMEI, EstadoMovil.VENDIDO)));
        servicio.repasarHechos(AHORA.minusMinutes(30));
        verify(hechos).repasar(List.of(deVendido), List.of(), AHORA);
    }

    private static AltaTaller alta(String imei, LocalDateTime ultimoEn) {
        return new AltaTaller(imei, "14", "R20261006_57", false, "Técnico 2", null, 1, AHORA.minusDays(19), ultimoEn);
    }

    @Test void lasTarjetasSeparanLosDescartados() {
        when(nsys.imeisDelTaller()).thenReturn(List.of("350000000000088", "SIN-IMEI-CAM"));
        when(nsys.altas(List.of("350000000000088", "SIN-IMEI-CAM"))).thenReturn(List.of(
                alta("350000000000088", AHORA.minusDays(2)), alta("SIN-IMEI-CAM", AHORA.minusDays(2))));
        when(descartes.todos()).thenReturn(List.of(new DescarteTaller("sin-imei-cam", 7, "Supertécnico 1", AHORA.minusDays(1),
                "no es un móvil")));
        TarjetasPorPasar t = servicio.tarjetas();
        assertEquals(List.of("350000000000088"), t.altas().stream().map(FilaAltaNsys::imeiSn).toList());
        assertEquals(List.of("SIN-IMEI-CAM"), t.descartadas().stream().map(FilaDescartadaNsys::imeiSn).toList());
    }

    @Test void laPlantillaDeAltasNoLlevaLosDescartados() {
        when(nsys.imeisDelTaller()).thenReturn(List.of("350000000000088", "SIN-IMEI-CAM"));
        when(nsys.altas(List.of("350000000000088", "SIN-IMEI-CAM"))).thenReturn(List.of(
                alta("350000000000088", AHORA.minusDays(2)), alta("SIN-IMEI-CAM", AHORA.minusDays(2))));
        when(descartes.todos()).thenReturn(List.of(new DescarteTaller("SIN-IMEI-CAM", 7, "Supertécnico 1", AHORA.minusDays(1),
                "no es un móvil")));
        assertEquals(1, servicio.plantillaAltas().filas());
    }

    @Test void descartarGuardaQuitaLaMarcaYLoApuntaConElMotivo() {
        servicio.descartar(" SIN-IMEI-CAM ", "  no es un móvil, es una cámara ", 7);
        verify(descartes).descartar("SIN-IMEI-CAM", 7, "no es un móvil, es una cámara", AHORA);
        verify(hechos).desmarcar(TarjetaHecho.ALTA, "SIN-IMEI-CAM");
        verify(registro).insertar(7, "DESCARTAR_ALTA_NSYS", "IMEI: SIN-IMEI-CAM", "no es un móvil, es una cámara");
    }

    @Test void descartarRechazaLoQueNoVale() {
        assertEquals(HttpStatus.BAD_REQUEST, estado(() -> servicio.descartar(null, "motivo", 7)));
        assertEquals(HttpStatus.BAD_REQUEST, estado(() -> servicio.descartar(" ", "motivo", 7)));
        assertEquals(HttpStatus.BAD_REQUEST, estado(() -> servicio.descartar("9".repeat(41), "motivo", 7)));
        assertEquals(HttpStatus.BAD_REQUEST, estado(() -> servicio.descartar("SIN-IMEI-CAM", null, 7)));
        assertEquals(HttpStatus.BAD_REQUEST, estado(() -> servicio.descartar("SIN-IMEI-CAM", "   ", 7)));
        assertEquals(HttpStatus.BAD_REQUEST, estado(() -> servicio.descartar("SIN-IMEI-CAM", "x".repeat(501), 7)));
        verifyNoInteractions(descartes, hechos, registro);
        servicio.descartar("SIN-IMEI-CAM", "x".repeat(500), 7);
        verify(descartes).descartar("SIN-IMEI-CAM", 7, "x".repeat(500), AHORA);
    }

    @Test void losMensajesDeLos400() {
        assertEquals("Falta el IMEI", assertThrows(ResponseStatusException.class, () -> servicio.descartar(" ", "m", 7)).getReason());
        assertEquals("Un IMEI no puede pasar de 40 caracteres",
                assertThrows(ResponseStatusException.class, () -> servicio.descartar("9".repeat(41), "m", 7)).getReason());
        assertEquals("Falta el motivo",
                assertThrows(ResponseStatusException.class, () -> servicio.descartar("SIN-IMEI-CAM", " ", 7)).getReason());
        assertEquals("El motivo no puede pasar de 500 caracteres",
                assertThrows(ResponseStatusException.class, () -> servicio.descartar("SIN-IMEI-CAM", "x".repeat(501), 7)).getReason());
    }

    @Test void deshacerElDescarteSoloApuntaSiHabiaUno() {
        when(descartes.deshacer("SIN-IMEI-CAM")).thenReturn(1);
        servicio.deshacerDescarte(" SIN-IMEI-CAM ", 7);
        verify(registro).insertar(7, "DESHACER_DESCARTE_NSYS", "IMEI: SIN-IMEI-CAM", null);
        clearInvocations(registro);
        servicio.deshacerDescarte("350000000000088", 7);
        verify(descartes).deshacer("350000000000088");
        verifyNoInteractions(registro);
        assertEquals(HttpStatus.BAD_REQUEST, estado(() -> servicio.deshacerDescarte(" ", 7)));
        assertEquals(HttpStatus.BAD_REQUEST, estado(() -> servicio.deshacerDescarte("9".repeat(41), 7)));
    }

    /** Como «Revisado»: escritura y línea del registro en la misma transacción, en READ COMMITTED (error 1020 de MariaDB). */
    @Test void lasEscriturasDelDescarteVanEnReadCommitted() throws NoSuchMethodException {
        for (var m : List.of(PorPasarNsys.class.getMethod("descartar", String.class, String.class, int.class),
                PorPasarNsys.class.getMethod("deshacerDescarte", String.class, int.class))) {
            Transactional t = m.getAnnotation(Transactional.class);
            assertNotNull(t, m.getName());
            assertEquals(Isolation.READ_COMMITTED, t.isolation(), m.getName());
        }
    }

    @Test void elRepasoQuitaLosDescartesDeImeisQueNsysYaConoce() {
        when(descartes.todos()).thenReturn(List.of(
                new DescarteTaller("sin-imei-cam", 7, "Supertécnico 1", AHORA, "no es un móvil"),
                new DescarteTaller("350000000000088", 7, "Supertécnico 1", AHORA, "móvil de un cliente")));
        when(nsys.clavesDelEspejo()).thenReturn(Set.of("SIN-IMEI-CAM"));
        servicio.repasarDescartes();
        verify(descartes).quitar(List.of("sin-imei-cam"));
    }

    @Test void sinDescartesElRepasoNoLeeElEspejo() {
        servicio.repasarDescartes();
        verify(nsys, never()).clavesDelEspejo();
        verify(descartes, never()).quitar(any());
    }

    @Test void siNsysNoConoceNingunoElRepasoNoEscribe() {
        when(descartes.todos()).thenReturn(List.of(new DescarteTaller("SIN-IMEI-CAM", 7, "Supertécnico 1", AHORA, "no es un móvil")));
        when(nsys.clavesDelEspejo()).thenReturn(Set.of("350000000000001"));
        servicio.repasarDescartes();
        verify(descartes, never()).quitar(any());
    }
}
