package com.reparaciones.servidor.controller;

import com.reparaciones.servidor.dao.LogDAO;
import com.reparaciones.servidor.dao.ParametroDAO;
import com.reparaciones.servidor.model.PesosPrevision;
import com.reparaciones.servidor.security.UsuarioPrincipal;
import com.reparaciones.servidor.util.PrevisionPedido.Pesos;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class ParametroControllerTest {

    private final ParametroDAO dao = mock(ParametroDAO.class);
    private final LogDAO logDao = mock(LogDAO.class);
    private final ParametroController ctl = new ParametroController(dao, logDao);
    private final UsuarioPrincipal admin = new UsuarioPrincipal(1, "admin", "", "ADMIN", null);

    @Test void devuelveLosPesosGuardados() {
        when(dao.getPesosPrevision()).thenReturn(new Pesos(40, 35, 25));
        assertEquals(new PesosPrevision(40, 35, 25), ctl.getPrevision());
    }

    @Test void guardaYAnotaElCambio() {
        when(dao.getPesosPrevision()).thenReturn(Pesos.POR_DEFECTO);
        ctl.guardarPrevision(new PesosPrevision(40, 35, 25), admin);
        verify(dao).guardarPesosPrevision(new Pesos(40, 35, 25));
        verify(logDao).insertar(1, "EDITAR_PARAMETROS", "PREVISION: 50/30/20 → 40/35/25");
    }

    @Test void sinCambiosNoAnota() {
        when(dao.getPesosPrevision()).thenReturn(Pesos.POR_DEFECTO);
        ctl.guardarPrevision(new PesosPrevision(50, 30, 20), admin);
        verify(logDao, never()).insertar(anyInt(), anyString(), anyString());
    }

    @Test void siNoSumanCienEs422YNoEscribe() {
        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> ctl.guardarPrevision(new PesosPrevision(50, 30, 30), admin));
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, e.getStatusCode());
        assertEquals("Los tres pesos tienen que ser enteros entre 0 y 100 y sumar 100.", e.getReason());
        verify(dao, never()).guardarPesosPrevision(any());
    }

    @Test void unNegativoEs422() {
        assertThrows(ResponseStatusException.class, () -> ctl.guardarPrevision(new PesosPrevision(-10, 60, 50), admin));
        verify(dao, never()).guardarPesosPrevision(any());
    }
}
