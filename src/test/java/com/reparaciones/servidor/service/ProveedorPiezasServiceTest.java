package com.reparaciones.servidor.service;

import com.reparaciones.servidor.dao.ProveedorComponenteDAO;
import com.reparaciones.servidor.dao.ProveedorDAO;
import com.reparaciones.servidor.model.Proveedor;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ProveedorPiezasServiceTest {

    private final ProveedorDAO proveedorDao = mock(ProveedorDAO.class);
    private final ProveedorComponenteDAO catalogo = mock(ProveedorComponenteDAO.class);
    private final ProveedorPiezasService servicio = new ProveedorPiezasService(proveedorDao, catalogo);

    private static Proveedor proveedor(String tipo) {
        Proveedor p = mock(Proveedor.class);
        when(p.getTipo()).thenReturn(tipo);
        return p;
    }

    private void existe(int idProv, String tipo) {
        Proveedor p = proveedor(tipo);
        when(proveedorDao.getById(idProv)).thenReturn(Optional.of(p));
    }

    private static int estado(ResponseStatusException e) { return e.getStatusCode().value(); }

    @Test void sustituyeLaListaAnadiendoYQuitandoSoloLoQueCambia() {
        existe(7, "COMPONENTES");
        when(catalogo.mastersDe(any())).thenReturn(Map.of(5, 5, 12, 3));   // 12 es un slave de 3
        when(catalogo.piezasDe(7)).thenReturn(Set.of(5, 8));
        ProveedorPiezasService.Cambios c = servicio.reemplazar(7, List.of(5, 12, 12));
        assertEquals(new ProveedorPiezasService.Cambios(1, 1), c);
        verify(catalogo).anadir(7, List.of(3));
        verify(catalogo).quitar(7, List.of(8));
        verify(catalogo).mastersDe(new LinkedHashSet<>(List.of(5, 12)));
    }

    @Test void sinCambiosNoEscribe() {
        existe(7, "COMPONENTES");
        when(catalogo.mastersDe(any())).thenReturn(Map.of(5, 5));
        when(catalogo.piezasDe(7)).thenReturn(Set.of(5));
        assertEquals(new ProveedorPiezasService.Cambios(0, 0), servicio.reemplazar(7, List.of(5)));
        verify(catalogo, never()).anadir(anyInt(), any());
        verify(catalogo, never()).quitar(anyInt(), any());
    }

    @Test void listaVaciaLasQuitaTodas() {
        existe(7, "COMPONENTES");
        when(catalogo.piezasDe(7)).thenReturn(Set.of(5, 8));
        assertEquals(new ProveedorPiezasService.Cambios(0, 2), servicio.reemplazar(7, List.of()));
        verify(catalogo).quitar(7, List.of(5, 8));
    }

    @Test void proveedorInexistenteEs404() {
        when(proveedorDao.getById(99)).thenReturn(Optional.empty());
        ResponseStatusException e = assertThrows(ResponseStatusException.class, () -> servicio.reemplazar(99, List.of(5)));
        assertEquals(404, estado(e));
        assertEquals(ProveedorPiezasService.MSG_NO_ENCONTRADO, e.getReason());
    }

    @Test void proveedorDeOtrosEs422() {
        existe(7, "OTROS");
        ResponseStatusException e = assertThrows(ResponseStatusException.class, () -> servicio.reemplazar(7, List.of(5)));
        assertEquals(422, estado(e));
        assertEquals("Solo los proveedores de componentes tienen piezas.", e.getReason());
        verifyNoInteractions(catalogo);
    }

    @Test void sinListaEs422() {
        existe(7, "COMPONENTES");
        ResponseStatusException e = assertThrows(ResponseStatusException.class, () -> servicio.reemplazar(7, null));
        assertEquals(422, estado(e));
        assertEquals("Falta la lista de piezas.", e.getReason());
    }

    @Test void unaPiezaQueNoExisteEs422YNoEscribe() {
        existe(7, "COMPONENTES");
        when(catalogo.mastersDe(any())).thenReturn(Map.of(5, 5));
        ResponseStatusException e = assertThrows(ResponseStatusException.class, () -> servicio.reemplazar(7, List.of(5, 99)));
        assertEquals(422, estado(e));
        assertEquals("Alguna pieza ya no existe.", e.getReason());
        verify(catalogo, never()).anadir(anyInt(), any());
        verify(catalogo, never()).quitar(anyInt(), any());
    }

    @Test void unNuloEnLaListaEs422() {
        existe(7, "COMPONENTES");
        List<Integer> conNulo = new ArrayList<>(Arrays.asList(5, null));
        ResponseStatusException e = assertThrows(ResponseStatusException.class, () -> servicio.reemplazar(7, conNulo));
        assertEquals(422, estado(e));
        assertEquals("Alguna pieza ya no existe.", e.getReason());
    }
}
