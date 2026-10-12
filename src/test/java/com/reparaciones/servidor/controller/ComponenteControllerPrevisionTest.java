package com.reparaciones.servidor.controller;

import com.reparaciones.servidor.dao.ComponenteDAO;
import com.reparaciones.servidor.dao.LogDAO;
import com.reparaciones.servidor.service.ProveedorPiezasService;
import com.reparaciones.servidor.model.Componente;
import com.reparaciones.servidor.security.UsuarioPrincipal;
import com.reparaciones.servidor.service.PrevisionPedidoService;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

/** La previsión de Stock solo se calcula para SUPERTECNICO y ADMIN (spec 0.9.5 §3.3). */
class ComponenteControllerPrevisionTest {

    private final ComponenteDAO dao = mock(ComponenteDAO.class);
    private final PrevisionPedidoService prevision = mock(PrevisionPedidoService.class);
    private final ProveedorPiezasService catalogo = mock(ProveedorPiezasService.class);
    private final ComponenteController ctl = new ComponenteController(dao, mock(LogDAO.class), prevision, catalogo);

    @Test void alTecnicoNoSeLeCalcula() {
        when(dao.getAllGestionados()).thenReturn(List.of(new Componente()));
        ctl.getAllGestionados(new UsuarioPrincipal(8, "tecnico_n", "", "TECNICO", 4));
        verify(prevision, never()).rellenar(anyList(), any(LocalDate.class));
    }

    @Test void alSupertecnicoYAlAdminSi() {
        List<Componente> lista = List.of(new Componente());
        when(dao.getAllGestionados()).thenReturn(lista);
        ctl.getAllGestionados(new UsuarioPrincipal(7, "tecnico_f", "", "SUPERTECNICO", 3));
        ctl.getAllGestionados(new UsuarioPrincipal(1, "admin", "", "ADMIN", null));
        verify(prevision, times(2)).rellenar(eq(lista), any(LocalDate.class));
    }

    @Test void alTecnicoNoLeLleganNiLoPendienteNiLosProveedores() {
        Componente c = new Componente();
        c.setPendiente(3);
        when(dao.getAllGestionados()).thenReturn(List.of(c));
        ctl.getAllGestionados(new UsuarioPrincipal(8, "tecnico_n", "", "TECNICO", 4));
        assertNull(c.getPendiente());
        assertNull(c.getProveedores());
        verifyNoInteractions(catalogo);
    }

    @Test void alSupertecnicoCadaPiezaLlevaLosProveedoresDeSuMaster() {
        Componente master = new Componente(2, "bat-x", null, 0, 1, true, null);
        Componente slave = new Componente(5, "bat-y", null, 0, 1, true, null);
        slave.setIdComMaster(2);
        Componente sinLista = new Componente(7, "flex-x", null, 0, 1, true, null);
        when(dao.getAllGestionados()).thenReturn(List.of(master, slave, sinLista));
        when(catalogo.proveedoresPorMaster(List.of(master, slave, sinLista))).thenReturn(Map.of(2, List.of(1, 3)));
        ctl.getAllGestionados(new UsuarioPrincipal(7, "tecnico_f", "", "SUPERTECNICO", 3));
        assertEquals(List.of(1, 3), master.getProveedores());
        assertEquals(List.of(1, 3), slave.getProveedores());
        assertEquals(List.of(), sinLista.getProveedores());
    }

    private static <T> T eq(T v) { return org.mockito.ArgumentMatchers.eq(v); }
}
