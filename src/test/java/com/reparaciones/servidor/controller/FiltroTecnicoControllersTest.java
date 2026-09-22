package com.reparaciones.servidor.controller;

import com.reparaciones.servidor.dao.*;
import com.reparaciones.servidor.idempotencia.RegistroIdempotencia;
import com.reparaciones.servidor.security.UsuarioPrincipal;
import com.reparaciones.servidor.service.ImeiLookupService;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Los seis endpoints de lectura del taller pasan el ?tecnico= por FiltroTecnico (spec web-taller §5.1). */
class FiltroTecnicoControllersTest {

    private final ReparacionDAO dao = mock(ReparacionDAO.class);
    private final ReparacionController rep = new ReparacionController(
            dao, mock(ReparacionComponenteDAO.class), mock(LogDAO.class), mock(BorradorDAO.class),
            mock(ComponenteDAO.class), mock(DificultadPuntosDAO.class), mock(TecnicoDAO.class), new RegistroIdempotencia());
    private final GlassController glass = new GlassController(dao, mock(LogDAO.class), mock(TelefonoDAO.class), mock(ImeiLookupService.class));
    private final PulidoController pulido = new PulidoController(dao, mock(LogDAO.class), mock(TelefonoDAO.class), mock(ImeiLookupService.class));

    private final UsuarioPrincipal tecnico = new UsuarioPrincipal(8, "tecnico_n", "x", "TECNICO", 4);
    private final UsuarioPrincipal supertecnico = new UsuarioPrincipal(7, "tecnico_f", "x", "SUPERTECNICO", 3);
    private final UsuarioPrincipal admin = new UsuarioPrincipal(1, "admin", "x", "ADMIN", null);

    @Test void tecnicoSinParametroSoloRecibeLoSuyoEnLasSeisRutas() {
        rep.getHistorial(null, tecnico);        verify(dao).getHistorial(4);
        rep.getAsignaciones(null, tecnico);     verify(dao).getAsignaciones(4);
        glass.getHistorial(null, tecnico);      verify(dao).getHistorialGlass(4);
        glass.getAsignaciones(null, tecnico);   verify(dao).getAsignacionesGlass(4);
        pulido.getHistorial(null, tecnico);     verify(dao).getHistorialPulido(4);
        pulido.getAsignaciones(null, tecnico);  verify(dao).getAsignacionesPulido(4);
    }

    @Test void tecnicoPidiendoAOtroEs403SinTocarElDao() {
        assertEquals(403, status(() -> rep.getHistorial(9, tecnico)));
        assertEquals(403, status(() -> rep.getAsignaciones(9, tecnico)));
        assertEquals(403, status(() -> glass.getHistorial(9, tecnico)));
        assertEquals(403, status(() -> glass.getAsignaciones(9, tecnico)));
        assertEquals(403, status(() -> pulido.getHistorial(9, tecnico)));
        assertEquals(403, status(() -> pulido.getAsignaciones(9, tecnico)));
        verifyNoInteractions(dao);
    }

    @Test void supertecnicoYAdminConservanElFiltroLibre() {
        rep.getHistorial(null, supertecnico);   verify(dao).getHistorial(null);
        rep.getHistorial(9, supertecnico);      verify(dao).getHistorial(9);
        pulido.getAsignaciones(null, admin);    verify(dao).getAsignacionesPulido(null);
        glass.getAsignaciones(9, admin);        verify(dao).getAsignacionesGlass(9);
    }

    private static int status(Runnable r) {
        return assertThrows(ResponseStatusException.class, r::run).getStatusCode().value();
    }
}
