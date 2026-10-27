package com.reparaciones.servidor.nsys;

import com.reparaciones.servidor.dao.ColorEquivalenciaDAO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.reparaciones.servidor.nsys.CambioTaller.Estado.*;
import static com.reparaciones.servidor.nsys.DatoTaller.COLOR;
import static com.reparaciones.servidor.nsys.DatoTaller.GRADO;
import static com.reparaciones.servidor.nsys.TallerDePrueba.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** El SKU del taller desde las reparaciones, con los DAO mockeados y la hora fija (21/10/2026 10:30 en Madrid). El móvil de
 *  prueba es un iPhone 13 128GB Azul · B; sus SKU: 101 bati13, 111 lcdi13, 131 chai13red, 132 chai13pink (Rosa no tiene
 *  ningún móvil en NSYS: no se deduce) y 133 chai13blue. */
class TallerReparacionesTest {

    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");
    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 10, 21, 10, 30);
    private static final String IMEI = "350000000000001";
    private static final String R1 = "R20261021_3";
    private static final String R2 = "R20261021_4";
    private final NsysDAO nsys = mock(NsysDAO.class);
    private final TallerDAO taller = mock(TallerDAO.class);
    private final ColorEquivalenciaDAO colores = mock(ColorEquivalenciaDAO.class);
    private final TallerReparaciones servicio = new TallerReparaciones(nsys, taller, colores,
            Clock.fixed(AHORA.atZone(MADRID).toInstant(), MADRID));

    @BeforeEach void base() {
        when(nsys.porImei(IMEI)).thenReturn(List.of(movil(IMEI, "Azul", "B")));
        when(taller.bloquear(IMEI)).thenReturn(true);
        when(taller.deshacer(anyInt(), any())).thenReturn(1);
        when(nsys.cargarProductos()).thenReturn(List.of(producto("Blue", "Azul", "B"), producto("(PRODUCT)RED", "Rojo", "B"),
                producto("Pink", "Rosa", "B")));
        when(nsys.goodsMasUsados(any())).thenReturn(Map.of("Azul", "Apple iPhone 13 128GB Blue",
                "Rojo", "Apple iPhone 13 128GB (PRODUCT)RED"));
        when(taller.tiposDeComponentes(any())).thenReturn(Map.of(101, "bati13", 111, "lcdi13", 131, "chai13red",
                132, "chai13pink", 133, "chai13blue"));
    }

    private static TallerReparaciones.PiezaGuardada fila(String idRep, int idCom, boolean reutilizada) {
        return new TallerReparaciones.PiezaGuardada(idRep, idCom, reutilizada);
    }

    // ---------------------------------------------------------------- al guardar (spec §2.1)

    @Test void unChasisDeOtroColorCambiaElColorYElGradoConLaReparacion() {
        servicio.aplicar(IMEI, List.of(fila(R1, 131, false)), 8);
        InOrder orden = inOrder(taller);
        orden.verify(taller).bloquear(IMEI);
        orden.verify(taller).vigentes(List.of(IMEI));
        orden.verify(taller).guardar(IMEI, List.of(new TallerDAO.Nuevo(COLOR, "Rojo", "Azul", VIGENTE),
                new TallerDAO.Nuevo(GRADO, "A", "B", VIGENTE)), R1, 8, "chasis chai13red nuevo", AHORA);
        verify(taller, never()).avisar(any(), anyInt(), any());
    }

    @Test void elChasisSeAplicaAntesQueLaBateriaYCadaFilaConSuReparacion() {
        // 0.9.21.1: el chasis va primero aunque su fila sea la segunda.
        servicio.aplicar(IMEI, List.of(fila(R1, 101, false), fila(R2, 131, true)), 8);
        InOrder orden = inOrder(taller);
        orden.verify(taller).guardar(IMEI, List.of(new TallerDAO.Nuevo(COLOR, "Rojo", "Azul", VIGENTE),
                new TallerDAO.Nuevo(GRADO, "A", "B", VIGENTE)), R2, 8, "chasis chai13red reutilizado", AHORA);
        orden.verify(taller).guardar(IMEI, List.of(new TallerDAO.Nuevo(GRADO, "A100", "B", VIGENTE)), R1, 8,
                "batería bati13 nueva", AHORA);
    }

    @Test void unColorQueNoSeDeduceSeAvisaYElGradoCambia() {
        servicio.aplicar(IMEI, List.of(fila(R1, 132, false)), 8);
        verify(taller).guardar(IMEI, List.of(new TallerDAO.Nuevo(GRADO, "A", "B", VIGENTE)), R1, 8, "chasis chai13pink nuevo", AHORA);
        verify(taller).avisar(new AvisoTaller.Nuevo(IMEI, R1, AvisoTaller.Tipo.SIN_COLOR, null, COLOR, "chai13pink", null,
                "chasis chai13pink nuevo"), 8, AHORA);
    }

    @Test void unColorSinMovilesEnNsysSeDeduceConElNombreDelMaestro() {
        when(nsys.cargarProductos()).thenReturn(List.of(producto("Blue", "Azul", "B"), producto("(PRODUCT)RED", "Rojo", "B"),
                productoConNombre("Pink", "Rosa", "B")));
        servicio.aplicar(IMEI, List.of(fila(R1, 132, false)), 8);
        verify(taller).guardar(IMEI, List.of(new TallerDAO.Nuevo(COLOR, "Rosa", "Azul", VIGENTE),
                new TallerDAO.Nuevo(GRADO, "A", "B", VIGENTE)), R1, 8, "chasis chai13pink nuevo", AHORA);
        verify(taller, never()).avisar(any(), anyInt(), any());
    }

    @Test void unaBateriaEnUnComoNaSeAvisaYNoCambia() {
        when(nsys.porImei(IMEI)).thenReturn(List.of(movil(IMEI, "Azul", "n/a")));
        servicio.aplicar(IMEI, List.of(fila(R1, 101, false)), 8);
        verify(taller, never()).guardar(any(), any(), any(), anyInt(), any(), any());
        verify(taller).avisar(new AvisoTaller.Nuevo(IMEI, R1, AvisoTaller.Tipo.SIN_GRADO, null, GRADO, "n/a", null,
                "batería bati13 nueva"), 8, AHORA);
    }

    @Test void unaBateriaEnUnGradoIgnoradoNiCambiaNiAvisa() {
        when(nsys.porImei(IMEI)).thenReturn(List.of(movil(IMEI, "Azul", "ID")));
        servicio.aplicar(IMEI, List.of(fila(R1, 101, false)), 8);
        verify(taller, never()).guardar(any(), any(), any(), anyInt(), any(), any());
        verify(taller, never()).avisar(any(), anyInt(), any());
    }

    @Test void unImeiQueNsysNoConoceNoCambiaNada() {
        when(taller.bloquear(IMEI)).thenReturn(false);
        servicio.aplicar(IMEI, List.of(fila(R1, 131, false)), 8);
        verify(taller, never()).vigentes(any());
        verify(taller, never()).guardar(any(), any(), any(), anyInt(), any(), any());
    }

    @Test void unaPiezaQueNoCuentaOUnaGlassNoTocanElMovil() {
        servicio.aplicar(IMEI, List.of(fila(R1, 111, false)), 8);
        servicio.aplicar(IMEI, List.of(fila("G20261021_1", 131, false)), 8);
        verify(taller, never()).bloquear(any());
    }

    @Test void elCandadoDelMovilEsElDelTaller() {
        assertTrue(servicio.bloquear(IMEI));
        assertFalse(servicio.bloquear("359999999999999"));
        verify(taller).bloquear(IMEI);
        verify(taller).bloquear("359999999999999");
        verifyNoInteractions(nsys, colores);
    }

    // ---------------------------------------------------------------- editar y borrar (spec §2.3)

    @Test void deshacerUnVigenteQueSustituyoAOtroDevuelveElAnterior() {
        CambioTaller a = aMano(4, IMEI, COLOR, "Verde", "Azul", SUSTITUIDO, null);
        CambioTaller c = deReparacion(5, IMEI, COLOR, "Rojo", "Azul", R1, VIGENTE, 4);
        when(taller.deReparacion(R1)).thenReturn(List.of(c));
        when(taller.deImei(IMEI)).thenReturn(List.of(a, c));
        servicio.deshacer(R1, AvisoTaller.Causa.BORRADA, 7);
        InOrder orden = inOrder(taller);
        orden.verify(taller).bloquear(IMEI);
        orden.verify(taller).deshacer(5, AHORA);
        orden.verify(taller).reactivar(4);
        orden.verify(taller).avisar(new AvisoTaller.Nuevo(IMEI, R1, AvisoTaller.Tipo.DESHECHO, AvisoTaller.Causa.BORRADA, COLOR,
                "Rojo", "Verde", "chasis chai13red nuevo"), 7, AHORA);
    }

    @Test void siElCambioYaNoEraVigenteNoSeReactivaNadaYSoloSeAvisa() {
        CambioTaller a = aMano(4, IMEI, COLOR, "Verde", "Azul", SUSTITUIDO, null);
        CambioTaller c = deReparacion(5, IMEI, COLOR, "Rojo", "Azul", R1, VIGENTE, 4);
        when(taller.deReparacion(R1)).thenReturn(List.of(c));
        when(taller.deImei(IMEI)).thenReturn(List.of(a, c));
        when(taller.deshacer(5, AHORA)).thenReturn(0);
        servicio.deshacer(R1, AvisoTaller.Causa.BORRADA, 7);
        verify(taller).deshacer(5, AHORA);
        verify(taller, never()).reactivar(anyInt());
        verify(taller).avisar(new AvisoTaller.Nuevo(IMEI, R1, AvisoTaller.Tipo.SIN_DESHACER, AvisoTaller.Causa.BORRADA, COLOR,
                "Rojo", null, "chasis chai13red nuevo"), 7, AHORA);
    }

    @Test void deshacerSinAnteriorVuelveALoDeNsys() {
        CambioTaller c = deReparacion(5, IMEI, COLOR, "Rojo", "Azul", R1, VIGENTE, null);
        when(taller.deReparacion(R1)).thenReturn(List.of(c));
        when(taller.deImei(IMEI)).thenReturn(List.of(c));
        servicio.deshacer(R1, AvisoTaller.Causa.BORRADA, 7);
        verify(taller).deshacer(5, AHORA);
        verify(taller, never()).reactivar(anyInt());
        verify(taller).avisar(new AvisoTaller.Nuevo(IMEI, R1, AvisoTaller.Tipo.DESHECHO, AvisoTaller.Causa.BORRADA, COLOR,
                "Rojo", "Azul", "chasis chai13red nuevo"), 7, AHORA);
    }

    @Test void loQueYaNoMandaSoloSeAvisa() {
        CambioTaller c = deReparacion(5, IMEI, COLOR, "Rojo", "Azul", R1, PASADO, null);
        when(taller.deReparacion(R1)).thenReturn(List.of(c));
        when(taller.deImei(IMEI)).thenReturn(List.of(c));
        servicio.deshacer(R1, AvisoTaller.Causa.EDITADA, 7);
        verify(taller, never()).deshacer(anyInt(), any());
        verify(taller).avisar(new AvisoTaller.Nuevo(IMEI, R1, AvisoTaller.Tipo.SIN_DESHACER, AvisoTaller.Causa.EDITADA, COLOR,
                "Rojo", null, "chasis chai13red nuevo"), 7, AHORA);
    }

    @Test void unaReparacionSinCambiosDelTallerNoTocaNada() {
        servicio.deshacer(R1, AvisoTaller.Causa.BORRADA, 7);
        servicio.deshacer("G20261021_1", AvisoTaller.Causa.BORRADA, 7);
        verify(taller, never()).bloquear(any());
        verify(taller, never()).avisar(any(), anyInt(), any());
    }

    @Test void editarConLaMismaPiezaNoTocaNada() {
        servicio.editar(new TallerDAO.PiezaDeReparacion(R1, IMEI, 131, false), fila(R1, 131, false), 7);
        verify(taller, never()).deReparacion(any());
        verify(taller, never()).bloquear(any());
    }

    @Test void editarConOtraPiezaDeshaceLoSuyoYAplicaLaNueva() {
        CambioTaller color = deReparacion(5, IMEI, COLOR, "Rojo", "Azul", R1, VIGENTE, null);
        CambioTaller grado = deReparacion(6, IMEI, GRADO, "A", "B", R1, VIGENTE, null);
        when(taller.deReparacion(R1)).thenReturn(List.of(color, grado));
        when(taller.deImei(IMEI)).thenReturn(List.of(color, grado));
        servicio.editar(new TallerDAO.PiezaDeReparacion(R1, IMEI, 131, false), fila(R1, 133, true), 7);
        InOrder orden = inOrder(taller);
        orden.verify(taller).deshacer(5, AHORA);
        orden.verify(taller).avisar(new AvisoTaller.Nuevo(IMEI, R1, AvisoTaller.Tipo.DESHECHO, AvisoTaller.Causa.EDITADA, COLOR,
                "Rojo", "Azul", "chasis chai13red nuevo"), 7, AHORA);
        orden.verify(taller).deshacer(6, AHORA);
        orden.verify(taller).avisar(new AvisoTaller.Nuevo(IMEI, R1, AvisoTaller.Tipo.DESHECHO, AvisoTaller.Causa.EDITADA, GRADO,
                "A", "B", "chasis chai13red nuevo"), 7, AHORA);
        // Tras deshacer vale Azul · B (el mock de vigentes está vacío): el chasis azul solo pone la A.
        orden.verify(taller).guardar(IMEI, List.of(new TallerDAO.Nuevo(GRADO, "A", "B", VIGENTE)), R1, 7,
                "chasis chai13blue reutilizado", AHORA);
    }

    @Test void laPiezaDeUnaReparacionSoloParaLasR() {
        when(taller.piezaDeReparacion(R1)).thenReturn(Optional.of(new TallerDAO.PiezaDeReparacion(R1, IMEI, 131, false)));
        assertEquals(131, servicio.pieza(R1).orElseThrow().idCom());
        assertTrue(servicio.pieza("G20261021_1").isEmpty());
        verify(taller, never()).piezaDeReparacion("G20261021_1");
    }

    // ---------------------------------------------------------------- la banda del formulario (spec §2.4)

    @Test void previsualizarHaceLaMismaCuentaSinEscribir() {
        PreviaTaller previa = servicio.previsualizar(new PeticionPreviaTaller(IMEI, List.of(new FilaPreviaTaller(null, 131, false))));
        assertEquals(new PreviaTaller(true, new ValoresTaller("Azul", "B"), new ValoresTaller("Rojo", "A"), false, false), previa);
        verify(taller, never()).bloquear(any());
        verify(taller, never()).guardar(any(), any(), any(), anyInt(), any(), any());
        verify(taller, never()).avisar(any(), anyInt(), any());
    }

    @Test void previsualizarConLosAvisosYVariasFilasJuntas() {
        PreviaTaller previa = servicio.previsualizar(new PeticionPreviaTaller(IMEI,
                List.of(new FilaPreviaTaller(null, 132, false), new FilaPreviaTaller(null, 101, false))));
        assertEquals(new PreviaTaller(true, new ValoresTaller("Azul", "B"), new ValoresTaller("Azul", "A100"), true, false), previa);
    }

    @Test void previsualizarSinPiezasQueCuentenDejaElMovilIgual() {
        assertEquals(new PreviaTaller(true, new ValoresTaller("Azul", "B"), new ValoresTaller("Azul", "B"), false, false),
                servicio.previsualizar(new PeticionPreviaTaller(IMEI, List.of(new FilaPreviaTaller(null, 111, false)))));
    }

    @Test void previsualizarRechazaDemasiadasFilas() {
        List<FilaPreviaTaller> muchas = java.util.Collections.nCopies(51, new FilaPreviaTaller(null, 131, false));
        org.springframework.web.server.ResponseStatusException e = assertThrows(
                org.springframework.web.server.ResponseStatusException.class,
                () -> servicio.previsualizar(new PeticionPreviaTaller(IMEI, muchas)));
        assertEquals(org.springframework.http.HttpStatus.BAD_REQUEST, e.getStatusCode());
    }

    @Test void previsualizarIgnoraLasFilasNulas() {
        List<FilaPreviaTaller> filas = new java.util.ArrayList<>();
        filas.add(null);
        filas.add(new FilaPreviaTaller(null, 131, false));
        assertEquals(new PreviaTaller(true, new ValoresTaller("Azul", "B"), new ValoresTaller("Rojo", "A"), false, false),
                servicio.previsualizar(new PeticionPreviaTaller(IMEI, filas)));
    }

    @Test void previsualizarUnImeiQueNsysNoConoce() {
        when(nsys.porImei("350000000000099")).thenReturn(List.of());
        assertEquals(PreviaTaller.FUERA_DE_NSYS, servicio.previsualizar(new PeticionPreviaTaller("350000000000099", List.of())));
        assertEquals(PreviaTaller.FUERA_DE_NSYS, servicio.previsualizar(new PeticionPreviaTaller("  ", List.of())));
        assertFalse(PreviaTaller.FUERA_DE_NSYS.enNsys());
    }

    @Test void previsualizarUnaEdicionDeshaceLoSuyoAntesDeAplicarLaNueva() {
        // La R… puso Rojo · A con un chasis rojo; se edita a un chasis rosa (no se deduce): al deshacer vuelve Azul · B y el
        // rosa solo pone la A. Sin deshacer se vería Rojo · A como si nada.
        CambioTaller color = deReparacion(5, IMEI, COLOR, "Rojo", "Azul", R1, VIGENTE, null);
        CambioTaller grado = deReparacion(6, IMEI, GRADO, "A", "B", R1, VIGENTE, null);
        when(taller.deImei(IMEI)).thenReturn(List.of(color, grado));
        when(taller.piezaDeReparacion(R1)).thenReturn(Optional.of(new TallerDAO.PiezaDeReparacion(R1, IMEI, 131, false)));
        PreviaTaller previa = servicio.previsualizar(new PeticionPreviaTaller(IMEI, List.of(new FilaPreviaTaller(R1, 132, false))));
        assertEquals(new PreviaTaller(true, new ValoresTaller("Rojo", "A"), new ValoresTaller("Azul", "A"), true, false), previa);
        verify(taller, never()).deshacer(anyInt(), any());
    }

    @Test void previsualizarUnaEdicionConLaMismaPiezaNoDeshace() {
        CambioTaller color = deReparacion(5, IMEI, COLOR, "Rojo", "Azul", R1, VIGENTE, null);
        when(taller.deImei(IMEI)).thenReturn(List.of(color));
        when(taller.piezaDeReparacion(R1)).thenReturn(Optional.of(new TallerDAO.PiezaDeReparacion(R1, IMEI, 131, false)));
        assertEquals(new PreviaTaller(true, new ValoresTaller("Rojo", "B"), new ValoresTaller("Rojo", "B"), false, false),
                servicio.previsualizar(new PeticionPreviaTaller(IMEI, List.of(new FilaPreviaTaller(R1, 131, false)))));
    }

    // ---------------------------------------------------------------- 0.9.22 pieza 4: la batería recordada (spec §5.2)

    @Test void unChasisDespuesDeUnaBateriaNuevaRecordadaQuedaEnA100YLaDaPorUsada() {
        when(nsys.porImei(IMEI)).thenReturn(List.of(movil(IMEI, "Azul", "n/a")));
        when(taller.bateriasRecordadas(List.of(IMEI))).thenReturn(List.of(
                new BateriaRecordada(40, IMEI, R1, true, "batería bati13 nueva", 8, AHORA.minusSeconds(4))));
        servicio.aplicar(IMEI, List.of(fila(R2, 133, true)), 8);
        verify(taller).guardar(IMEI, List.of(new TallerDAO.Nuevo(GRADO, "A100", "n/a", VIGENTE)), R2, 8,
                "chasis chai13blue reutilizado", AHORA);
        verify(taller).usarBaterias(List.of(40), "A100", AHORA);
        verify(taller, never()).avisar(any(), anyInt(), any());
    }

    @Test void sinChasisLaBateriaRecordadaSigue() {
        when(nsys.porImei(IMEI)).thenReturn(List.of(movil(IMEI, "Azul", "n/a")));
        when(taller.bateriasRecordadas(List.of(IMEI))).thenReturn(List.of(
                new BateriaRecordada(40, IMEI, R1, true, "batería bati13 nueva", 8, AHORA.minusSeconds(4))));
        servicio.aplicar(IMEI, List.of(fila(R2, 101, false)), 8);
        verify(taller, never()).usarBaterias(any(), any(), any());
    }

    // ---------------------------------------------------------------- 0.9.22 pieza 4: la batería recordada tras la importación (§5.2)

    private static BateriaRecordada bateria(int id, boolean nueva, LocalDateTime fecha) {
        return new BateriaRecordada(id, IMEI, "R20261020_" + id, nueva, nueva ? "batería bati13 nueva" : "batería bati13 reutilizada",
                8, fecha);
    }

    @Test void trasLaImportacionConLetraDeNsysLaBateriaNuevaPoneEl100ANombreDeSuReparacion() {
        when(taller.bateriasRecordadas(List.of(IMEI))).thenReturn(List.of(bateria(40, true, AHORA.minusDays(1))));
        when(taller.usarBaterias(any(), any(), any())).thenReturn(1);
        assertEquals(new TallerReparaciones.BateriaUsada(1, true), servicio.usarBateriaRecordada(movil(IMEI, "Azul", "A")));
        InOrder orden = inOrder(taller);
        orden.verify(taller).bloquear(IMEI);
        orden.verify(taller).guardar(IMEI, List.of(new TallerDAO.Nuevo(GRADO, "A100", "A", VIGENTE)), "R20261020_40", 8,
                "batería bati13 nueva", AHORA);
        orden.verify(taller).usarBaterias(List.of(40), "A100", AHORA);
    }

    @Test void trasLaImportacionConLetraDelTallerTambienSeAplica() {
        when(taller.bateriasRecordadas(List.of(IMEI))).thenReturn(List.of(bateria(40, true, AHORA.minusDays(1))));
        when(taller.vigentes(List.of(IMEI))).thenReturn(List.of(vigente(9, IMEI, GRADO, "A", "n/a")));
        servicio.usarBateriaRecordada(movil(IMEI, "Azul", "n/a"));
        verify(taller).guardar(IMEI, List.of(new TallerDAO.Nuevo(GRADO, "A100", "n/a", VIGENTE)), "R20261020_40", 8,
                "batería bati13 nueva", AHORA);
        verify(taller).usarBaterias(List.of(40), "A100", AHORA);
    }

    @Test void trasLaImportacionSiYaLlevaEl100NoCambiaNadaYLaDaPorUsada() {
        when(taller.bateriasRecordadas(List.of(IMEI))).thenReturn(List.of(bateria(40, true, AHORA.minusDays(1))));
        when(taller.usarBaterias(any(), any(), any())).thenReturn(1);
        assertEquals(new TallerReparaciones.BateriaUsada(1, false), servicio.usarBateriaRecordada(movil(IMEI, "Azul", "A100")));
        verify(taller, never()).guardar(any(), any(), any(), any(), any(), any());
        verify(taller).usarBaterias(List.of(40), "A100", AHORA);
    }

    @Test void trasLaImportacionConUnGradoIgnoradoSeDaPorUsadaSinCambiar() {
        when(taller.bateriasRecordadas(List.of(IMEI))).thenReturn(List.of(bateria(40, true, AHORA.minusDays(1))));
        servicio.usarBateriaRecordada(movil(IMEI, "Azul", "F"));
        verify(taller, never()).guardar(any(), any(), any(), any(), any(), any());
        verify(taller).usarBaterias(List.of(40), "F", AHORA);
    }

    @Test void trasLaImportacionSinGradoTodaviaSigueRecordada() {
        when(taller.bateriasRecordadas(List.of(IMEI))).thenReturn(List.of(bateria(40, true, AHORA.minusDays(1))));
        assertEquals(new TallerReparaciones.BateriaUsada(0, false), servicio.usarBateriaRecordada(movil(IMEI, "Azul", "n/a")));
        verify(taller, never()).usarBaterias(any(), any(), any());
        verify(taller, never()).guardar(any(), any(), any(), any(), any(), any());
    }

    @Test void trasLaImportacionConVariasValeLaMasRecienteYSeUsanTodas() {
        when(taller.bateriasRecordadas(List.of(IMEI))).thenReturn(List.of(bateria(40, true, AHORA.minusDays(2)),
                bateria(41, false, AHORA.minusDays(1))));
        servicio.usarBateriaRecordada(movil(IMEI, "Azul", "A100"));
        verify(taller).guardar(IMEI, List.of(new TallerDAO.Nuevo(GRADO, "A", "A100", VIGENTE)), "R20261020_41", 8,
                "batería bati13 reutilizada", AHORA);
        verify(taller).usarBaterias(List.of(40, 41), "A", AHORA);
    }

    /** La lectura de todos (el prefiltro de PorPasarNsys) vio la «A» de NSYS; entre medias se guardó a mano un «B100». Con el candado
     *  se relee lo de ese IMEI: el grado que vale es B100 y ya lleva el 100, así que no crea nada que lo pise y solo la da por usada. */
    @Test void trasLaImportacionReleeLoVigenteDeEseImeiConElCandado() {
        when(taller.bateriasRecordadas(List.of(IMEI))).thenReturn(List.of(bateria(40, true, AHORA.minusDays(1))));
        when(taller.vigentes(List.of(IMEI))).thenReturn(List.of(vigente(9, IMEI, GRADO, "B100", "A")));
        servicio.usarBateriaRecordada(movil(IMEI, "Azul", "A"));
        InOrder orden = inOrder(taller);
        orden.verify(taller).bloquear(IMEI);
        orden.verify(taller).vigentes(List.of(IMEI));
        orden.verify(taller).usarBaterias(List.of(40), "B100", AHORA);
        verify(taller, never()).guardar(any(), any(), any(), any(), any(), any());
    }

    /** Un «Corregir…» que dejó letra entre medias ya las dio por usadas: al releer no queda ninguna y no se hace nada. */
    @Test void trasLaImportacionSiYaNoQuedaNingunaRecordadaNoHaceNada() {
        assertEquals(new TallerReparaciones.BateriaUsada(0, false), servicio.usarBateriaRecordada(movil(IMEI, "Azul", "A")));
        InOrder orden = inOrder(taller);
        orden.verify(taller).bloquear(IMEI);
        orden.verify(taller).bateriasRecordadas(List.of(IMEI));
        verify(taller, never()).guardar(any(), any(), any(), any(), any(), any());
        verify(taller, never()).usarBaterias(any(), any(), any());
    }

    @Test void trasLaImportacionUnImeiQueNsysYaNoConoceNoCambiaNada() {
        when(taller.bloquear(IMEI)).thenReturn(false);
        when(taller.bateriasRecordadas(List.of(IMEI))).thenReturn(List.of(bateria(40, true, AHORA.minusDays(1))));
        servicio.usarBateriaRecordada(movil(IMEI, "Azul", "A"));
        verify(taller, never()).guardar(any(), any(), any(), any(), any(), any());
        verify(taller, never()).usarBaterias(any(), any(), any());
    }

    /** Cada móvil en su transacción, con el candado del IMEI tomado dentro, y READ COMMITTED como «Revisado» y «Corregir…» (con
     *  REPEATABLE READ, MariaDB 11.6+ da el error 1020). PorPasarNsys la llama desde fuera: pasa por el proxy de Spring. */
    @Test void trasLaImportacionCadaMovilVaEnSuTransaccionEnReadCommitted() throws NoSuchMethodException {
        Transactional t = TallerReparaciones.class.getMethod("usarBateriaRecordada", MovilNsys.class).getAnnotation(Transactional.class);
        assertNotNull(t, "el cambio de grado y dar por usadas van juntos, con el candado");
        assertEquals(Isolation.READ_COMMITTED, t.isolation());
    }
}
