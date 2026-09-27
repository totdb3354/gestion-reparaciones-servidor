package com.reparaciones.servidor.controller;

import com.reparaciones.servidor.dao.ClienteDAO;
import com.reparaciones.servidor.dao.LogDAO;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ClienteControllerTest {

    private final ClienteDAO dao = mock(ClienteDAO.class);
    private final LogDAO logDao = mock(LogDAO.class);
    private final ClienteController ctl = new ClienteController(dao, logDao, new com.reparaciones.servidor.idempotencia.RegistroIdempotencia());

    @Test void tieneTelefonosDevuelveValorBooleanoTipado() {
        when(dao.tieneTelefonos(5)).thenReturn(true);
        assertTrue(ctl.tieneTelefonos(5).value());
        when(dao.tieneTelefonos(6)).thenReturn(false);
        assertFalse(ctl.tieneTelefonos(6).value());
    }
}
