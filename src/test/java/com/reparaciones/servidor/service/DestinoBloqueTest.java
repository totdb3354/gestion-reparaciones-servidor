package com.reparaciones.servidor.service;

import com.reparaciones.servidor.dao.BloqueCompraDAO;
import com.reparaciones.servidor.model.DestinoPedido;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

/** Regla de destino de una línea nueva o que cambia de proveedor (0.9.9, spec §5.2). */
class DestinoBloqueTest {

    private static final LocalDateTime AT = LocalDateTime.of(2026, 10, 9, 10, 0);
    private final BloqueCompraDAO dao = mock(BloqueCompraDAO.class);
    private final DestinoBloque destino = new DestinoBloque(dao);

    private static BloqueCompraDAO.Bloque bloque(int id, int idProv) {
        return new BloqueCompraDAO.Bloque(id, idProv, AT, null, false, AT);
    }

    @Test void sinPedidoVaAlAbiertoMasRecienteDelProveedor() {
        when(dao.abiertoMasReciente(2)).thenReturn(Optional.of(41));
        assertEquals(new DestinoBloque.Destino(41, false), destino.resolver(2, null, true));
        verify(dao, never()).crear(anyInt());
    }

    @Test void sinPedidoNiAbiertoCreaUnoNuevo() {
        when(dao.crear(2)).thenReturn(90);
        assertEquals(new DestinoBloque.Destino(90, true), destino.resolver(2, null, true));
    }

    @Test void sinPreferirAbiertoCreaSiempreUnoNuevo() {
        when(dao.crear(2)).thenReturn(90);
        assertEquals(new DestinoBloque.Destino(90, true), destino.resolver(2, null, false));
        verify(dao, never()).abiertoMasReciente(anyInt());
    }

    @Test void pedidoConBloqueNuloCreaUnoNuevo() {
        when(dao.crear(2)).thenReturn(90);
        assertEquals(new DestinoBloque.Destino(90, true), destino.resolver(2, new DestinoPedido(null), true));
        verify(dao, never()).abiertoMasReciente(anyInt());
    }

    @Test void pedidoConUnBloqueDelProveedorVaAEse() {
        when(dao.getById(41)).thenReturn(Optional.of(bloque(41, 2)));
        assertEquals(new DestinoBloque.Destino(41, false), destino.resolver(2, new DestinoPedido(41), true));
    }

    @Test void unBloqueDeOtroProveedorEs422() {
        when(dao.getById(41)).thenReturn(Optional.of(bloque(41, 3)));
        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> destino.resolver(2, new DestinoPedido(41), true));
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, e.getStatusCode());
        assertEquals("El bloque 41 es de otro proveedor.", e.getReason());
    }

    @Test void unBloqueQueYaNoExisteEs409() {
        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> destino.resolver(2, new DestinoPedido(99), true));
        assertEquals(HttpStatus.CONFLICT, e.getStatusCode());
        assertEquals("El bloque 99 ya no existe; elige otro destino.", e.getReason());
        verify(dao, never()).crear(anyInt());
    }
}
