package com.reparaciones.servidor.controller;

import com.reparaciones.servidor.dao.LogDAO;
import com.reparaciones.servidor.dao.TecnicoDAO;
import com.reparaciones.servidor.security.UsuarioPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** PATCH /api/tecnicos/{idTec}/glass (spec 2026-09-05-glass-prediccion, §3.2). El rol lo filtra @PreAuthorize. */
class TecnicoControllerGlassTest {

    private final TecnicoDAO dao = mock(TecnicoDAO.class);
    private final LogDAO logDao = mock(LogDAO.class);
    private final TecnicoController ctl = new TecnicoController(dao, logDao);
    private final UsuarioPrincipal supertecnico = new UsuarioPrincipal(2, "super", "x", "SUPERTECNICO", 9);

    @Test void habilitarActualizaYLoguea() {
        when(dao.setGlass(7, true)).thenReturn(1);
        when(dao.getNombreById(7)).thenReturn("Javi");
        ctl.setGlass(7, new TecnicoController.GlassRequest(true), supertecnico);
        verify(dao).setGlass(7, true);
        verify(logDao).insertar(2, "HABILITAR_GLASS", "ID_TEC: 7, NOMBRE: Javi");
    }

    @Test void deshabilitarActualizaYLoguea() {
        when(dao.setGlass(7, false)).thenReturn(1);
        when(dao.getNombreById(7)).thenReturn("Javi");
        ctl.setGlass(7, new TecnicoController.GlassRequest(false), supertecnico);
        verify(dao).setGlass(7, false);
        verify(logDao).insertar(2, "DESHABILITAR_GLASS", "ID_TEC: 7, NOMBRE: Javi");
    }

    @Test void tecnicoInexistenteDa404SinLog() {
        when(dao.setGlass(99, true)).thenReturn(0);
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> ctl.setGlass(99, new TecnicoController.GlassRequest(true), supertecnico));
        assertEquals(404, ex.getStatusCode().value());
        verify(dao, never()).getNombreById(anyInt());
        verifyNoInteractions(logDao);
    }
}
