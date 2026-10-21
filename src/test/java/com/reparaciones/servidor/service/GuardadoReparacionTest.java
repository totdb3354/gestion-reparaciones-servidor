package com.reparaciones.servidor.service;

import com.reparaciones.servidor.dao.ReparacionDAO;
import com.reparaciones.servidor.model.FilaReparacion;
import com.reparaciones.servidor.nsys.AvisoTaller;
import com.reparaciones.servidor.nsys.TallerDAO;
import com.reparaciones.servidor.nsys.TallerReparaciones;
import com.reparaciones.servidor.nsys.TallerReparaciones.PiezaGuardada;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Las escrituras del formulario con el SKU del taller (0.9.21, pieza 2): la reparación y su efecto en el móvil. */
class GuardadoReparacionTest {

    private static final String IMEI = "355400000000111";
    private static final String ASIG = "A20260916_1";
    private static final LocalDateTime UPDATED = LocalDateTime.of(2026, 9, 16, 7, 2);
    private final ReparacionDAO dao = mock(ReparacionDAO.class);
    private final TallerReparaciones taller = mock(TallerReparaciones.class);
    private final GuardadoReparacion guardado = new GuardadoReparacion(dao, taller);

    private static FilaReparacion fila(int idCom, boolean reutilizado, boolean solicitud) {
        FilaReparacion f = new FilaReparacion();
        f.idCom = idCom;
        f.cantidad = reutilizado ? 0 : 1;
        f.reutilizado = reutilizado;
        f.esSolicitud = solicitud;
        return f;
    }

    @Test void terminarAplicaCadaReparacionCreadaConSuFila() {
        List<FilaReparacion> filas = List.of(fila(101, false, false), fila(102, false, true), fila(131, true, false));
        when(dao.insertarCompleta(filas, IMEI, 4, null, ASIG, null)).thenReturn(List.of("R20260916_5", "R20260916_6"));
        guardado.insertarCompleta(filas, IMEI, 4, null, ASIG, null, 8);
        verify(taller).aplicar(IMEI, List.of(new PiezaGuardada("R20260916_5", 101, false),
                new PiezaGuardada("R20260916_6", 131, true)), 8);
    }

    @Test void guardarFilaAplicaLaReparacionQueDevuelveElDao() {
        List<FilaReparacion> filas = List.of(fila(131, false, false));
        when(dao.guardarFilaIndividual(filas, IMEI, 4, null, ASIG)).thenReturn("R20260916_5");
        assertEquals("R20260916_5", guardado.guardarFilaIndividual(filas, IMEI, 4, null, ASIG, 8));
        verify(taller).aplicar(IMEI, List.of(new PiezaGuardada("R20260916_5", 131, false)), 8);
    }

    @Test void siLaReparacionFallaNoSeTocaElTaller() {
        when(dao.insertarCompleta(any(), any(), anyInt(), any(), any(), any()))
                .thenThrow(new ResponseStatusException(HttpStatus.CONFLICT, "Stock insuficiente"));
        assertThrows(ResponseStatusException.class, () -> guardado.insertarCompleta(List.of(fila(101, false, false)), IMEI, 4,
                null, ASIG, null, 8));
        verify(taller).bloquear(IMEI);
        verifyNoMoreInteractions(taller);
    }

    // ---------------------------------------------------------------- el candado del móvil va lo primero (REPEATABLE READ)

    @Test void terminarCogeElCandadoDelMovilAntesQueNada() {
        List<FilaReparacion> filas = List.of(fila(131, false, false));
        when(dao.insertarCompleta(filas, IMEI, 4, null, ASIG, null)).thenReturn(List.of("R20260916_5"));
        guardado.insertarCompleta(filas, IMEI, 4, null, ASIG, null, 8);
        InOrder orden = inOrder(taller, dao);
        orden.verify(taller).bloquear(IMEI);
        orden.verify(dao).insertarCompleta(filas, IMEI, 4, null, ASIG, null);
        orden.verify(taller).aplicar(IMEI, List.of(new PiezaGuardada("R20260916_5", 131, false)), 8);
        verifyNoMoreInteractions(taller, dao);
    }

    @Test void guardarFilaCogeElCandadoDelMovilAntesQueNada() {
        List<FilaReparacion> filas = List.of(fila(131, false, false));
        when(dao.guardarFilaIndividual(filas, IMEI, 4, null, ASIG)).thenReturn("R20260916_5");
        guardado.guardarFilaIndividual(filas, IMEI, 4, null, ASIG, 8);
        InOrder orden = inOrder(taller, dao);
        orden.verify(taller).bloquear(IMEI);
        orden.verify(dao).guardarFilaIndividual(filas, IMEI, 4, null, ASIG);
        orden.verify(taller).aplicar(IMEI, List.of(new PiezaGuardada("R20260916_5", 131, false)), 8);
        verifyNoMoreInteractions(taller, dao);
    }

    @Test void editarBloqueaLaReparacionYDespuesElMovilAntesQueNada() {
        TallerDAO.PiezaDeReparacion antes = new TallerDAO.PiezaDeReparacion("R20260916_5", IMEI, 131, false);
        when(dao.bloquearReparacion("R20260916_5")).thenReturn(Optional.of(IMEI));
        when(taller.pieza("R20260916_5")).thenReturn(Optional.of(antes));
        guardado.editarReparacion("R20260916_5", 133, true, "obs", 0, UPDATED, 7);
        InOrder orden = inOrder(taller, dao);
        orden.verify(dao).bloquearReparacion("R20260916_5");
        orden.verify(taller).bloquear(IMEI);
        orden.verify(taller).pieza("R20260916_5");
        orden.verify(dao).editarReparacion("R20260916_5", 133, true, "obs", 0, UPDATED);
        orden.verify(taller).editar(antes, new PiezaGuardada("R20260916_5", 133, true), 7);
        verifyNoMoreInteractions(taller, dao);
    }

    @Test void borrarBloqueaLaReparacionYDespuesElMovilAntesQueNada() {
        when(dao.bloquearReparacion("R20260916_5")).thenReturn(Optional.of(IMEI));
        guardado.eliminar("R20260916_5", 7);
        InOrder orden = inOrder(taller, dao);
        orden.verify(dao).bloquearReparacion("R20260916_5");
        orden.verify(taller).bloquear(IMEI);
        orden.verify(taller).deshacer("R20260916_5", AvisoTaller.Causa.BORRADA, 7);
        orden.verify(dao).eliminar("R20260916_5");
        verifyNoMoreInteractions(taller, dao);
    }

    @Test void conUnImeiFueraDeNsysSeGuardaIgual() {
        when(taller.bloquear(IMEI)).thenReturn(false);
        List<FilaReparacion> filas = List.of(fila(131, false, false));
        when(dao.insertarCompleta(filas, IMEI, 4, null, ASIG, null)).thenReturn(List.of("R20260916_5"));
        when(dao.guardarFilaIndividual(filas, IMEI, 4, null, ASIG)).thenReturn("R20260916_6");
        when(dao.bloquearReparacion(any())).thenReturn(Optional.of(IMEI));
        TallerDAO.PiezaDeReparacion antes = new TallerDAO.PiezaDeReparacion("R20260916_5", IMEI, 131, false);
        when(taller.pieza("R20260916_5")).thenReturn(Optional.of(antes));

        guardado.insertarCompleta(filas, IMEI, 4, null, ASIG, null, 8);
        assertEquals("R20260916_6", guardado.guardarFilaIndividual(filas, IMEI, 4, null, ASIG, 8));
        guardado.editarReparacion("R20260916_5", 133, true, "obs", 0, UPDATED, 7);
        guardado.eliminar("R20260916_6", 7);

        verify(taller).aplicar(IMEI, List.of(new PiezaGuardada("R20260916_5", 131, false)), 8);
        verify(taller).aplicar(IMEI, List.of(new PiezaGuardada("R20260916_6", 131, false)), 8);
        verify(dao).editarReparacion("R20260916_5", 133, true, "obs", 0, UPDATED);
        verify(taller).editar(antes, new PiezaGuardada("R20260916_5", 133, true), 7);
        verify(taller).deshacer("R20260916_6", AvisoTaller.Causa.BORRADA, 7);
        verify(dao).eliminar("R20260916_6");
    }

    @Test void unaReparacionQueNoExisteDaElErrorDeSiempre() {
        when(dao.bloquearReparacion("R20260916_9")).thenReturn(Optional.empty());
        doThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Recurso no encontrado: R20260916_9"))
                .when(dao).eliminar("R20260916_9");
        ResponseStatusException e = assertThrows(ResponseStatusException.class, () -> guardado.eliminar("R20260916_9", 7));
        assertEquals(HttpStatus.NOT_FOUND, e.getStatusCode());
        verify(taller, never()).bloquear(any());
        verify(dao).eliminar("R20260916_9");
    }

    // ---------------------------------------------------------------- emparejar filas y reparaciones

    @Test void sinTantasReparacionesComoFilasConUsoNoSeEmpareja() {
        List<FilaReparacion> filas = List.of(fila(101, false, false), fila(102, false, true), fila(131, true, false));
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> GuardadoReparacion.guardado(filas, List.of("R20260916_5")));
        assertEquals("No cuadran las filas con uso (2) y las reparaciones creadas (1)", e.getMessage());
        assertThrows(IllegalStateException.class, () -> GuardadoReparacion.guardado(List.of(), List.of("R20260916_5")));
        assertEquals(List.of(), GuardadoReparacion.guardado(List.of(fila(102, false, true)), List.of()));
    }

    @Test void editarLeeLaPiezaDeAntesEditaYDespuesAjustaElTaller() {
        TallerDAO.PiezaDeReparacion antes = new TallerDAO.PiezaDeReparacion("R20260916_5", IMEI, 131, false);
        when(taller.pieza("R20260916_5")).thenReturn(Optional.of(antes));
        guardado.editarReparacion("R20260916_5", 133, true, "obs", 0, UPDATED, 7);
        InOrder orden = inOrder(taller, dao);
        orden.verify(taller).pieza("R20260916_5");
        orden.verify(dao).editarReparacion("R20260916_5", 133, true, "obs", 0, UPDATED);
        orden.verify(taller).editar(antes, new PiezaGuardada("R20260916_5", 133, true), 7);
    }

    @Test void editarUnaReparacionSinPiezaQueContarSoloEdita() {
        when(taller.pieza("G20260916_5")).thenReturn(Optional.empty());
        guardado.editarReparacion("G20260916_5", 141, false, null, 1, UPDATED, 7);
        verify(dao).editarReparacion("G20260916_5", 141, false, null, 1, UPDATED);
        verify(taller, never()).editar(any(), any(), anyInt());
    }

    @Test void borrarDeshaceAntesDeBorrar() {
        guardado.eliminar("R20260916_5", 7);
        InOrder orden = inOrder(taller, dao);
        orden.verify(taller).deshacer("R20260916_5", AvisoTaller.Causa.BORRADA, 7);
        orden.verify(dao).eliminar("R20260916_5");
    }

    @Test void lasCuatroEscriturasSonUnaSolaTransaccion() throws NoSuchMethodException {
        assertNotNull(GuardadoReparacion.class.getMethod("insertarCompleta", List.class, String.class, int.class, String.class,
                String.class, String.class, int.class).getAnnotation(Transactional.class));
        assertNotNull(GuardadoReparacion.class.getMethod("guardarFilaIndividual", List.class, String.class, int.class,
                String.class, String.class, int.class).getAnnotation(Transactional.class));
        assertNotNull(GuardadoReparacion.class.getMethod("editarReparacion", String.class, int.class, boolean.class,
                String.class, int.class, LocalDateTime.class, int.class).getAnnotation(Transactional.class));
        assertNotNull(GuardadoReparacion.class.getMethod("eliminar", String.class, int.class).getAnnotation(Transactional.class));
    }
}
