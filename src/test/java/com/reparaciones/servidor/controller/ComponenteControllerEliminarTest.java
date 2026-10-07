package com.reparaciones.servidor.controller;

import com.reparaciones.servidor.dao.ComponenteDAO;
import com.reparaciones.servidor.dao.LogDAO;
import com.reparaciones.servidor.security.UsuarioPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Un segundo borrado del mismo componente (getTipoById usa queryForObject y lanza
 *  EmptyResultDataAccessException) respondía 500; debe responder 404 y no borrar ni loguear. */
class ComponenteControllerEliminarTest {

    private final ComponenteDAO dao = mock(ComponenteDAO.class);
    private final LogDAO logDao = mock(LogDAO.class);
    private final ComponenteController ctl = new ComponenteController(dao, logDao, mock(com.reparaciones.servidor.service.PrevisionPedidoService.class));
    private final UsuarioPrincipal super7 = new UsuarioPrincipal(7, "super_j", "x", "SUPERTECNICO", 3);

    @Test void eliminarInexistenteEs404YNoEscribeNada() {
        when(dao.getTipoById(99)).thenThrow(new EmptyResultDataAccessException(1));
        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> ctl.eliminar(99, super7));
        assertEquals(HttpStatus.NOT_FOUND, e.getStatusCode());
        assertEquals("Recurso no encontrado: 99", e.getReason());
        verify(dao, never()).eliminar(anyInt());
        verifyNoInteractions(logDao);
    }

    @Test void eliminarExistenteBorraYRegistraComoAntes() {
        when(dao.getTipoById(5)).thenReturn("lcd-x");
        ctl.eliminar(5, super7);
        verify(dao).eliminar(5);
        verify(logDao).insertar(7, "ELIMINAR_COMPONENTE", "ID_COM: 5, TIPO: lcd-x");
    }
}
