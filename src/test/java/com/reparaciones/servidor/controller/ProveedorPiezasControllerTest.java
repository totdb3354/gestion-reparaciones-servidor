package com.reparaciones.servidor.controller;

import com.reparaciones.servidor.dao.LogDAO;
import com.reparaciones.servidor.security.UsuarioPrincipal;
import com.reparaciones.servidor.service.ProveedorPiezasService;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ProveedorPiezasControllerTest {

    private final ProveedorPiezasService servicio = mock(ProveedorPiezasService.class);
    private final LogDAO logDao = mock(LogDAO.class);
    private final ProveedorPiezasController ctl = new ProveedorPiezasController(servicio, logDao);
    private final UsuarioPrincipal yo = new UsuarioPrincipal(7, "tecnico_f", "", "SUPERTECNICO", 3);

    @Test void registraCuantasPiezasAnadeYQuita() {
        when(servicio.reemplazar(5, List.of(1, 2))).thenReturn(new ProveedorPiezasService.Cambios(2, 1));
        ctl.reemplazar(5, new ProveedorPiezasController.Request(List.of(1, 2)), yo);
        verify(logDao).insertar(7, "EDITAR_PIEZAS_PROVEEDOR", "ID_PROV: 5, PIEZAS: +2 -1");
    }

    @Test void unFalloNoRegistraNada() {
        when(servicio.reemplazar(anyInt(), any())).thenThrow(new ResponseStatusException(org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY, "x"));
        assertThrows(ResponseStatusException.class, () -> ctl.reemplazar(5, new ProveedorPiezasController.Request(List.of(1)), yo));
        verifyNoInteractions(logDao);
    }
}
