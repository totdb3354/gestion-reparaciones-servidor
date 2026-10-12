package com.reparaciones.servidor.service;

import com.reparaciones.servidor.dao.ProveedorDAO;
import com.reparaciones.servidor.dao.ProveedorTipoDAO;
import com.reparaciones.servidor.model.Componente;
import com.reparaciones.servidor.model.Proveedor;
import com.reparaciones.servidor.service.ProveedorPiezasService.Entrada;
import com.reparaciones.servidor.service.ProveedorPiezasService.TipoProveedor;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Tipos de pieza de cada proveedor (spec 2026-10-12 §4.2-§4.3). */
class ProveedorPiezasServiceTest {

    private final ProveedorDAO proveedorDao = mock(ProveedorDAO.class);
    private final ProveedorTipoDAO tiposDao = mock(ProveedorTipoDAO.class);
    private final ProveedorPiezasService servicio = new ProveedorPiezasService(proveedorDao, tiposDao);

    private void existe(int idProv, String tipo) {
        Proveedor p = mock(Proveedor.class);
        when(p.getTipo()).thenReturn(tipo);
        when(proveedorDao.getById(idProv)).thenReturn(Optional.of(p));
        when(proveedorDao.getByIdParaEditar(idProv)).thenReturn(Optional.of(p));
    }

    /** Baterías del 12 y el 13 (activas) y del 11 (desactivada); glass del 14; cámara del 13; una pantalla desactivada. */
    private void catalogo() {
        when(tiposDao.piezas()).thenReturn(List.of(
                new ProveedorTipoDAO.Pieza("bati12", true), new ProveedorTipoDAO.Pieza("bati13", true),
                new ProveedorTipoDAO.Pieza("bati11", false), new ProveedorTipoDAO.Pieza("gi14negra", true),
                new ProveedorTipoDAO.Pieza("cami13", true), new ProveedorTipoDAO.Pieza("lcdi15negraic", false),
                new ProveedorTipoDAO.Pieza("tapai15black", true)));
    }

    private static Entrada e(String prefijo, String... excluidos) { return new Entrada(prefijo, List.of(excluidos)); }

    private ResponseStatusException falla422(List<Entrada> tipos, String mensaje) {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> servicio.reemplazar(7, tipos));
        assertEquals(422, ex.getStatusCode().value());
        assertEquals(mensaje, ex.getReason());
        verify(tiposDao, never()).reemplazar(anyInt(), any());
        return ex;
    }

    // ── leer ─────────────────────────────────────────────────────────────────────

    @Test void leerDevuelveTodosLosTiposEnElOrdenDelFormulario() {
        existe(7, "COMPONENTES");
        catalogo();
        when(tiposDao.deProveedor(7)).thenReturn(Map.of("bat", Set.of("13"), "lcd", Set.of()));
        List<TipoProveedor> r = servicio.leer(7);
        assertEquals(List.of(
                new TipoProveedor("bat", true, List.of("12", "13"), List.of("13")),
                new TipoProveedor("tapa", false, List.of("15"), List.of()),
                new TipoProveedor("g", false, List.of("14"), List.of()),
                new TipoProveedor("lcd", true, List.of(), List.of()),          // sin piezas activas: sigue saliendo
                new TipoProveedor("cam", false, List.of("13"), List.of())), r);
        verify(proveedorDao, never()).getByIdParaEditar(anyInt());              // leer no bloquea
    }

    @Test void leerEnsenaLosExcluidosQueYaNoTienenPiezaActiva() {
        existe(7, "COMPONENTES");
        catalogo();
        when(tiposDao.deProveedor(7)).thenReturn(Map.of("bat", Set.of("11")));
        assertEquals(List.of("11", "12", "13"), servicio.leer(7).get(0).modelos());
    }

    @Test void leerProveedorInexistenteEs404YDeOtrosEs422() {
        when(proveedorDao.getById(99)).thenReturn(Optional.empty());
        ResponseStatusException e404 = assertThrows(ResponseStatusException.class, () -> servicio.leer(99));
        assertEquals(404, e404.getStatusCode().value());
        assertEquals("Proveedor no encontrado.", e404.getReason());
        existe(8, "OTROS");
        ResponseStatusException e422 = assertThrows(ResponseStatusException.class, () -> servicio.leer(8));
        assertEquals("Solo los proveedores de componentes tienen piezas.", e422.getReason());
    }

    // ── reemplazar ───────────────────────────────────────────────────────────────

    @Test void reemplazarGuardaConBloqueoYDevuelveElResumenOrdenado() {
        existe(7, "COMPONENTES");
        catalogo();
        String resumen = servicio.reemplazar(7, List.of(e("g"), e("bat", "13", "12mini", "13")));
        assertEquals("bat (sin 12mini, 13), g (todos)", resumen);
        verify(tiposDao).reemplazar(7, Map.of("bat", Set.of("12mini", "13"), "g", Set.of()));
        verify(proveedorDao).getByIdParaEditar(7);
        verify(proveedorDao, never()).getById(anyInt());
    }

    @Test void elResumenVaEnElOrdenDeTiposYDeModelosNoAlfabetico() {
        existe(7, "COMPONENTES");
        catalogo();
        assertEquals("tapa (todos), g (sin 8, 11)", servicio.reemplazar(7, List.of(e("g", "11", "8"), e("tapa"))));
    }

    @Test void listaVaciaNoVendeNada() {
        existe(7, "COMPONENTES");
        catalogo();
        assertEquals("ninguna", servicio.reemplazar(7, List.of()));
        verify(tiposDao).reemplazar(7, Map.of());
    }

    @Test void unTipoSinPiezasActivasSePuedeMarcarSinExcepciones() {
        existe(7, "COMPONENTES");
        catalogo();
        assertEquals("lcd (todos)", servicio.reemplazar(7, List.of(e("lcd"))));
    }

    @Test void erroresDeLaPeticion() {
        existe(7, "COMPONENTES");
        catalogo();
        falla422(null, "Falta la lista de tipos.");
        List<Entrada> conNulo = new ArrayList<>();
        conNulo.add(null);
        falla422(conNulo, "Falta la lista de tipos.");
        falla422(List.of(new Entrada("bat", null)), "Falta la lista de modelos excluidos.");
        falla422(List.of(e("pan")), "Algún tipo de pieza no existe.");
        falla422(List.of(new Entrada(null, List.of())), "Algún tipo de pieza no existe.");
        falla422(List.of(e("bat"), e("bat", "12")), "Un tipo de pieza está repetido.");
        falla422(List.of(e("bat", "13x")), "Algún modelo no existe.");
        List<String> modeloNulo = new ArrayList<>();
        modeloNulo.add(null);
        falla422(List.of(new Entrada("bat", modeloNulo)), "Algún modelo no existe.");
        falla422(List.of(e("bat", "12", "13")), "Si no vende ningún modelo de un tipo, desmarca el tipo.");
    }

    @Test void unTipoSinPiezasActivasSeGuardaConSusExcluidos() {
        existe(7, "COMPONENTES");
        catalogo();
        assertEquals("lcd (sin 15)", servicio.reemplazar(7, List.of(e("lcd", "15"))));
        verify(tiposDao).reemplazar(7, Map.of("lcd", Set.of("15")));
    }

    @Test void reemplazarProveedorInexistenteEs404YDeOtrosEs422() {
        when(proveedorDao.getByIdParaEditar(99)).thenReturn(Optional.empty());
        ResponseStatusException e404 = assertThrows(ResponseStatusException.class, () -> servicio.reemplazar(99, List.of()));
        assertEquals(404, e404.getStatusCode().value());
        existe(8, "OTROS");
        ResponseStatusException e422 = assertThrows(ResponseStatusException.class, () -> servicio.reemplazar(8, List.of()));
        assertEquals(422, e422.getStatusCode().value());
        assertEquals("Solo los proveedores de componentes tienen piezas.", e422.getReason());
        verify(tiposDao, never()).reemplazar(anyInt(), any());
    }

    // ── proveedores de cada pieza del listado de Stock ───────────────────────────

    @Test void proveedoresPorMasterUneLosDeLasPiezasDelGrupo() {
        when(tiposDao.todos()).thenReturn(Map.of(
                1, Map.of("cam", Set.of()),
                2, Map.of("cam", Set.of("13")),                     // no vende la del 13, sí la del 13 Pro
                3, Map.of("bat", Set.of())));
        Componente master = new Componente(10, "cami13", null, 0, 0, true, null);
        Componente slave = new Componente(11, "cami13pro", null, 0, 0, true, null);
        slave.setIdComMaster(10);
        Componente desactivada = new Componente(12, "bati12", null, 0, 0, false, null);
        Componente sinTipo = new Componente(13, "flexboton", null, 0, 0, true, null);
        Map<Integer, List<Integer>> r = servicio.proveedoresPorMaster(List.of(master, slave, desactivada, sinTipo));
        assertEquals(List.of(1, 2), r.get(10));
        assertEquals(List.of(3), r.get(12));
        assertEquals(List.of(), r.get(13));
        assertFalse(r.containsKey(11));                                 // el slave va por su master
    }
}
