package com.reparaciones.servidor.controller;

import com.reparaciones.servidor.dao.*;
import com.reparaciones.servidor.idempotencia.RegistroIdempotencia;
import com.reparaciones.servidor.model.FilaReparacion;
import com.reparaciones.servidor.security.PropiedadAsignacion;
import com.reparaciones.servidor.security.UsuarioPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

/** La regla de PropiedadAsignacion aplicada en cada escritura del formulario (spec web-formulario §5.1). */
class PropiedadAsignacionControllersTest {

    private static final String ASIG = "A20260916_1";
    private static final String IMEI = "355400000000111";

    private final ReparacionDAO dao = mock(ReparacionDAO.class);
    private final LogDAO logDao = mock(LogDAO.class);
    private final BorradorDAO borradorDao = mock(BorradorDAO.class);
    private final ReparacionController ctl = new ReparacionController(
            dao, mock(ReparacionComponenteDAO.class), logDao, borradorDao,
            mock(ComponenteDAO.class), mock(DificultadPuntosDAO.class), new RegistroIdempotencia());

    private final UsuarioPrincipal tecnico = new UsuarioPrincipal(8, "tecnico_n", "x", "TECNICO", 4);
    private final UsuarioPrincipal supertecnico = new UsuarioPrincipal(7, "tecnico_f", "x", "SUPERTECNICO", 3);
    private final UsuarioPrincipal admin = new UsuarioPrincipal(1, "admin", "x", "ADMIN", null);

    private final List<FilaReparacion> filas = List.of(fila(101));

    private static FilaReparacion fila(int idCom) {
        FilaReparacion f = new FilaReparacion();
        f.idCom = idCom;
        f.cantidad = 1;
        f.prefijo = "bat";
        return f;
    }

    private static ResponseStatusException rechazo(Runnable r) {
        return assertThrows(ResponseStatusException.class, r::run);
    }

    @Test void completaConAsignacionUsaElTecnicoDelTokenEIgnoraElDelCuerpo() {
        when(dao.getIdTecDeAsignacion(ASIG)).thenReturn(4);
        ctl.insertarCompleta(new ReparacionController.InsertarCompletaRequest(filas, IMEI, 9, null, ASIG, null), tecnico, null);
        verify(dao).insertarCompleta(filas, IMEI, 4, null, ASIG, null);
        verify(dao).getNombreTecnicoById(4);          // el log nombra al técnico efectivo
        verify(dao, never()).getNombreTecnicoById(9);
    }

    @Test void completaConAsignacionAjenaEs403SinEscribir() {
        when(dao.getIdTecDeAsignacion(ASIG)).thenReturn(9);
        for (UsuarioPrincipal quien : List.of(tecnico, supertecnico, admin)) {
            ResponseStatusException ex = rechazo(() -> ctl.insertarCompleta(
                    new ReparacionController.InsertarCompletaRequest(filas, IMEI, 9, null, ASIG, null), quien, null));
            assertEquals(403, ex.getStatusCode().value());
            assertEquals(PropiedadAsignacion.MSG_NO_ES_TUYA, ex.getReason());
        }
        verify(dao, never()).insertarCompleta(any(), any(), anyInt(), any(), any(), any());
        verifyNoInteractions(logDao);
    }

    @Test void completaSinAsignacionExigeSupertecnicoYConservaElIdTecDelCuerpo() {
        ctl.insertarCompleta(new ReparacionController.InsertarCompletaRequest(filas, IMEI, 4, null, null, "R"), supertecnico, null);
        verify(dao).insertarCompleta(filas, IMEI, 4, null, null, "R");   // técnico original, no el 3 del token
        verify(dao).getNombreTecnicoById(4);
        verify(dao, never()).getIdTecDeAsignacion(any());
    }

    @Test void completaSinAsignacionSiendoTecnicoEs403() {
        for (UsuarioPrincipal quien : List.of(tecnico, admin)) {
            ResponseStatusException ex = rechazo(() -> ctl.insertarCompleta(
                    new ReparacionController.InsertarCompletaRequest(filas, IMEI, 4, null, null, "R"), quien, null));
            assertEquals(403, ex.getStatusCode().value());
            assertEquals(PropiedadAsignacion.MSG_SOLO_SUPERTECNICO, ex.getReason());
        }
        verify(dao, never()).insertarCompleta(any(), any(), anyInt(), any(), any(), any());
        verifyNoInteractions(logDao);
    }

    @Test void filasUsaElTecnicoDelToken() {
        when(dao.getIdTecDeAsignacion(ASIG)).thenReturn(4);
        when(dao.guardarFilaIndividual(filas, IMEI, 4, null, ASIG)).thenReturn("R20260916_5");
        ctl.guardarFilaIndividual(ASIG, new ReparacionController.GuardarFilaRequest(filas, IMEI, 9, null), tecnico, null);
        verify(dao).guardarFilaIndividual(filas, IMEI, 4, null, ASIG);
        verify(dao).getNombreTecnicoById(4);
        verify(dao, never()).getNombreTecnicoById(9);
    }

    @Test void filasAjenaEs403() {
        when(dao.getIdTecDeAsignacion(ASIG)).thenReturn(9);
        ResponseStatusException ex = rechazo(() -> ctl.guardarFilaIndividual(ASIG,
                new ReparacionController.GuardarFilaRequest(filas, IMEI, 4, null), tecnico, null));
        assertEquals(403, ex.getStatusCode().value());
        assertEquals(PropiedadAsignacion.MSG_NO_ES_TUYA, ex.getReason());
        verify(dao, never()).guardarFilaIndividual(any(), any(), anyInt(), any(), any());
        verifyNoInteractions(logDao);
    }

    @Test void agotarAjenaEs403SinTocarStock() {
        when(dao.getIdTecDeAsignacion(ASIG)).thenReturn(9);
        for (UsuarioPrincipal quien : List.of(tecnico, supertecnico)) {
            ResponseStatusException ex = rechazo(() -> ctl.agotarComponente(ASIG,
                    new ReparacionController.AgotarRequest(102, 1, null), quien, null));
            assertEquals(403, ex.getStatusCode().value());
        }
        verify(dao, never()).agotarComponente(any(), anyInt(), anyInt(), any());
        verifyNoInteractions(logDao);
    }

    @Test void agotarPropiaLlamaAlDao() {
        when(dao.getIdTecDeAsignacion(ASIG)).thenReturn(4);
        ctl.agotarComponente(ASIG, new ReparacionController.AgotarRequest(102, 1, "sin existencias"), tecnico, null);
        verify(dao).agotarComponente(ASIG, 102, 1, "sin existencias");
    }

    @Test void borradorLeerGuardarYBorrarSoloSobreAsignacionPropia() {
        when(dao.getIdTecDeAsignacion(ASIG)).thenReturn(9);
        assertEquals(403, rechazo(() -> ctl.getBorrador(ASIG, tecnico)).getStatusCode().value());
        assertEquals(403, rechazo(() -> ctl.guardarBorrador(ASIG,
                new ReparacionController.BorradorRequest("{}"), tecnico)).getStatusCode().value());
        assertEquals(403, rechazo(() -> ctl.eliminarBorrador(ASIG, tecnico)).getStatusCode().value());
        verifyNoInteractions(borradorDao);

        when(dao.getIdTecDeAsignacion(ASIG)).thenReturn(4);
        ctl.getBorrador(ASIG, tecnico);
        ctl.guardarBorrador(ASIG, new ReparacionController.BorradorRequest("{\"modelo\":\"13\"}"), tecnico);
        ctl.eliminarBorrador(ASIG, tecnico);
        verify(borradorDao).get(ASIG);
        verify(borradorDao).guardar(ASIG, "{\"modelo\":\"13\"}");
        verify(borradorDao).eliminar(ASIG);
    }

    @Test void completarAjenaEs403() {
        when(dao.getIdTecDeAsignacion(ASIG)).thenReturn(9);
        ResponseStatusException ex = rechazo(() -> ctl.completar(ASIG, tecnico));
        assertEquals(403, ex.getStatusCode().value());
        assertEquals(PropiedadAsignacion.MSG_NO_ES_TUYA, ex.getReason());
        verify(dao, never()).completar(any());
        verifyNoInteractions(logDao);
    }

    @Test void completarPropiaLlamaAlDao() {
        when(dao.getIdTecDeAsignacion(ASIG)).thenReturn(4);
        ctl.completar(ASIG, tecnico);
        verify(dao).completar(ASIG);

        // Asignación inexistente: la regla no lanza y el DAO responde su 409 como hasta ahora.
        when(dao.getIdTecDeAsignacion("A20260916_99")).thenReturn(null);
        ctl.completar("A20260916_99", tecnico);
        verify(dao).completar("A20260916_99");
    }
}
