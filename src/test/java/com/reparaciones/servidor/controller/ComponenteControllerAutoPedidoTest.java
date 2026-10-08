package com.reparaciones.servidor.controller;

import com.reparaciones.servidor.dao.ComponenteDAO;
import com.reparaciones.servidor.dao.LogDAO;
import com.reparaciones.servidor.model.Componente;
import com.reparaciones.servidor.security.UsuarioPrincipal;
import com.reparaciones.servidor.service.PrevisionPedidoService;
import org.junit.jupiter.api.Test;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Pedido automático (spec 0.9.6 §4.2): la marca se guarda en el master, se apunta y solo la ven SUPER y ADMIN. */
class ComponenteControllerAutoPedidoTest {

    private static final UsuarioPrincipal SUPER = new UsuarioPrincipal(7, "tecnico_f", "", "SUPERTECNICO", 3);

    private final ComponenteDAO dao = mock(ComponenteDAO.class);
    private final LogDAO logDao = mock(LogDAO.class);
    private final ComponenteController ctl = new ComponenteController(dao, logDao, mock(PrevisionPedidoService.class));

    @Test void marcarGuardaEnElMasterYLoApunta() {
        when(dao.setAutoPedido(12, true)).thenReturn(3);
        ctl.setAutoPedido(12, new ComponenteController.AutoPedidoRequest(true), SUPER);
        verify(logDao).insertar(7, "EDITAR_COMPONENTE", "ID_COM: 3, AUTO_PEDIDO: SI");
    }

    @Test void desmarcarApuntaNo() {
        when(dao.setAutoPedido(3, false)).thenReturn(3);
        ctl.setAutoPedido(3, new ComponenteController.AutoPedidoRequest(false), SUPER);
        verify(logDao).insertar(7, "EDITAR_COMPONENTE", "ID_COM: 3, AUTO_PEDIDO: NO");
    }

    @Test void unIdInexistenteDa404SinApuntar() {
        when(dao.setAutoPedido(99, true)).thenThrow(new EmptyResultDataAccessException(1));
        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> ctl.setAutoPedido(99, new ComponenteController.AutoPedidoRequest(true), SUPER));
        assertEquals(HttpStatus.NOT_FOUND, e.getStatusCode());
        verifyNoInteractions(logDao);
    }

    @Test void alTecnicoLeLlegaLaMarcaNula() {
        Componente c = new Componente();
        c.setAutoPedido(true);
        when(dao.getAllGestionados()).thenReturn(List.of(c));
        ctl.getAllGestionados(new UsuarioPrincipal(8, "tecnico_n", "", "TECNICO", 4));
        assertNull(c.getAutoPedido());
    }

    @Test void alSupertecnicoYAlAdminSeLaDeja() {
        Componente c = new Componente();
        c.setAutoPedido(true);
        when(dao.getAllGestionados()).thenReturn(List.of(c));
        ctl.getAllGestionados(SUPER);
        ctl.getAllGestionados(new UsuarioPrincipal(1, "admin", "", "ADMIN", null));
        assertEquals(Boolean.TRUE, c.getAutoPedido());
    }
}
