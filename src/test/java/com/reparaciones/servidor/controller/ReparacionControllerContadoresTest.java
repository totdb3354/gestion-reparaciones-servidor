package com.reparaciones.servidor.controller;

import com.reparaciones.servidor.dao.*;
import com.reparaciones.servidor.idempotencia.RegistroIdempotencia;
import com.reparaciones.servidor.model.ContadoresPendientes;
import com.reparaciones.servidor.security.UsuarioPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** GET /api/reparaciones/pendientes/contadores (spec web-taller §5.2): el badge de Pendientes de la web. */
class ReparacionControllerContadoresTest {

    private final ReparacionDAO dao = mock(ReparacionDAO.class);
    private final ReparacionController ctl = new ReparacionController(
            dao, mock(ReparacionComponenteDAO.class), mock(LogDAO.class), mock(BorradorDAO.class),
            mock(ComponenteDAO.class), mock(DificultadPuntosDAO.class), new RegistroIdempotencia());
    private final UsuarioPrincipal tecnico = new UsuarioPrincipal(8, "tecnico_n", "x", "TECNICO", 4);
    private final UsuarioPrincipal supertecnico = new UsuarioPrincipal(7, "tecnico_f", "x", "SUPERTECNICO", 3);
    private final UsuarioPrincipal admin = new UsuarioPrincipal(1, "admin", "x", "ADMIN", null);

    @Test void tecnicoRecibeSusContadores() {
        when(dao.contarPendientes(4)).thenReturn(new ContadoresPendientes(10, 0, 2));
        assertEquals(new ContadoresPendientes(10, 0, 2), ctl.getContadoresPendientes(null, tecnico));
    }

    @Test void tecnicoPidiendoAOtroEs403() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> ctl.getContadoresPendientes(9, tecnico));
        assertEquals(403, ex.getStatusCode().value());
        verifyNoInteractions(dao);
    }

    @Test void supertecnicoSinParametroRecibeLosSuyosYConParametroLosDelOtro() {
        when(dao.contarPendientes(3)).thenReturn(new ContadoresPendientes(0, 0, 0));
        when(dao.contarPendientes(9)).thenReturn(new ContadoresPendientes(1, 2, 3));
        assertEquals(new ContadoresPendientes(0, 0, 0), ctl.getContadoresPendientes(null, supertecnico));
        assertEquals(new ContadoresPendientes(1, 2, 3), ctl.getContadoresPendientes(9, supertecnico));
    }

    @Test void adminSinTecnicoRecibeCerosSinConsultar() {
        assertEquals(new ContadoresPendientes(0, 0, 0), ctl.getContadoresPendientes(null, admin));
        verifyNoInteractions(dao);
        when(dao.contarPendientes(9)).thenReturn(new ContadoresPendientes(5, 0, 0));
        assertEquals(new ContadoresPendientes(5, 0, 0), ctl.getContadoresPendientes(9, admin));
    }
}
