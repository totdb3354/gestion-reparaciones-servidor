package com.reparaciones.servidor.controller;

import com.reparaciones.servidor.dao.ComponenteDAO;
import com.reparaciones.servidor.dao.LogDAO;
import com.reparaciones.servidor.model.Componente;
import com.reparaciones.servidor.security.UsuarioPrincipal;
import com.reparaciones.servidor.service.PrevisionPedidoService;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

/** La previsión de Stock solo se calcula para SUPERTECNICO y ADMIN (spec 0.9.5 §3.3). */
class ComponenteControllerPrevisionTest {

    private final ComponenteDAO dao = mock(ComponenteDAO.class);
    private final PrevisionPedidoService prevision = mock(PrevisionPedidoService.class);
    private final ComponenteController ctl = new ComponenteController(dao, mock(LogDAO.class), prevision);

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

    private static <T> T eq(T v) { return org.mockito.ArgumentMatchers.eq(v); }
}
