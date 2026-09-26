package com.reparaciones.servidor.controller;

import com.reparaciones.servidor.dao.LogDAO;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

/** GET /api/logs con limite y GET /api/logs/acciones (spec 6 §4.5). Mockito sin Spring; la guarda de rol se comprueba
 *  sobre la anotación (el resto de GET /api/logs ya era hasRole('ADMIN')). */
class LogControllerTest {

    private final LogDAO logDao = mock(LogDAO.class);
    private final LogController ctl = new LogController(logDao);

    private void limiteNoValido(Integer limite) {
        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> ctl.getAll(null, null, null, null, limite));
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, e.getStatusCode());
        assertEquals("Límite no válido (debe estar entre 1 y 5000).", e.getReason());
    }

    @Test void limiteFueraDeRangoEs422SinConsultar() {
        limiteNoValido(0);
        limiteNoValido(-1);
        limiteNoValido(5001);
        verifyNoInteractions(logDao);
    }

    @Test void losExtremosDelRangoSonValidos() {
        ctl.getAll(null, null, null, null, 1);
        ctl.getAll(null, null, null, null, 5000);
        verify(logDao).getFiltered(null, null, null, null, 1);
        verify(logDao).getFiltered(null, null, null, null, 5000);
    }

    /** Sin limite (el JavaFX) el DAO recibe null: todo el log, como antes. */
    @Test void sinLimitePasaNullYLosFiltrosTalCual() {
        LocalDate desde = LocalDate.of(2026, 6, 10);
        LocalDate hasta = LocalDate.of(2026, 6, 12);
        ctl.getAll("LOGIN", "usuario-a", desde, hasta, null);
        verify(logDao).getFiltered("LOGIN", "usuario-a", desde, hasta, null);
    }

    @Test void accionesDevuelveLaListaDelDao() {
        when(logDao.getAcciones()).thenReturn(List.of("CREAR_USUARIO", "LOGIN"));
        assertEquals(List.of("CREAR_USUARIO", "LOGIN"), ctl.getAcciones());
    }

    @Test void accionesEsSoloAdmin() throws Exception {
        PreAuthorize guarda = LogController.class.getMethod("getAcciones").getAnnotation(PreAuthorize.class);
        assertEquals("hasRole('ADMIN')", guarda.value());
    }
}
