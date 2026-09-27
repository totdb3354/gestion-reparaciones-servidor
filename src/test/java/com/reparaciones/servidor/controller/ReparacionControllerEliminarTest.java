package com.reparaciones.servidor.controller;

import com.reparaciones.servidor.dao.*;
import com.reparaciones.servidor.idempotencia.RegistroIdempotencia;
import com.reparaciones.servidor.model.ReparacionResumen;
import com.reparaciones.servidor.security.UsuarioPrincipal;
import com.reparaciones.servidor.service.CargaAsignacionesService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Un segundo borrado de la misma asignación/reparación (lista sin refrescar entre dos supertécnicos)
 *  respondía 500 porque el DAO consultaba una fila que ya no existía. Debe responder 404, igual que
 *  {@code deshacerLlegadaGlass}, y no escribir nada (ni DAO ni log).
 *
 *  <p>La existencia real la decide el DAO transaccional contra la tabla Reparacion sin filtros (ver
 *  {@code ReparacionDAOEliminarAsignacionTest} / {@code ReparacionDAOEliminarTest}); el {@code Optional}
 *  que lee el controlador ({@code getAsignacionAnyById} / {@code getResumenById}) usa vistas filtradas
 *  (excluyen pulidos 'P...', asignaciones ya cerradas, etc.) y solo sirve para enriquecer el log, con el
 *  mismo fallback "ID_REP: id" de siempre cuando la vista no la encuentra pero la fila sí existe. */
class ReparacionControllerEliminarTest {

    private static final String IMEI = "351111112222333";
    private final ReparacionDAO dao = mock(ReparacionDAO.class);
    private final LogDAO logDao = mock(LogDAO.class);
    private final ReparacionController ctl = new ReparacionController(
            dao, mock(ReparacionComponenteDAO.class), logDao, mock(BorradorDAO.class), mock(ComponenteDAO.class),
            mock(DificultadPuntosDAO.class), mock(TecnicoDAO.class), new RegistroIdempotencia(),
            mock(CargaAsignacionesService.class));
    private final UsuarioPrincipal super7 = new UsuarioPrincipal(7, "super_j", "x", "SUPERTECNICO", 3);

    private ReparacionResumen resumen() {
        ReparacionResumen r = mock(ReparacionResumen.class);
        when(r.getImei()).thenReturn(IMEI);
        when(r.getModelo()).thenReturn("iPhone 13");
        when(r.getNombreTecnico()).thenReturn("Técnico H");
        return r;
    }

    // ── asignaciones ─────────────────────────────────────────────────────────

    @Test void eliminarAsignacionInexistenteEs404YNoEscribeNada() {
        doThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Recurso no encontrado: A20260927_1"))
                .when(dao).eliminarAsignacion("A20260927_1");
        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> ctl.eliminarAsignacion("A20260927_1", null, super7));
        assertEquals(HttpStatus.NOT_FOUND, e.getStatusCode());
        assertEquals("Recurso no encontrado: A20260927_1", e.getReason());
        verifyNoInteractions(logDao);
    }

    @Test void eliminarAsignacionExistenteBorraYRegistraComoAntes() {
        ReparacionResumen r = resumen();
        when(dao.getAsignacionAnyById("A20260927_1")).thenReturn(Optional.of(r));
        ctl.eliminarAsignacion("A20260927_1", null, super7);
        verify(dao).eliminarAsignacion("A20260927_1");
        verify(logDao).insertar(7, "ELIMINAR_ASIGNACION",
                "ID_REP: A20260927_1, IMEI: " + IMEI + ", MODELO: iPhone 13, TECNICO: Técnico H", null);
    }

    /** Una A/AG ya cerrada existe en Reparacion pero getAsignacionAnyById (FECHA_FIN IS NULL, sin
     *  'AP%') no la devuelve: debe seguir borrándose con 204 y log corto, como en main. */
    @Test void eliminarAsignacionQueLaVistaNoDevuelveBorraIgualConLogCorto() {
        when(dao.getAsignacionAnyById("A20260927_1")).thenReturn(Optional.empty());
        ctl.eliminarAsignacion("A20260927_1", null, super7);
        verify(dao).eliminarAsignacion("A20260927_1");
        verify(logDao).insertar(7, "ELIMINAR_ASIGNACION", "ID_REP: A20260927_1", null);
    }

    // ── reparaciones ─────────────────────────────────────────────────────────

    @Test void eliminarReparacionInexistenteEs404YNoEscribeNada() {
        doThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Recurso no encontrado: R20260927_1"))
                .when(dao).eliminar("R20260927_1");
        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> ctl.eliminar("R20260927_1", null, super7));
        assertEquals(HttpStatus.NOT_FOUND, e.getStatusCode());
        assertEquals("Recurso no encontrado: R20260927_1", e.getReason());
        verifyNoInteractions(logDao);
    }

    @Test void eliminarReparacionExistenteBorraYRegistraComoAntes() {
        ReparacionResumen r = resumen();
        when(dao.getResumenById("R20260927_1")).thenReturn(Optional.of(r));
        ctl.eliminar("R20260927_1", null, super7);
        verify(dao).eliminar("R20260927_1");
        verify(logDao).insertar(7, "ELIMINAR_REPARACION",
                "ID_REP: R20260927_1, IMEI: " + IMEI + ", MODELO: iPhone 13, TECNICO: Técnico H", null);
    }

    /** Un pulido 'P...' existe en Reparacion pero getResumenById (solo 'R%'/'G%') no lo devuelve: el
     *  JavaFX (vista Agrupado) lo borra por esta misma ruta y debe seguir dando 204 con log corto. */
    @Test void eliminarPulidoPorEstaRutaQueLaVistaNoDevuelveBorraIgualConLogCorto() {
        when(dao.getResumenById("P20260927_1")).thenReturn(Optional.empty());
        ctl.eliminar("P20260927_1", null, super7);
        verify(dao).eliminar("P20260927_1");
        verify(logDao).insertar(7, "ELIMINAR_REPARACION", "ID_REP: P20260927_1", null);
    }
}
