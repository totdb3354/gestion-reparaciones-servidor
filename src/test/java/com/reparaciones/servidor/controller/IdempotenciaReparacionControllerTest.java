package com.reparaciones.servidor.controller;

import com.reparaciones.servidor.dao.BorradorDAO;
import com.reparaciones.servidor.dao.ComponenteDAO;
import com.reparaciones.servidor.dao.DificultadPuntosDAO;
import com.reparaciones.servidor.dao.LogDAO;
import com.reparaciones.servidor.dao.ReparacionComponenteDAO;
import com.reparaciones.servidor.dao.ReparacionDAO;
import com.reparaciones.servidor.dao.TecnicoDAO;
import com.reparaciones.servidor.idempotencia.RegistroIdempotencia;
import com.reparaciones.servidor.model.FilaReparacion;
import com.reparaciones.servidor.model.ValorTexto;
import com.reparaciones.servidor.security.PropiedadAsignacion;
import com.reparaciones.servidor.security.UsuarioPrincipal;
import com.reparaciones.servidor.service.CargaAsignacionesService;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Reintentos seguros con {@code Idempotency-Key} en las cuatro escrituras no repetibles del formulario
 * (spec web-formulario, tarea añadida al cierre 2026-09-19): con la misma clave y la misma petición, un
 * reintento devuelve el resultado de la primera ejecución sin volver a llamar al DAO ni a repetir el log.
 */
class IdempotenciaReparacionControllerTest {

    private static final String ASIG  = "A20260916_1";
    private static final String IMEI  = "355400000000111";
    private static final String CLAVE = "clave-de-prueba-1";

    private final ReparacionDAO dao = mock(ReparacionDAO.class);
    private final LogDAO logDao = mock(LogDAO.class);
    private final BorradorDAO borradorDao = mock(BorradorDAO.class);
    private final ReparacionController ctl = new ReparacionController(
            dao, mock(ReparacionComponenteDAO.class), logDao, borradorDao,
            mock(ComponenteDAO.class), mock(DificultadPuntosDAO.class), mock(TecnicoDAO.class), new RegistroIdempotencia(),
            mock(CargaAsignacionesService.class));

    private final UsuarioPrincipal tecnico = new UsuarioPrincipal(8, "tecnico_n", "x", "TECNICO", 4);
    private final UsuarioPrincipal supertecnico = new UsuarioPrincipal(7, "tecnico_f", "x", "SUPERTECNICO", 3);

    private final List<FilaReparacion> filas = List.of(fila(101));

    private static FilaReparacion fila(int idCom) {
        FilaReparacion f = new FilaReparacion();
        f.idCom = idCom;
        f.cantidad = 1;
        f.prefijo = "bat";
        return f;
    }

    @Test void filasConLaMismaClaveLlamaAlDaoUnaVezYDevuelveElMismoId() {
        when(dao.getIdTecDeAsignacion(ASIG)).thenReturn(4);
        when(dao.guardarFilaIndividual(filas, IMEI, 4, null, ASIG)).thenReturn("R20260916_5");

        ValorTexto primero = ctl.guardarFilaIndividual(ASIG,
                new ReparacionController.GuardarFilaRequest(filas, IMEI, 9, null), tecnico, CLAVE);
        ValorTexto segundo = ctl.guardarFilaIndividual(ASIG,
                new ReparacionController.GuardarFilaRequest(filas, IMEI, 9, null), tecnico, CLAVE);

        assertEquals("R20260916_5", primero.value());
        assertEquals(primero.value(), segundo.value());
        verify(dao, times(1)).guardarFilaIndividual(filas, IMEI, 4, null, ASIG);
        verify(logDao, times(1)).insertar(eq(8), any(), any());
    }

    @Test void completaConLaMismaClaveLlamaAlDaoUnaVezYLogueaUnaVez() {
        when(dao.getIdTecDeAsignacion(ASIG)).thenReturn(4);

        ctl.insertarCompleta(new ReparacionController.InsertarCompletaRequest(filas, IMEI, 9, null, ASIG, null),
                tecnico, CLAVE);
        ctl.insertarCompleta(new ReparacionController.InsertarCompletaRequest(filas, IMEI, 9, null, ASIG, null),
                tecnico, CLAVE);

        verify(dao, times(1)).insertarCompleta(filas, IMEI, 4, null, ASIG, null);
        verify(logDao, times(1)).insertar(eq(8), any(), any());
    }

    @Test void agotarConLaMismaClaveNoDescuentaDosVeces() {
        when(dao.getIdTecDeAsignacion(ASIG)).thenReturn(4);

        ctl.agotarComponente(ASIG, new ReparacionController.AgotarRequest(102, 1, "sin existencias"), tecnico, CLAVE);
        ctl.agotarComponente(ASIG, new ReparacionController.AgotarRequest(102, 1, "sin existencias"), tecnico, CLAVE);

        verify(dao, times(1)).agotarComponente(ASIG, 102, 1, "sin existencias");
        verify(logDao, times(1)).insertar(eq(8), eq("AGOTAR_COMPONENTE"), any());
    }

    @Test void editarReintentadoDevuelveOkSinSegundaLlamadaAlDao() {
        LocalDateTime updatedAt = LocalDateTime.of(2026, 9, 16, 10, 0);
        when(dao.getResumenById("R20260916_5")).thenReturn(Optional.empty());

        ctl.editarReparacion("R20260916_5",
                new ReparacionController.EditarRequest(0, false, "obs", 1, updatedAt), supertecnico, CLAVE);
        ctl.editarReparacion("R20260916_5",
                new ReparacionController.EditarRequest(0, false, "obs", 1, updatedAt), supertecnico, CLAVE);

        verify(dao, times(1)).editarReparacion("R20260916_5", 0, false, "obs", 1, updatedAt);
        verify(logDao, times(1)).insertar(eq(7), any(), any());
    }

    /** Compatibilidad con el cliente de escritorio: sin cabecera, cada llamada ejecuta como hasta ahora. */
    @Test void sinCabeceraCadaLlamadaEjecuta() {
        when(dao.getIdTecDeAsignacion(ASIG)).thenReturn(4);

        ctl.agotarComponente(ASIG, new ReparacionController.AgotarRequest(102, 1, null), tecnico, null);
        ctl.agotarComponente(ASIG, new ReparacionController.AgotarRequest(102, 1, null), tecnico, null);

        verify(dao, times(2)).agotarComponente(ASIG, 102, 1, null);
    }

    @Test void unaAsignacionAjenaEs403AunqueLaClaveYaTengaResultado() {
        when(dao.getIdTecDeAsignacion(ASIG)).thenReturn(4);
        ctl.agotarComponente(ASIG, new ReparacionController.AgotarRequest(102, 1, null), tecnico, CLAVE);

        when(dao.getIdTecDeAsignacion(ASIG)).thenReturn(9);   // la asignación ha pasado a ser de otro técnico
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> ctl.agotarComponente(
                ASIG, new ReparacionController.AgotarRequest(102, 1, null), tecnico, CLAVE));

        assertEquals(403, ex.getStatusCode().value());
        assertEquals(PropiedadAsignacion.MSG_NO_ES_TUYA, ex.getReason());
        verify(dao, times(1)).agotarComponente(ASIG, 102, 1, null);   // solo la primera llamada llegó al DAO
    }

    // ── la escritura ya comprometida no se repite aunque falle lo de después (el log) ──────────

    @Test void siElLogFallaTrasGuardarLaFilaElReintentoNoVuelveAEscribir() {
        when(dao.getIdTecDeAsignacion(ASIG)).thenReturn(4);
        when(dao.guardarFilaIndividual(filas, IMEI, 4, null, ASIG)).thenReturn("R20260916_5");
        RuntimeException falloLog = new RuntimeException("fallo de log");
        doThrow(falloLog).when(logDao).insertar(eq(8), any(), any());

        RuntimeException primera = assertThrows(RuntimeException.class, () -> ctl.guardarFilaIndividual(ASIG,
                new ReparacionController.GuardarFilaRequest(filas, IMEI, 9, null), tecnico, CLAVE));
        assertSame(falloLog, primera);

        ValorTexto segundo = ctl.guardarFilaIndividual(ASIG,
                new ReparacionController.GuardarFilaRequest(filas, IMEI, 9, null), tecnico, CLAVE);

        assertEquals("R20260916_5", segundo.value());
        verify(dao, times(1)).guardarFilaIndividual(filas, IMEI, 4, null, ASIG);
    }

    @Test void siElLogFallaTrasCompletarElReintentoNoVuelveADescontar() {
        when(dao.getIdTecDeAsignacion(ASIG)).thenReturn(4);
        RuntimeException falloLog = new RuntimeException("fallo de log");
        doThrow(falloLog).when(logDao).insertar(eq(8), any(), any());

        RuntimeException primera = assertThrows(RuntimeException.class, () -> ctl.insertarCompleta(
                new ReparacionController.InsertarCompletaRequest(filas, IMEI, 9, null, ASIG, null), tecnico, CLAVE));
        assertSame(falloLog, primera);

        ctl.insertarCompleta(new ReparacionController.InsertarCompletaRequest(filas, IMEI, 9, null, ASIG, null),
                tecnico, CLAVE);

        verify(dao, times(1)).insertarCompleta(filas, IMEI, 4, null, ASIG, null);
    }

    // ── la clave queda ligada a la asignación o reparación concreta del primer uso ──────────────

    @Test void laMismaClaveSobreOtraAsignacionEs422EnFilas() {
        String otraAsig = "A20260916_2";
        when(dao.getIdTecDeAsignacion(ASIG)).thenReturn(4);
        when(dao.getIdTecDeAsignacion(otraAsig)).thenReturn(4);
        when(dao.guardarFilaIndividual(filas, IMEI, 4, null, ASIG)).thenReturn("R20260916_5");

        ctl.guardarFilaIndividual(ASIG, new ReparacionController.GuardarFilaRequest(filas, IMEI, 9, null), tecnico, CLAVE);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> ctl.guardarFilaIndividual(
                otraAsig, new ReparacionController.GuardarFilaRequest(filas, IMEI, 9, null), tecnico, CLAVE));

        assertEquals(422, ex.getStatusCode().value());
        verify(dao, never()).guardarFilaIndividual(filas, IMEI, 4, null, otraAsig);
    }

    @Test void laMismaClaveSobreOtraAsignacionEs422EnAgotar() {
        String otraAsig = "A20260916_2";
        when(dao.getIdTecDeAsignacion(ASIG)).thenReturn(4);
        when(dao.getIdTecDeAsignacion(otraAsig)).thenReturn(4);

        ctl.agotarComponente(ASIG, new ReparacionController.AgotarRequest(102, 1, null), tecnico, CLAVE);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> ctl.agotarComponente(
                otraAsig, new ReparacionController.AgotarRequest(102, 1, null), tecnico, CLAVE));

        assertEquals(422, ex.getStatusCode().value());
        verify(dao, never()).agotarComponente(otraAsig, 102, 1, null);
    }

    @Test void laMismaClaveSobreOtraReparacionEs422() {
        LocalDateTime updatedAt = LocalDateTime.of(2026, 9, 16, 10, 0);
        when(dao.getResumenById(any())).thenReturn(Optional.empty());

        ctl.editarReparacion("R20260916_5",
                new ReparacionController.EditarRequest(0, false, "obs", 1, updatedAt), supertecnico, CLAVE);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> ctl.editarReparacion(
                "R20260916_6", new ReparacionController.EditarRequest(0, false, "obs", 1, updatedAt),
                supertecnico, CLAVE));

        assertEquals(422, ex.getStatusCode().value());
        verify(dao, never()).editarReparacion(eq("R20260916_6"), anyInt(), anyBoolean(), any(), anyInt(), any());
    }
}
