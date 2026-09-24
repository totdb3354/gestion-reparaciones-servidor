package com.reparaciones.servidor.controller;

import com.reparaciones.servidor.dao.ComponenteDAO;
import com.reparaciones.servidor.dao.LogDAO;
import com.reparaciones.servidor.security.UsuarioPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/** Rangos que hasta el 4a solo comprobaba el cliente (inventario de Stock §9.1-9.2); mismos textos. */
class ComponenteControllerValidacionTest {

    private final ComponenteDAO dao = mock(ComponenteDAO.class);
    private final LogDAO logDao = mock(LogDAO.class);
    private final ComponenteController ctl = new ComponenteController(dao, logDao);
    private final UsuarioPrincipal super7 = new UsuarioPrincipal(7, "tecnico_f", "", "SUPERTECNICO", 3);
    private final LocalDateTime ahora = LocalDateTime.of(2026, 9, 24, 10, 0);

    @Test void editarConStockNegativoEs422() {
        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> ctl.actualizar(5, new ComponenteController.ActualizarRequest("lcd-x", -1, 2, ahora), super7));
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, e.getStatusCode());
        assertEquals("Cantidad no válida (debe ser ≥ 0).", e.getReason());
        verify(dao, never()).actualizar(anyInt(), anyString(), anyInt(), anyInt(), any());
    }

    @Test void editarConMinimoNegativoEs422() {
        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> ctl.actualizar(5, new ComponenteController.ActualizarRequest("lcd-x", 3, -2, ahora), super7));
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, e.getStatusCode());
        assertEquals("Valor no válido (debe ser ≥ 0).", e.getReason());
        verify(dao, never()).actualizar(anyInt(), anyString(), anyInt(), anyInt(), any());
    }

    @Test void editarConCeroVale() {
        when(dao.getStockById(5)).thenReturn(4);
        ctl.actualizar(5, new ComponenteController.ActualizarRequest("lcd-x", 0, 0, ahora), super7);
        verify(dao).actualizar(5, "lcd-x", 0, 0, ahora);
    }

    @Test void minimoNegativoEs422YCeroVale() {
        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> ctl.setStockMinimo(5, new ComponenteController.StockMinimoRequest(-1), super7));
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, e.getStatusCode());
        assertEquals("Valor no válido (debe ser ≥ 0).", e.getReason());
        verify(dao, never()).setStockMinimo(anyInt(), anyInt());
        ctl.setStockMinimo(5, new ComponenteController.StockMinimoRequest(0), super7);
        verify(dao).setStockMinimo(5, 0);
    }
}
