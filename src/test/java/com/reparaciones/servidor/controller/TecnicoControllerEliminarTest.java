package com.reparaciones.servidor.controller;

import com.reparaciones.servidor.dao.LogDAO;
import com.reparaciones.servidor.dao.TecnicoDAO;
import com.reparaciones.servidor.security.UsuarioPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Un segundo borrado del mismo técnico (getNombreById usa queryForObject y lanza
 *  EmptyResultDataAccessException) respondía 500; debe responder 404 y no borrar ni loguear,
 *  igual que ya hace {@code setGlass} para el mismo id inexistente. */
class TecnicoControllerEliminarTest {

    private final TecnicoDAO dao = mock(TecnicoDAO.class);
    private final LogDAO logDao = mock(LogDAO.class);
    private final TecnicoController ctl = new TecnicoController(dao, logDao);
    private final UsuarioPrincipal admin = new UsuarioPrincipal(1, "admin", "x", "ADMIN", null);

    @Test void eliminarInexistenteEs404YNoEscribeNada() {
        when(dao.getNombreById(99)).thenThrow(new EmptyResultDataAccessException(1));
        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> ctl.eliminar(99, admin));
        assertEquals(HttpStatus.NOT_FOUND, e.getStatusCode());
        assertEquals("Técnico no encontrado: 99", e.getReason());
        verify(dao, never()).eliminar(anyInt());
        verifyNoInteractions(logDao);
    }

    @Test void eliminarExistenteBorraYRegistraComoAntes() {
        when(dao.getNombreById(5)).thenReturn("Técnico H");
        ctl.eliminar(5, admin);
        verify(dao).eliminar(5);
        verify(logDao).insertar(1, "ELIMINAR_TECNICO", "ID_TEC: 5, NOMBRE: Técnico H");
    }
}
