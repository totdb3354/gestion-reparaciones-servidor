package com.reparaciones.servidor.controller;

import com.reparaciones.servidor.dao.*;
import com.reparaciones.servidor.idempotencia.RegistroIdempotencia;
import com.reparaciones.servidor.model.EstadoAbrir;
import com.reparaciones.servidor.model.ReparacionResumen;
import com.reparaciones.servidor.model.ResultadoEntregaGlass;
import com.reparaciones.servidor.security.UsuarioPrincipal;
import com.reparaciones.servidor.service.CargaAsignacionesService;
import com.reparaciones.servidor.service.EntregaGlassService;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ReparacionControllerEntregaGlassTest {

    private static final String IMEI = "351111112222333";
    private final ReparacionDAO dao = mock(ReparacionDAO.class);
    private final LogDAO logDao = mock(LogDAO.class);
    private final EntregaGlassService servicio = mock(EntregaGlassService.class);
    private final ReparacionController ctl = new ReparacionController(
            dao, mock(ReparacionComponenteDAO.class), logDao, mock(BorradorDAO.class), mock(ComponenteDAO.class),
            mock(DificultadPuntosDAO.class), mock(TecnicoDAO.class), new RegistroIdempotencia(),
            mock(CargaAsignacionesService.class), servicio);
    private final UsuarioPrincipal tecnico = new UsuarioPrincipal(42, "tecnico_j", "x", "TECNICO", 7);

    private ReparacionResumen asigDe(int idTec) {
        ReparacionResumen a = mock(ReparacionResumen.class);
        when(a.getIdTec()).thenReturn(idTec);
        when(a.getImei()).thenReturn(IMEI);
        return a;
    }

    /** Invoca y devuelve el status del ResponseStatusException lanzado. */
    private int statusDe(Runnable r) {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, r::run);
        return ex.getStatusCode().value();
    }

    private static ReparacionController.EntregaGlassRequest req(boolean entregado) {
        return new ReparacionController.EntregaGlassRequest(entregado);
    }

    /** Asignación A… propia con una glass abierta de «Técnico H». */
    private ReparacionResumen propiaConGlass() {
        ReparacionResumen asig = asigDe(7);
        when(dao.getAsignacionAnyById("A20260828_1")).thenReturn(Optional.of(asig));
        when(dao.getGlassAbiertas(IMEI)).thenReturn(List.of(new ReparacionDAO.GlassAbierta("AG20260828_3", "Técnico H")));
        return asig;
    }

    @Test void rechazaIdsQueNoSonReparacionNormal() {
        assertEquals(422, statusDe(() -> ctl.actualizarEntregaGlass("AG20260828_1", req(true), tecnico)));
        assertEquals(422, statusDe(() -> ctl.actualizarEntregaGlass("AP20260828_1", req(true), tecnico)));
        verifyNoInteractions(logDao, servicio);
    }

    @Test void asignacionInexistenteOCerradaEs404() {
        when(dao.getAsignacionAnyById("A20260828_1")).thenReturn(Optional.empty());
        assertEquals(404, statusDe(() -> ctl.actualizarEntregaGlass("A20260828_1", req(true), tecnico)));
    }

    @Test void soloElDuenoPuedeEntregar() {
        ReparacionResumen asig = asigDe(99);
        when(dao.getAsignacionAnyById("A20260828_1")).thenReturn(Optional.of(asig));
        assertEquals(403, statusDe(() -> ctl.actualizarEntregaGlass("A20260828_1", req(true), tecnico)));
        UsuarioPrincipal admin = new UsuarioPrincipal(1, "admin", "x", "ADMIN", null);
        assertEquals(403, statusDe(() -> ctl.actualizarEntregaGlass("A20260828_1", req(true), admin)));
        verifyNoInteractions(servicio);
    }

    @Test void sinGlassAbiertaEs422YNoEscribe() {
        ReparacionResumen asig = asigDe(7);
        when(dao.getAsignacionAnyById("A20260828_1")).thenReturn(Optional.of(asig));
        when(dao.getGlassAbiertas(IMEI)).thenReturn(List.of());
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> ctl.actualizarEntregaGlass("A20260828_1", req(true), tecnico));
        assertEquals(422, ex.getStatusCode().value());
        assertEquals("Sin glass abierta para este IMEI", ex.getReason());
        verifyNoInteractions(logDao, servicio);
    }

    @Test void entregarLlamaAlServicioDevuelveSuResultadoYAnotaElAbrir() {
        propiaConGlass();
        ResultadoEntregaGlass creado = ResultadoEntregaGlass.creado("R20260828_9", "Técnico J");
        when(servicio.entregar(IMEI, 7, "A20260828_1")).thenReturn(creado);
        assertEquals(creado, ctl.actualizarEntregaGlass("A20260828_1", req(true), tecnico));
        verify(servicio, never()).deshacer(anyString());
        verify(logDao).insertar(42, "ENTREGAR_GLASS",
                "ID_REP: A20260828_1, IMEI: " + IMEI + ", GLASS: AG20260828_3, TECNICO_GLASS: Técnico H, ABRIR: R20260828_9");
    }

    @Test void entregarSinAbrirAnotaElMotivo() {
        propiaConGlass();
        when(servicio.entregar(IMEI, 7, "A20260828_1"))
                .thenReturn(ResultadoEntregaGlass.sinCrear(EstadoAbrir.SIN_PIEZA_OTRO, "Técnico J"));
        ctl.actualizarEntregaGlass("A20260828_1", req(true), tecnico);
        verify(logDao).insertar(42, "ENTREGAR_GLASS",
                "ID_REP: A20260828_1, IMEI: " + IMEI + ", GLASS: AG20260828_3, TECNICO_GLASS: Técnico H, ABRIR: SIN_PIEZA_OTRO");
    }

    @Test void reEntregarUnaYaEntregadaEs422SinServicioNiLog() {
        ReparacionResumen propia = propiaConGlass();
        when(propia.getGlassEntregadoAt()).thenReturn(java.time.LocalDateTime.of(2026, 8, 31, 10, 0));
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> ctl.actualizarEntregaGlass("A20260828_1", req(true), tecnico));
        assertEquals(422, ex.getStatusCode().value());
        assertEquals("La entrega ya está registrada", ex.getReason());
        verifyNoInteractions(servicio, logDao);
    }

    @Test void deshacerLlamaAlServicioYAnotaElAbrirBorrado() {
        ReparacionResumen propia = propiaConGlass();
        when(propia.getGlassEntregadoAt()).thenReturn(java.time.LocalDateTime.of(2026, 8, 31, 10, 0));
        when(propia.getGlassEntregadoPor()).thenReturn(7);
        when(servicio.deshacer(IMEI)).thenReturn(ResultadoEntregaGlass.deshecho("R20260828_9"));
        assertEquals(ResultadoEntregaGlass.deshecho("R20260828_9"), ctl.actualizarEntregaGlass("A20260828_1", req(false), tecnico));
        verify(servicio, never()).entregar(anyString(), anyInt(), anyString());
        verify(logDao).insertar(42, "DESHACER_ENTREGA_GLASS",
                "ID_REP: A20260828_1, IMEI: " + IMEI + ", GLASS: AG20260828_3, TECNICO_GLASS: Técnico H, ABRIR BORRADO: R20260828_9");
    }

    @Test void deshacerSinAbrirQueBorrarDejaElLogComoAntes() {
        ReparacionResumen propia = propiaConGlass();
        when(propia.getGlassEntregadoAt()).thenReturn(java.time.LocalDateTime.of(2026, 8, 31, 10, 0));
        when(propia.getGlassEntregadoPor()).thenReturn(7);
        when(servicio.deshacer(IMEI)).thenReturn(ResultadoEntregaGlass.deshecho(null));
        ctl.actualizarEntregaGlass("A20260828_1", req(false), tecnico);
        verify(logDao).insertar(42, "DESHACER_ENTREGA_GLASS",
                "ID_REP: A20260828_1, IMEI: " + IMEI + ", GLASS: AG20260828_3, TECNICO_GLASS: Técnico H");
    }

    @Test void deshacerConFirmaAjenaEs403() {
        ReparacionResumen propia = propiaConGlass();
        when(propia.getGlassEntregadoAt()).thenReturn(java.time.LocalDateTime.of(2026, 8, 31, 10, 0));
        when(propia.getGlassEntregadoPor()).thenReturn(9);   // la marcó el de glass
        assertEquals(403, statusDe(() -> ctl.actualizarEntregaGlass("A20260828_1", req(false), tecnico)));
        verifyNoInteractions(servicio);
    }

    @Test void deshacerSinEntregaEs422() {
        propiaConGlass();
        assertEquals(422, statusDe(() -> ctl.actualizarEntregaGlass("A20260828_1", req(false), tecnico)));
        verifyNoInteractions(servicio);
    }

    @Test void deshacerLlegadaSoloConFirmaPropiaYAnotaElAbrirBorrado() {
        ReparacionResumen propia = asigDe(7);
        when(propia.getEntregadoAt()).thenReturn(java.time.LocalDateTime.of(2026, 8, 31, 10, 0));
        when(propia.getEntregadoPor()).thenReturn(7);
        when(dao.getAsignacionAnyById("AG20260828_3")).thenReturn(Optional.of(propia));
        when(servicio.deshacer(IMEI)).thenReturn(ResultadoEntregaGlass.deshecho("R20260828_9"));
        assertEquals(ResultadoEntregaGlass.deshecho("R20260828_9"), ctl.deshacerLlegadaGlass("AG20260828_3", tecnico));
        verify(logDao).insertar(42, "DESHACER_ENTREGA_GLASS",
                "ID_REP: AG20260828_3, IMEI: " + IMEI + ", LLEGADA deshecha por el tecnico de glass, ABRIR BORRADO: R20260828_9");
    }

    @Test void deshacerLlegadaConFirmaAjenaEs403() {
        ReparacionResumen propia = asigDe(7);
        when(propia.getEntregadoAt()).thenReturn(java.time.LocalDateTime.of(2026, 8, 31, 10, 0));
        when(propia.getEntregadoPor()).thenReturn(3);   // la entregó el de rep
        when(dao.getAsignacionAnyById("AG20260828_3")).thenReturn(Optional.of(propia));
        assertEquals(403, statusDe(() -> ctl.deshacerLlegadaGlass("AG20260828_3", tecnico)));
        verifyNoInteractions(servicio);
    }

    @Test void deshacerLlegadaValidaTipoYExistencia() {
        assertEquals(422, statusDe(() -> ctl.deshacerLlegadaGlass("A20260828_1", tecnico)));
        when(dao.getAsignacionAnyById("AG20260828_9")).thenReturn(Optional.empty());
        assertEquals(404, statusDe(() -> ctl.deshacerLlegadaGlass("AG20260828_9", tecnico)));
    }

    @Test void llegadaRechazaIdsQueNoSonGlass() {
        assertEquals(422, statusDe(() -> ctl.marcarLlegadaGlass("A20260828_1", tecnico)));
        verifyNoInteractions(logDao, servicio);
    }

    @Test void llegadaInexistenteEs404() {
        when(dao.getAsignacionAnyById("AG20260828_3")).thenReturn(Optional.empty());
        assertEquals(404, statusDe(() -> ctl.marcarLlegadaGlass("AG20260828_3", tecnico)));
    }

    @Test void llegadaSoloDelDueno() {
        ReparacionResumen ajena = asigDe(99);
        when(dao.getAsignacionAnyById("AG20260828_3")).thenReturn(Optional.of(ajena));
        assertEquals(403, statusDe(() -> ctl.marcarLlegadaGlass("AG20260828_3", tecnico)));
        verifyNoInteractions(servicio);
    }

    @Test void llegadaYaEntregadaEs422() {
        ReparacionResumen propia = asigDe(7);
        when(propia.getEntregadoAt()).thenReturn(java.time.LocalDateTime.of(2026, 8, 31, 10, 0));
        when(dao.getAsignacionAnyById("AG20260828_3")).thenReturn(Optional.of(propia));
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> ctl.marcarLlegadaGlass("AG20260828_3", tecnico));
        assertEquals(422, ex.getStatusCode().value());
        assertEquals("La entrega ya está registrada", ex.getReason());
        verifyNoInteractions(servicio);
    }

    @Test void llegadaFirmaElPropioTecnicoYAnotaElAbrir() {
        ReparacionResumen propia = asigDe(7);
        when(dao.getAsignacionAnyById("AG20260828_3")).thenReturn(Optional.of(propia));
        ResultadoEntregaGlass creado = ResultadoEntregaGlass.creado("R20260828_9", "Técnico L");
        when(servicio.marcarLlegada(IMEI, 7)).thenReturn(creado);
        assertEquals(creado, ctl.marcarLlegadaGlass("AG20260828_3", tecnico));
        verify(logDao).insertar(42, "ENTREGAR_GLASS",
                "ID_REP: AG20260828_3, IMEI: " + IMEI + ", LLEGADA registrada por el tecnico de glass, ABRIR: R20260828_9");
    }

    @Test void llegadaSinNormalAnotaSinNormal() {
        ReparacionResumen propia = asigDe(7);
        when(dao.getAsignacionAnyById("AG20260828_3")).thenReturn(Optional.of(propia));
        when(servicio.marcarLlegada(IMEI, 7)).thenReturn(ResultadoEntregaGlass.sinCrear(EstadoAbrir.SIN_NORMAL, null));
        ctl.marcarLlegadaGlass("AG20260828_3", tecnico);
        verify(logDao).insertar(42, "ENTREGAR_GLASS",
                "ID_REP: AG20260828_3, IMEI: " + IMEI + ", LLEGADA registrada por el tecnico de glass, ABRIR: SIN_NORMAL");
    }
}
