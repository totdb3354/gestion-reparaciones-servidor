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
import java.time.LocalDate;
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

    private static AvisoTaller sinColor() {
        return new AvisoTaller(5, IMEI, "R20261024_19", AvisoTaller.Tipo.SIN_COLOR, null, COLOR, "chai13yellow", null,
                "chasis chai13yellow nuevo", 8, "Técnico 2", AHORA.minusDays(1));
    }

    @Test void lasTarjetasJuntanLoDelTallerLasAltasYLosAvisos() {
        when(taller.movilesPorPasar()).thenReturn(List.of(soloGrado(IMEI)));
        when(nsys.clavesDelEspejo()).thenReturn(Set.of(IMEI, "F2LXABC123"));
        when(nsys.imeisDelTaller()).thenReturn(List.of(IMEI, "f2lxabc123", "350000000000088"));
        when(nsys.altas(List.of("350000000000088"))).thenReturn(List.of(new AltaTaller("350000000000088", "14", "R20261006_57",
                false, "Técnico 2", null, 1, AHORA.minusDays(19), AHORA.minusDays(19))));
        when(tallerDao.avisosPorRevisar()).thenReturn(List.of(sinColor()));
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
        when(tallerDao.avisoPorId(5)).thenReturn(Optional.of(sinColor()));
        when(tallerDao.revisar(5, 7, AHORA)).thenReturn(1);
        servicio.revisado(5, 7);
        verify(registro).insertar(7, "REVISAR_AVISO_TALLER", "IMEI: 350000000000001, SIN_COLOR de R20261024_19", null);
    }

    @Test void revisadoDeUnAvisoQueNoExiste() {
        assertEquals(HttpStatus.NOT_FOUND, estado(() -> servicio.revisado(9, 7)));
    }

    @Test void revisadoDeUnAvisoYaRevisadoNoHaceNada() {
        AvisoTaller a = sinColor();
        when(tallerDao.avisoPorId(5)).thenReturn(Optional.of(new AvisoTaller(a.id(), a.imeiSn(), a.idRep(), a.tipo(), a.causa(),
                a.dato(), a.de(), a.a(), a.motivo(), a.idUsu(), a.usuario(), a.fecha(), "Supertécnico 1", AHORA.minusMinutes(1))));
        assertDoesNotThrow(() -> servicio.revisado(5, 7));
        verify(tallerDao, never()).revisar(anyInt(), anyInt(), any());
        verify(registro, never()).insertar(anyInt(), any(), any(), any());
    }

    @Test void revisadoDeUnAvisoQueNoEsDeRevisarAManoDa400() {
        AvisoTaller a = sinColor();
        when(tallerDao.avisoPorId(5)).thenReturn(Optional.of(new AvisoTaller(a.id(), a.imeiSn(), a.idRep(),
                AvisoTaller.Tipo.DESHECHO, AvisoTaller.Causa.BORRADA, a.dato(), a.de(), a.a(), a.motivo(), a.idUsu(), a.usuario(),
                a.fecha())));
        when(tallerDao.revisar(5, 7, AHORA)).thenReturn(0);
        assertEquals(HttpStatus.BAD_REQUEST, estado(() -> servicio.revisado(5, 7)));
        verifyNoInteractions(registro);
    }

    @Test void revisadoDeUnGradoSinCalcularDa400PorqueYaNoEsDeRevisarAMano() {
        when(tallerDao.avisoPorId(6)).thenReturn(Optional.of(new AvisoTaller(6, IMEI, "R20261024_20", AvisoTaller.Tipo.SIN_GRADO,
                null, GRADO, "n/a", null, "batería bati13 nueva", 8, "Técnico 2", AHORA.minusDays(1))));
        when(tallerDao.revisar(6, 7, AHORA)).thenReturn(0);
        assertEquals(HttpStatus.BAD_REQUEST, estado(() -> servicio.revisado(6, 7)));
        verifyNoInteractions(registro);
    }

    @Test void revisadoSiLoAcabaDeRevisarOtroEntreMediasNoHaceNada() {
        when(tallerDao.avisoPorId(5)).thenReturn(Optional.of(sinColor()));
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

    // ---------------------------------------------------------------- 0.9.22 pieza 4: los que han vuelto (spec §6)

    private static TallerNsys.MovilPorPasar vendidoConAmbosCambios(String imei, LocalDate venta) {
        return new TallerNsys.MovilPorPasar(vendido(imei, "Azul", "B", venta),
                new LoQueVale.Valor("Rosa", "A100", null, "Azul", "B", null, true),
                List.of(vigente(1, imei, COLOR, "Rosa", "Azul", AHORA.minusDays(1)), vigente(2, imei, GRADO, "A100", "B", AHORA.minusDays(1))),
                new ProductoNsys.Poner("Apple iPhone 13 128GB Pink", false));
    }

    private static AvisoTaller sinColorDe(int id, String imei) {
        return new AvisoTaller(id, imei, "R20261024_" + id, AvisoTaller.Tipo.SIN_COLOR, null, COLOR, "chai13yellow", null,
                "chasis chai13yellow nuevo", 8, "Técnico 2", AHORA.minusDays(1));
    }

    @Test void unVendidoQueHaVueltoSaleMarcadoEnColorYGradoYUnoQueNoNo() {
        String vuelto = "350000000000002";
        when(taller.movilesPorPasar()).thenReturn(List.of(vendidoConAmbosCambios(vuelto, LocalDate.of(2026, 9, 14)),
                vendidoConAmbosCambios("350000000000003", LocalDate.of(2026, 10, 24))));
        when(nsys.trabajosDe(any())).thenReturn(List.of(
                new NsysDAO.TrabajoFecha(vuelto, "R20261022_101", LocalDateTime.of(2026, 10, 22, 14, 6)),
                new NsysDAO.TrabajoFecha("350000000000003", "R20261023_7", LocalDateTime.of(2026, 10, 23, 9, 17))));
        TarjetasPorPasar t = servicio.tarjetas();
        assertEquals(List.of(vuelto), t.color().stream().map(FilaColorNsys::imeiSn).toList());
        assertEquals(LocalDate.of(2026, 9, 14), t.color().get(0).vendidoEn());
        assertEquals(LocalDateTime.of(2026, 10, 22, 14, 6), t.color().get(0).vueltoDesde());
        assertEquals(List.of(vuelto), t.grado().stream().map(FilaGradoNsys::imeiSn).toList());
        assertEquals(LocalDate.of(2026, 9, 14), t.grado().get(0).vendidoEn());
    }

    @Test void laPlantillaDeColorNoLlevaLosQueHanVuelto() {
        String vuelto = "350000000000002";
        when(taller.movilesPorPasar()).thenReturn(List.of(vendidoConAmbosCambios(vuelto, LocalDate.of(2026, 9, 14))));
        when(nsys.trabajosDe(any())).thenReturn(List.of(
                new NsysDAO.TrabajoFecha(vuelto, "R20261022_101", LocalDateTime.of(2026, 10, 22, 14, 6))));
        assertEquals(0, servicio.plantillaColor().filas());
        verify(nsys, never()).costes(any());
    }

    @Test void revisarSinLosAvisosDeVendidosQueNoHanVueltoYConLosQueSi() {
        String vuelto = "350000000000002";
        String vendidoSinMas = "350000000000003";
        when(tallerDao.avisosPorRevisar()).thenReturn(List.of(sinColorDe(1, IMEI), sinColorDe(2, vuelto), sinColorDe(3, vendidoSinMas)));
        when(nsys.porImeis(any())).thenReturn(List.of(movil(IMEI, "Azul", "B"), vendido(vuelto, "Azul", "B", LocalDate.of(2026, 9, 14)),
                vendido(vendidoSinMas, "Azul", "B", LocalDate.of(2026, 10, 24))));
        when(nsys.trabajosDe(any())).thenReturn(List.of(
                new NsysDAO.TrabajoFecha(vuelto, "R20261022_101", LocalDateTime.of(2026, 10, 22, 14, 6))));
        TarjetasPorPasar t = servicio.tarjetas();
        assertEquals(List.of(IMEI, vuelto), t.revisar().stream().map(FilaRevisarNsys::imeiSn).toList());
        assertNull(t.revisar().get(0).vueltoDesde());
        assertEquals(LocalDate.of(2026, 9, 14), t.revisar().get(1).vendidoEn());
    }

    @Test void conTodoEnLaEmpresaNoSeLeenTrabajos() {
        when(taller.movilesPorPasar()).thenReturn(List.of(soloGrado(IMEI)));
        servicio.tarjetas();
        verify(nsys, never()).trabajosDe(any());
    }

    // ---------------------------------------------------------------- 0.9.22 pieza 4: «Sin grado en NSYS» (spec §4.1)

    @Test void sinGradoLeeLosTrabajosDesdeEl22YSoloSiHayAlgunoLoDemas() {
        assertTrue(servicio.tarjetas().sinGrado().isEmpty());
        verify(nsys).trabajosDesde(LocalDateTime.of(2026, 10, 22, 12, 49));
        verify(nsys, never()).porImeis(any());
        verify(tallerDao, never()).bateriasRecordadas(any());
    }

    @Test void sinGradoConUnTrabajoDeUnMovilNaYSuBateriaRecordada() {
        when(nsys.trabajosDesde(any())).thenReturn(List.of(new NsysDAO.TrabajoFecha(IMEI, "A20261025_9", AHORA.minusHours(2))));
        when(nsys.porImeis(List.of(IMEI))).thenReturn(List.of(movil(IMEI, "Azul", "n/a")));
        when(tallerDao.bateriasRecordadas(List.of(IMEI))).thenReturn(List.of(new BateriaRecordada(40, IMEI, "R20261025_8", false,
                "batería bati13 reutilizada", 8, AHORA.minusHours(3))));
        TarjetasPorPasar t = servicio.tarjetas();
        assertEquals(List.of(IMEI), t.sinGrado().stream().map(FilaSinGradoNsys::imeiSn).toList());
        assertEquals("A20261025_9", t.sinGrado().get(0).ultimoTrabajo());
        assertEquals(Boolean.FALSE, t.sinGrado().get(0).bateriaNueva());
        verify(tallerDao).vigentes(List.of(IMEI));
    }

    @Test void unMovilConGradoNoSaleEnSinGrado() {
        when(nsys.trabajosDesde(any())).thenReturn(List.of(new NsysDAO.TrabajoFecha(IMEI, "A20261025_9", AHORA.minusHours(2))));
        when(nsys.porImeis(List.of(IMEI))).thenReturn(List.of(movil(IMEI, "Azul", "B")));
        assertTrue(servicio.tarjetas().sinGrado().isEmpty());
        verify(tallerDao, never()).bateriasRecordadas(anyCollection());
    }

    /** Casi todos los móviles con trabajo desde el corte acaban con letra y vendidos: el grado se mira antes que sus vueltas y sus
     *  baterías. Los dos vendidos, si llegaran a vueltas(), pedirían sus trabajos. */
    @Test void sinGradoFiltraPorGradoAntesDeMirarVueltasYBaterias() {
        String vendidoConLetra = "350000000000011", vendidoConLetraDelTaller = "350000000000012", enStock = "350000000000013";
        LocalDateTime trasElCorte = LocalDateTime.of(2026, 10, 23, 9, 17);
        when(nsys.trabajosDesde(any())).thenReturn(List.of(new NsysDAO.TrabajoFecha(vendidoConLetra, "R20261023_1", trasElCorte),
                new NsysDAO.TrabajoFecha(vendidoConLetraDelTaller, "R20261023_2", trasElCorte),
                new NsysDAO.TrabajoFecha(enStock, "R20261023_3", trasElCorte)));
        when(nsys.porImeis(List.of(vendidoConLetra, vendidoConLetraDelTaller, enStock))).thenReturn(List.of(
                vendido(vendidoConLetra, "Azul", "B", LocalDate.of(2026, 9, 14)),
                vendido(vendidoConLetraDelTaller, "Azul", "n/a", LocalDate.of(2026, 9, 14)), movil(enStock, "Azul", "n/a")));
        when(tallerDao.vigentes(anyCollection())).thenReturn(List.of(vigente(9, vendidoConLetraDelTaller, GRADO, "A", "n/a")));
        TarjetasPorPasar t = servicio.tarjetas();
        assertEquals(List.of(enStock), t.sinGrado().stream().map(FilaSinGradoNsys::imeiSn).toList());
        verify(nsys, never()).trabajosDe(any());
        verify(tallerDao).bateriasRecordadas(List.of(enStock));
        verify(tallerDao).bateriasRecordadas(anyCollection());
    }

    // ---------------------------------------------------------------- 0.9.22 pieza 4: baterías tras la importación (spec §5.2)

    private static BateriaRecordada bateria(int id, boolean nueva, LocalDateTime fecha) {
        return new BateriaRecordada(id, IMEI, "R20261024_" + id, nueva, nueva ? "batería bati13 nueva" : "batería bati13 reutilizada",
                8, fecha);
    }

    @Test void conLetraDeNsysLaBateriaNuevaRecordadaPoneEl100ANombreDeSuReparacion() {
        when(tallerDao.bateriasRecordadas()).thenReturn(List.of(bateria(40, true, AHORA.minusDays(1))));
        when(nsys.porImeis(List.of(IMEI))).thenReturn(List.of(movil(IMEI, "Azul", "A")));
        when(tallerDao.usarBaterias(any(), any(), any())).thenReturn(1);
        servicio.usarBateriasRecordadas();
        verify(tallerDao).guardar(IMEI, List.of(new TallerDAO.Nuevo(GRADO, "A100", "A", CambioTaller.Estado.VIGENTE)), "R20261024_40",
                8, "batería bati13 nueva", AHORA);
        verify(tallerDao).usarBaterias(List.of(40), "A100", AHORA);
    }

    @Test void conLetraDelTallerTambienSeAplica() {
        when(tallerDao.bateriasRecordadas()).thenReturn(List.of(bateria(40, true, AHORA.minusDays(1))));
        when(nsys.porImeis(List.of(IMEI))).thenReturn(List.of(movil(IMEI, "Azul", "n/a")));
        when(tallerDao.vigentes(List.of(IMEI))).thenReturn(List.of(vigente(9, IMEI, GRADO, "A", "n/a")));
        servicio.usarBateriasRecordadas();
        verify(tallerDao).guardar(IMEI, List.of(new TallerDAO.Nuevo(GRADO, "A100", "n/a", CambioTaller.Estado.VIGENTE)),
                "R20261024_40", 8, "batería bati13 nueva", AHORA);
        verify(tallerDao).usarBaterias(List.of(40), "A100", AHORA);
    }

    @Test void siYaLlevaEl100NoCambiaNadaYLaDaPorUsada() {
        when(tallerDao.bateriasRecordadas()).thenReturn(List.of(bateria(40, true, AHORA.minusDays(1))));
        when(nsys.porImeis(List.of(IMEI))).thenReturn(List.of(movil(IMEI, "Azul", "A100")));
        servicio.usarBateriasRecordadas();
        verify(tallerDao, never()).guardar(any(), any(), any(), any(), any(), any());
        verify(tallerDao).usarBaterias(List.of(40), "A100", AHORA);
    }

    @Test void conUnGradoIgnoradoSeDaPorUsadaSinCambiar() {
        when(tallerDao.bateriasRecordadas()).thenReturn(List.of(bateria(40, true, AHORA.minusDays(1))));
        when(nsys.porImeis(List.of(IMEI))).thenReturn(List.of(movil(IMEI, "Azul", "F")));
        servicio.usarBateriasRecordadas();
        verify(tallerDao, never()).guardar(any(), any(), any(), any(), any(), any());
        verify(tallerDao).usarBaterias(List.of(40), "F", AHORA);
    }

    @Test void sinGradoTodaviaSigueRecordada() {
        when(tallerDao.bateriasRecordadas()).thenReturn(List.of(bateria(40, true, AHORA.minusDays(1))));
        when(nsys.porImeis(List.of(IMEI))).thenReturn(List.of(movil(IMEI, "Azul", "n/a")));
        servicio.usarBateriasRecordadas();
        verify(tallerDao, never()).usarBaterias(any(), any(), any());
        verify(tallerDao, never()).guardar(any(), any(), any(), any(), any(), any());
    }

    @Test void conVariasValeLaMasRecienteYSeUsanTodas() {
        when(tallerDao.bateriasRecordadas()).thenReturn(List.of(bateria(40, true, AHORA.minusDays(2)), bateria(41, false, AHORA.minusDays(1))));
        when(nsys.porImeis(List.of(IMEI))).thenReturn(List.of(movil(IMEI, "Azul", "A100")));
        servicio.usarBateriasRecordadas();
        verify(tallerDao).guardar(IMEI, List.of(new TallerDAO.Nuevo(GRADO, "A", "A100", CambioTaller.Estado.VIGENTE)), "R20261024_41",
                8, "batería bati13 reutilizada", AHORA);
        verify(tallerDao).usarBaterias(List.of(40, 41), "A", AHORA);
    }

    @Test void sinBateriasRecordadasNoLeeNadaMas() {
        servicio.usarBateriasRecordadas();
        verify(nsys, never()).porImeis(any());
        verify(tallerDao, never()).vigentes(any());
    }

    @Test void siUnMovilFallaLosDemasSiSeAplican() {
        String otro = "350000000000002";
        BateriaRecordada b1 = bateria(40, true, AHORA.minusDays(1));
        BateriaRecordada b2 = new BateriaRecordada(41, otro, "R20261024_41", true, "batería bati13 nueva", 8, AHORA.minusDays(1));
        when(tallerDao.bateriasRecordadas()).thenReturn(List.of(b1, b2));
        when(nsys.porImeis(List.of(IMEI, otro))).thenReturn(List.of(movil(IMEI, "Azul", "A"), movil(otro, "Azul", "A")));
        when(tallerDao.usarBaterias(any(), any(), any())).thenReturn(1);
        doThrow(new IllegalStateException("fallo")).when(tallerDao).guardar(eq(IMEI), any(), any(), any(), any(), any());
        servicio.usarBateriasRecordadas();
        verify(tallerDao).guardar(otro, List.of(new TallerDAO.Nuevo(GRADO, "A100", "A", CambioTaller.Estado.VIGENTE)), "R20261024_41",
                8, "batería bati13 nueva", AHORA);
        verify(tallerDao).usarBaterias(List.of(41), "A100", AHORA);
        verify(tallerDao, never()).usarBaterias(eq(List.of(40)), any(), any());
    }
}
