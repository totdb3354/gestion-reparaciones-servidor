package com.reparaciones.servidor.controller;

import com.reparaciones.servidor.dao.*;
import com.reparaciones.servidor.model.ReparacionResumen;
import com.reparaciones.servidor.security.UsuarioPrincipal;
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
    private final ReparacionController ctl = new ReparacionController(
            dao, mock(ReparacionComponenteDAO.class), logDao, mock(BorradorDAO.class), mock(ComponenteDAO.class));
    private final UsuarioPrincipal manu = new UsuarioPrincipal(42, "manu", "x", "TECNICO", 7);

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

    @Test void rechazaIdsQueNoSonReparacionNormal() {
        assertEquals(422, statusDe(() -> ctl.actualizarEntregaGlass("AG20260828_1", req(true), manu)));
        assertEquals(422, statusDe(() -> ctl.actualizarEntregaGlass("AP20260828_1", req(true), manu)));
        verifyNoInteractions(logDao);
    }

    @Test void asignacionInexistenteOCerradaEs404() {
        when(dao.getAsignacionAnyById("A20260828_1")).thenReturn(Optional.empty());
        assertEquals(404, statusDe(() -> ctl.actualizarEntregaGlass("A20260828_1", req(true), manu)));
    }

    @Test void soloElDuenoPuedeEntregar() {
        ReparacionResumen asig = asigDe(99);
        when(dao.getAsignacionAnyById("A20260828_1")).thenReturn(Optional.of(asig));
        assertEquals(403, statusDe(() -> ctl.actualizarEntregaGlass("A20260828_1", req(true), manu)));
        UsuarioPrincipal admin = new UsuarioPrincipal(1, "admin", "x", "ADMIN", null);
        assertEquals(403, statusDe(() -> ctl.actualizarEntregaGlass("A20260828_1", req(true), admin)));
        verify(dao, never()).entregarGlass(anyString(), anyInt());
    }

    @Test void sinGlassAbiertaEs422YNoEscribe() {
        ReparacionResumen asig = asigDe(7);
        when(dao.getAsignacionAnyById("A20260828_1")).thenReturn(Optional.of(asig));
        when(dao.getGlassAbiertas(IMEI)).thenReturn(List.of());
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> ctl.actualizarEntregaGlass("A20260828_1", req(true), manu));
        assertEquals(422, ex.getStatusCode().value());
        assertEquals("Sin glass abierta para este IMEI", ex.getReason());
        verify(dao, never()).entregarGlass(anyString(), anyInt());
        verifyNoInteractions(logDao);
    }

    @Test void entregarSellaYRegistraEnElLog() {
        ReparacionResumen asig = asigDe(7);
        when(dao.getAsignacionAnyById("A20260828_1")).thenReturn(Optional.of(asig));
        when(dao.getGlassAbiertas(IMEI)).thenReturn(List.of(new ReparacionDAO.GlassAbierta("AG20260828_3", "Jhona")));
        ctl.actualizarEntregaGlass("A20260828_1", req(true), manu);
        verify(dao).entregarGlass(IMEI, 7);
        verify(dao, never()).deshacerEntregaGlass(anyString());
        verify(logDao).insertar(42, "ENTREGAR_GLASS",
                "ID_REP: A20260828_1, IMEI: " + IMEI + ", GLASS: AG20260828_3, TECNICO_GLASS: Jhona");
    }

    @Test void deshacerLimpiaYRegistraEnElLog() {
        ReparacionResumen propia = asigDe(7);
        when(propia.getGlassEntregadoAt()).thenReturn(java.time.LocalDateTime.of(2026, 8, 31, 10, 0));
        when(propia.getGlassEntregadoPor()).thenReturn(7);
        when(dao.getAsignacionAnyById("A20260828_1")).thenReturn(Optional.of(propia));
        when(dao.getGlassAbiertas(IMEI)).thenReturn(List.of(new ReparacionDAO.GlassAbierta("AG20260828_3", "Jhona")));
        ctl.actualizarEntregaGlass("A20260828_1", req(false), manu);
        verify(dao).deshacerEntregaGlass(IMEI);
        verify(dao, never()).entregarGlass(anyString(), anyInt());
        verify(logDao).insertar(42, "DESHACER_ENTREGA_GLASS",
                "ID_REP: A20260828_1, IMEI: " + IMEI + ", GLASS: AG20260828_3, TECNICO_GLASS: Jhona");
    }

    @Test void deshacerConFirmaAjenaEs403() {
        ReparacionResumen propia = asigDe(7);
        when(propia.getGlassEntregadoAt()).thenReturn(java.time.LocalDateTime.of(2026, 8, 31, 10, 0));
        when(propia.getGlassEntregadoPor()).thenReturn(9);   // la marco el de glass
        when(dao.getAsignacionAnyById("A20260828_1")).thenReturn(Optional.of(propia));
        when(dao.getGlassAbiertas(IMEI)).thenReturn(List.of(new ReparacionDAO.GlassAbierta("AG20260828_3", "Jhona")));
        assertEquals(403, statusDe(() -> ctl.actualizarEntregaGlass("A20260828_1", req(false), manu)));
        verify(dao, never()).deshacerEntregaGlass(anyString());
    }

    @Test void deshacerSinEntregaEs422() {
        ReparacionResumen propia = asigDe(7);
        when(dao.getAsignacionAnyById("A20260828_1")).thenReturn(Optional.of(propia));
        when(dao.getGlassAbiertas(IMEI)).thenReturn(List.of(new ReparacionDAO.GlassAbierta("AG20260828_3", "Jhona")));
        assertEquals(422, statusDe(() -> ctl.actualizarEntregaGlass("A20260828_1", req(false), manu)));
        verify(dao, never()).deshacerEntregaGlass(anyString());
    }

    @Test void deshacerLlegadaSoloConFirmaPropia() {
        ReparacionResumen propia = asigDe(7);
        when(propia.getEntregadoAt()).thenReturn(java.time.LocalDateTime.of(2026, 8, 31, 10, 0));
        when(propia.getEntregadoPor()).thenReturn(7);
        when(dao.getAsignacionAnyById("AG20260828_3")).thenReturn(Optional.of(propia));
        ctl.deshacerLlegadaGlass("AG20260828_3", manu);
        verify(dao).deshacerEntregaGlass(IMEI);
        verify(logDao).insertar(42, "DESHACER_ENTREGA_GLASS",
                "ID_REP: AG20260828_3, IMEI: " + IMEI + ", LLEGADA deshecha por el tecnico de glass");
    }

    @Test void deshacerLlegadaConFirmaAjenaEs403() {
        ReparacionResumen propia = asigDe(7);
        when(propia.getEntregadoAt()).thenReturn(java.time.LocalDateTime.of(2026, 8, 31, 10, 0));
        when(propia.getEntregadoPor()).thenReturn(3);   // la entrego el de rep
        when(dao.getAsignacionAnyById("AG20260828_3")).thenReturn(Optional.of(propia));
        assertEquals(403, statusDe(() -> ctl.deshacerLlegadaGlass("AG20260828_3", manu)));
        verify(dao, never()).deshacerEntregaGlass(anyString());
    }

    @Test void deshacerLlegadaValidaTipoYExistencia() {
        assertEquals(422, statusDe(() -> ctl.deshacerLlegadaGlass("A20260828_1", manu)));
        when(dao.getAsignacionAnyById("AG20260828_9")).thenReturn(Optional.empty());
        assertEquals(404, statusDe(() -> ctl.deshacerLlegadaGlass("AG20260828_9", manu)));
    }

    private static ReparacionController.EntregaGlassRequest req(boolean entregado) {
        return new ReparacionController.EntregaGlassRequest(entregado);
    }

    @Test void llegadaRechazaIdsQueNoSonGlass() {
        assertEquals(422, statusDe(() -> ctl.marcarLlegadaGlass("A20260828_1", manu)));
        verifyNoInteractions(logDao);
    }

    @Test void llegadaInexistenteEs404() {
        when(dao.getAsignacionAnyById("AG20260828_3")).thenReturn(Optional.empty());
        assertEquals(404, statusDe(() -> ctl.marcarLlegadaGlass("AG20260828_3", manu)));
    }

    @Test void llegadaSoloDelDueno() {
        ReparacionResumen ajena = asigDe(99);
        when(dao.getAsignacionAnyById("AG20260828_3")).thenReturn(Optional.of(ajena));
        assertEquals(403, statusDe(() -> ctl.marcarLlegadaGlass("AG20260828_3", manu)));
        verify(dao, never()).entregarGlass(anyString(), anyInt());
    }

    @Test void llegadaYaEntregadaEs422() {
        ReparacionResumen propia = asigDe(7);
        when(propia.getEntregadoAt()).thenReturn(java.time.LocalDateTime.of(2026, 8, 31, 10, 0));
        when(dao.getAsignacionAnyById("AG20260828_3")).thenReturn(Optional.of(propia));
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> ctl.marcarLlegadaGlass("AG20260828_3", manu));
        assertEquals(422, ex.getStatusCode().value());
        assertEquals("La entrega ya está registrada", ex.getReason());
        verify(dao, never()).entregarGlass(anyString(), anyInt());
    }

    @Test void llegadaSellaYFirmaElPropioTecnico() {
        ReparacionResumen propia = asigDe(7);
        when(dao.getAsignacionAnyById("AG20260828_3")).thenReturn(Optional.of(propia));
        ctl.marcarLlegadaGlass("AG20260828_3", manu);
        verify(dao).entregarGlass(IMEI, 7);
        verify(logDao).insertar(42, "ENTREGAR_GLASS",
                "ID_REP: AG20260828_3, IMEI: " + IMEI + ", LLEGADA registrada por el tecnico de glass");
    }
}
