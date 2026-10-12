package com.reparaciones.servidor.controller;

import com.reparaciones.servidor.dao.LogDAO;
import com.reparaciones.servidor.security.UsuarioPrincipal;
import com.reparaciones.servidor.service.ProveedorPiezasService;
import com.reparaciones.servidor.service.ProveedorPiezasService.Entrada;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ProveedorPiezasControllerTest {

    private final ProveedorPiezasService servicio = mock(ProveedorPiezasService.class);
    private final LogDAO logDao = mock(LogDAO.class);
    private final ProveedorPiezasController ctl = new ProveedorPiezasController(servicio, logDao);
    private final UsuarioPrincipal yo = new UsuarioPrincipal(7, "tecnico_f", "", "SUPERTECNICO", 3);

    @Test void leerDevuelveLosTiposDelServicio() {
        when(servicio.leer(5)).thenReturn(List.of(new ProveedorPiezasService.TipoProveedor("bat", true, List.of("12"), List.of())));
        assertEquals(List.of(new ProveedorPiezasController.Tipo("bat", true, List.of("12"), List.of())), ctl.leer(5));
    }

    @Test void guardarPasaLosTiposYRegistraElResumen() {
        when(servicio.reemplazar(eq(5), any())).thenReturn("bat (todos), g (sin 12mini)");
        ctl.reemplazar(5, new ProveedorPiezasController.Request(List.of(
                new ProveedorPiezasController.TipoRequest("bat", List.of()),
                new ProveedorPiezasController.TipoRequest("g", List.of("12mini")))), yo);
        verify(servicio).reemplazar(5, List.of(new Entrada("bat", List.of()), new Entrada("g", List.of("12mini"))));
        verify(logDao).insertar(7, "EDITAR_PIEZAS_PROVEEDOR", "ID_PROV: 5, PIEZAS: bat (todos), g (sin 12mini)");
    }

    @Test void losNulosLleganTalCualAlServicio() {
        when(servicio.reemplazar(anyInt(), any())).thenThrow(new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "x"));
        assertThrows(ResponseStatusException.class, () -> ctl.reemplazar(5, new ProveedorPiezasController.Request(null), yo));
        verify(servicio).reemplazar(5, null);
        List<ProveedorPiezasController.TipoRequest> conNulo = new ArrayList<>();
        conNulo.add(null);
        assertThrows(ResponseStatusException.class, () -> ctl.reemplazar(5, new ProveedorPiezasController.Request(conNulo), yo));
        verify(servicio).reemplazar(eq(5), argThat(l -> l != null && l.size() == 1 && l.get(0) == null));
        verifyNoInteractions(logDao);
    }
}
