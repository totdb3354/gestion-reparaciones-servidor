package com.reparaciones.servidor.controller;

import com.reparaciones.servidor.dao.DificultadPuntosDAO;
import com.reparaciones.servidor.dao.LogDAO;
import com.reparaciones.servidor.model.ValorDificultad;
import com.reparaciones.servidor.security.UsuarioPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DificultadControllerTest {

    private final DificultadPuntosDAO dao = mock(DificultadPuntosDAO.class);
    private final LogDAO logDao = mock(LogDAO.class);
    private final DificultadController ctl = new DificultadController(dao, logDao);
    private final UsuarioPrincipal admin = new UsuarioPrincipal(1, "admin", "x", "ADMIN", null);

    @Test void getDevuelveLaTabla() {
        when(dao.getAll()).thenReturn(List.of(new ValorDificultad("bateria", 1.0)));
        assertEquals(1, ctl.getValores().size());
    }

    @Test void putActualizaYLogueaSoloLosCambios() {
        when(dao.getValores()).thenReturn(Map.of("bateria", 1.0, "chasis", 2.0));
        when(dao.actualizar("chasis", 3.0)).thenReturn(1);
        ctl.actualizarValores(List.of(
                new ValorDificultad("bateria", 1.0),   // sin cambio → ni update ni log
                new ValorDificultad("chasis", 3.0)), admin);
        verify(dao, never()).actualizar("bateria", 1.0);
        verify(dao).actualizar("chasis", 3.0);
        verify(logDao).insertar(eq(1), eq("EDITAR_PUNTOS"), contains("chasis: 2,0 -> 3,0"));
    }

    @Test void putRechazaNegativosYNoEscribe() {
        when(dao.getValores()).thenReturn(Map.of("bateria", 1.0));
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> ctl.actualizarValores(List.of(new ValorDificultad("bateria", -0.5)), admin));
        assertEquals(422, ex.getStatusCode().value());
        verify(dao, never()).actualizar(anyString(), anyDouble());
        verifyNoInteractions(logDao);
    }

    @Test void putRechazaClaveDesconocida() {
        when(dao.getValores()).thenReturn(Map.of("bateria", 1.0));
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> ctl.actualizarValores(List.of(new ValorDificultad("revision", 0.13)), admin));
        assertEquals(422, ex.getStatusCode().value());
        verify(dao, never()).actualizar(anyString(), anyDouble());
    }
}
