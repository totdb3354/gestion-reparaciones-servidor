package com.reparaciones.servidor.controller;

import com.reparaciones.servidor.dao.LogDAO;
import com.reparaciones.servidor.dao.UsuarioDAO;
import com.reparaciones.servidor.security.UsuarioPrincipal;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.*;

class UsuarioControllerExclusionTest {

    private final UsuarioDAO dao = mock(UsuarioDAO.class);
    private final LogDAO logDao = mock(LogDAO.class);
    private final UsuarioController ctl = new UsuarioController(dao, logDao);
    private final UsuarioPrincipal admin = new UsuarioPrincipal(1, "admin", "x", "ADMIN", null);

    @Test void excluirActualizaYLoguea() {
        when(dao.getNombreByIdTec(7)).thenReturn("Laura");
        ctl.excluirEstadisticas(7, admin);
        verify(dao).excluirEstadisticas(7);
        verify(logDao).insertar(1, "EXCLUIR_ESTADISTICAS", "ID_TEC: 7, NOMBRE: Laura");
    }

    @Test void incluirActualizaYLoguea() {
        when(dao.getNombreByIdTec(7)).thenReturn("Laura");
        ctl.incluirEstadisticas(7, admin);
        verify(dao).incluirEstadisticas(7);
        verify(logDao).insertar(1, "INCLUIR_ESTADISTICAS", "ID_TEC: 7, NOMBRE: Laura");
    }
}
