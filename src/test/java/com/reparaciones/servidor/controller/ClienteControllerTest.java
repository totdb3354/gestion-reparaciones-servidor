package com.reparaciones.servidor.controller;

import com.reparaciones.servidor.dao.ClienteDAO;
import com.reparaciones.servidor.dao.LogDAO;
import com.reparaciones.servidor.security.UsuarioPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ClienteControllerTest {

    private final ClienteDAO dao = mock(ClienteDAO.class);
    private final LogDAO logDao = mock(LogDAO.class);
    private final ClienteController ctl = new ClienteController(dao, logDao, new com.reparaciones.servidor.idempotencia.RegistroIdempotencia());
    private final UsuarioPrincipal super7 = new UsuarioPrincipal(7, "super_j", "x", "SUPERTECNICO", 3);

    @Test void tieneTelefonosDevuelveValorBooleanoTipado() {
        when(dao.tieneTelefonos(5)).thenReturn(true);
        assertTrue(ctl.tieneTelefonos(5).value());
        when(dao.tieneTelefonos(6)).thenReturn(false);
        assertFalse(ctl.tieneTelefonos(6).value());
    }

    /** Un segundo borrado del mismo cliente (getNombreById usa queryForObject y lanza
     *  EmptyResultDataAccessException) respondía 500; debe responder 404 y no borrar ni loguear. */
    @Test void borrarInexistenteEs404YNoEscribeNada() {
        when(dao.tieneTelefonos(99)).thenReturn(false);
        when(dao.getNombreById(99)).thenThrow(new EmptyResultDataAccessException(1));
        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> ctl.borrar(99, super7));
        assertEquals(HttpStatus.NOT_FOUND, e.getStatusCode());
        assertEquals("Recurso no encontrado: 99", e.getReason());
        verify(dao, never()).borrar(anyInt());
        verifyNoInteractions(logDao);
    }

    @Test void borrarExistenteBorraYRegistraComoAntes() {
        when(dao.tieneTelefonos(5)).thenReturn(false);
        when(dao.getNombreById(5)).thenReturn("Juan Pérez");
        ctl.borrar(5, super7);
        verify(dao).borrar(5);
        verify(logDao).insertar(7, "BORRAR_CLIENTE", "ID_CLI: 5, NOMBRE: Juan Pérez");
    }
}
