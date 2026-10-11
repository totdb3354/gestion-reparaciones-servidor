package com.reparaciones.servidor.service;

import com.reparaciones.servidor.dao.BloqueCompraDAO;
import com.reparaciones.servidor.dao.CompraComponenteDAO;
import com.reparaciones.servidor.model.CompraComponente;
import com.reparaciones.servidor.model.DestinoPedido;
import com.reparaciones.servidor.model.LineaVista;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** BloqueCompraService con las DAO mockeadas y el DestinoBloque real (0.9.9, spec «Pedidos por bloques» §5). */
class BloqueCompraServiceTest {

    static final LocalDateTime AT = LocalDateTime.of(2026, 10, 9, 10, 0, 0);
    final BloqueCompraDAO bloqueDao = mock(BloqueCompraDAO.class);
    final CompraComponenteDAO compraDao = mock(CompraComponenteDAO.class);
    final BloqueCompraService servicio = new BloqueCompraService(bloqueDao, compraDao, new DestinoBloque(bloqueDao));

    static BloqueCompraDAO.Bloque bloque(int id, int idProv) {
        return new BloqueCompraDAO.Bloque(id, idProv, AT, null, false, AT);
    }

    /** Línea 5 del proveedor 2 en el bloque `idBloque`, con UPDATED_AT = AT. */
    static CompraComponente linea(String estado, Integer idBloque) {
        return new CompraComponente(5, 1, "lcd-x-negro", 2, "Proveedor A", 3, null, false, AT, null, 10.0, "EUR", 10.0,
                estado, AT).conBloque(idBloque, AT, null, AT, false);
    }

    static void falla(HttpStatus estado, String mensaje, Runnable accion) {
        ResponseStatusException e = assertThrows(ResponseStatusException.class, accion::run);
        assertEquals(estado, e.getStatusCode());
        assertEquals(mensaje, e.getReason());
    }

    // ── alta suelta ──
    @Test void altaSueltaVaAlAbiertoDelProveedor() {
        when(bloqueDao.abiertoMasReciente(2)).thenReturn(Optional.of(41));
        assertEquals(new DestinoBloque.Destino(41, false), servicio.altaSuelta(1, 2, 3, false, 10.0, "EUR", 10.0));
        verify(compraDao).insertar(1, 2, 3, false, 10.0, "EUR", 10.0, 41);
    }

    @Test void altaSueltaSinAbiertoCreaUnBloque() {
        when(bloqueDao.crear(2)).thenReturn(90);
        assertEquals(new DestinoBloque.Destino(90, true), servicio.altaSuelta(1, 2, 3, false, 10.0, "EUR", 10.0));
        verify(compraDao).insertar(1, 2, 3, false, 10.0, "EUR", 10.0, 90);
    }

    // ── mover ──
    @Test void moverUnaPendienteAOtroBloqueDelProveedor() {
        when(compraDao.getById(5)).thenReturn(Optional.of(linea("pendiente", 41)));
        when(bloqueDao.getById(40)).thenReturn(Optional.of(bloque(40, 2)));
        BloqueCompraService.Movimiento m = servicio.mover(5, new DestinoPedido(40), AT);
        verify(compraDao).moverABloque(5, 41, 40);
        verify(bloqueDao).borrarSiVacio(41);
        assertEquals(new BloqueCompraService.Movimiento(5, "pendiente", 41, 40, false, 2, false), m);
    }

    @Test void moverABloqueNuevoCreaUnoDelMismoProveedor() {
        when(compraDao.getById(5)).thenReturn(Optional.of(linea("en_camino", 41)));
        when(bloqueDao.contarLineas(41)).thenReturn(2);
        when(bloqueDao.crear(2)).thenReturn(90);
        assertEquals(new BloqueCompraService.Movimiento(5, "en_camino", 41, 90, true, 2, false),
                servicio.mover(5, new DestinoPedido(null), AT));
        verify(compraDao).moverABloque(5, 41, 90);
    }

    @Test void elOrigenQueSeQuedaVacioSeBorra() {
        when(compraDao.getById(5)).thenReturn(Optional.of(linea("pendiente", 41)));
        when(bloqueDao.getById(40)).thenReturn(Optional.of(bloque(40, 2)));
        when(bloqueDao.borrarSiVacio(41)).thenReturn(true);
        assertTrue(servicio.mover(5, new DestinoPedido(40), AT).origenBorrado());
    }

    @Test void moverAlMismoBloqueOSolaABloqueNuevoEs422SinTocarNada() {
        when(compraDao.getById(5)).thenReturn(Optional.of(linea("pendiente", 41)));
        when(bloqueDao.contarLineas(41)).thenReturn(1);
        falla(HttpStatus.UNPROCESSABLE_ENTITY, "La línea ya está en ese bloque.", () -> servicio.mover(5, new DestinoPedido(41), AT));
        falla(HttpStatus.UNPROCESSABLE_ENTITY, "La línea ya está sola en su bloque.", () -> servicio.mover(5, new DestinoPedido(null), AT));
        verify(compraDao, never()).moverABloque(anyInt(), any(), anyInt());
        verify(bloqueDao, never()).crear(anyInt());
    }

    @Test void moverAUnBloqueDeOtroProveedorEs422() {
        when(compraDao.getById(5)).thenReturn(Optional.of(linea("pendiente", 41)));
        when(bloqueDao.getById(40)).thenReturn(Optional.of(bloque(40, 3)));
        falla(HttpStatus.UNPROCESSABLE_ENTITY, "El bloque 40 es de otro proveedor.", () -> servicio.mover(5, new DestinoPedido(40), AT));
        verify(compraDao, never()).moverABloque(anyInt(), any(), anyInt());
    }

    @Test void moverConOtraVersionEs409YUnaLineaInexistenteEs404() {
        when(compraDao.getById(5)).thenReturn(Optional.of(linea("pendiente", 41)));
        falla(HttpStatus.CONFLICT, "Dato modificado por otro usuario", () -> servicio.mover(5, new DestinoPedido(40), AT.plusSeconds(1)));
        falla(HttpStatus.NOT_FOUND, "El pedido no existe", () -> servicio.mover(6, new DestinoPedido(40), AT));
        verify(compraDao, never()).moverABloque(anyInt(), any(), anyInt());
    }

    // ── editar cambiando el proveedor ──
    @Test void editarUnaPendienteAOtroProveedorVaASuAbierto() {
        when(compraDao.getById(5)).thenReturn(Optional.of(linea("pendiente", 41)));
        when(bloqueDao.abiertoMasReciente(3)).thenReturn(Optional.of(60));
        BloqueCompraService.Movimiento m = servicio.editarCambiandoProveedor(5, 3, 4, false, 12.0, "EUR", 12.0, AT, null);
        InOrder orden = inOrder(compraDao);
        orden.verify(compraDao).editar(5, 3, 4, false, 12.0, "EUR", 12.0, AT);
        orden.verify(compraDao).moverABloque(5, 41, 60);
        assertEquals(new BloqueCompraService.Movimiento(5, "pendiente", 41, 60, false, 3, false), m);
    }

    @Test void editarUnaYaPedidaSinDestinoCreaUnBloqueNuevo() {
        when(compraDao.getById(5)).thenReturn(Optional.of(linea("en_camino", 41)));
        when(bloqueDao.crear(3)).thenReturn(91);
        assertEquals(91, servicio.editarCambiandoProveedor(5, 3, 4, false, 12.0, "EUR", 12.0, AT, null).a());
        verify(bloqueDao, never()).abiertoMasReciente(anyInt());
    }

    @Test void editarConDestinoElegidoUsaEseBloque() {
        when(compraDao.getById(5)).thenReturn(Optional.of(linea("en_camino", 41)));
        when(bloqueDao.getById(60)).thenReturn(Optional.of(bloque(60, 3)));
        assertEquals(60, servicio.editarCambiandoProveedor(5, 3, 4, false, 12.0, "EUR", 12.0, AT, new DestinoPedido(60)).a());
    }

    @Test void siEditarFallaNoSeTocaNingunBloque() {
        when(compraDao.getById(5)).thenReturn(Optional.of(linea("pendiente", 41)));
        doThrow(new ResponseStatusException(HttpStatus.CONFLICT, "Dato modificado por otro usuario"))
                .when(compraDao).editar(5, 3, 4, false, 12.0, "EUR", 12.0, AT);
        assertThrows(ResponseStatusException.class,
                () -> servicio.editarCambiandoProveedor(5, 3, 4, false, 12.0, "EUR", 12.0, AT, null));
        verify(bloqueDao, never()).crear(anyInt());
        verify(compraDao, never()).moverABloque(anyInt(), any(), anyInt());
    }

    // ── borrar una línea pendiente ──
    @Test void borrarLaUltimaLineaBorraSuBloque() {
        when(compraDao.getById(5)).thenReturn(Optional.of(linea("pendiente", 41)));
        when(bloqueDao.borrarSiVacio(41)).thenReturn(true);
        assertEquals(Optional.of(41), servicio.borrarLineaPendiente(5));
        verify(compraDao).borrarPendiente(5, 41);
    }

    @Test void borrarUnaLineaDeUnBloqueConMasNoLoBorra() {
        when(compraDao.getById(5)).thenReturn(Optional.of(linea("pendiente", 41)));
        assertEquals(Optional.empty(), servicio.borrarLineaPendiente(5));
        verify(compraDao).borrarPendiente(5, 41);
    }

    // ── acciones de bloque ──
    static final String MODIFICADO = "Este bloque fue modificado por otro usuario.";

    static BloqueCompraDAO.LineaBloque lb(int id, String estado) {
        return new BloqueCompraDAO.LineaBloque(id, "lcd-" + id, 2, estado, AT);
    }

    static LineaVista v(int id) {
        return new LineaVista(id, AT);
    }

    void bloque41(BloqueCompraDAO.LineaBloque... lineas) {
        when(bloqueDao.getById(41)).thenReturn(Optional.of(bloque(41, 2)));
        when(bloqueDao.lineas(41)).thenReturn(List.of(lineas));
    }

    @Test void recibirSoloLasLineasDeLaListaConLaRecepcionDeSiempre() {
        bloque41(lb(1, "en_camino"), lb(2, "en_camino"), lb(3, "recibido"));
        BloqueCompraService.Aplicada a = servicio.aplicar(41, BloqueCompraService.Accion.RECIBIR, List.of(v(1), v(2)));
        verify(compraDao).confirmarRecibido(1, AT);
        verify(compraDao).confirmarRecibido(2, AT);
        verify(compraDao, never()).confirmarRecibido(eq(3), any());
        assertEquals(new BloqueCompraService.Aplicada(41, List.of(lb(1, "en_camino"), lb(2, "en_camino"))), a);
        verify(bloqueDao, never()).borrarSiVacio(anyInt());
    }

    @Test void confirmarYCancelarUsanSuTransicionDeLinea() {
        bloque41(lb(1, "pendiente"), lb(2, "en_camino"));
        servicio.aplicar(41, BloqueCompraService.Accion.CONFIRMAR, List.of(v(1)));
        servicio.aplicar(41, BloqueCompraService.Accion.CANCELAR, List.of(v(2)));
        verify(compraDao).confirmar(1, AT);
        verify(compraDao).cancelar(2, AT);
    }

    @Test void otraVersionOtroEstadoOUnaLineaAjenaEs409SinTocarNada() {
        bloque41(lb(1, "en_camino"), lb(2, "recibido"));
        falla(HttpStatus.CONFLICT, MODIFICADO, () -> servicio.aplicar(41, BloqueCompraService.Accion.RECIBIR, List.of(new LineaVista(1, AT.plusSeconds(1)))));
        falla(HttpStatus.CONFLICT, MODIFICADO, () -> servicio.aplicar(41, BloqueCompraService.Accion.RECIBIR, List.of(v(1), v(2))));
        falla(HttpStatus.CONFLICT, MODIFICADO, () -> servicio.aplicar(41, BloqueCompraService.Accion.RECIBIR, List.of(v(9))));
        verify(compraDao, never()).confirmarRecibido(anyInt(), any());
    }

    @Test void unBloqueQueYaNoExisteEs409YUnaListaVaciaEs422() {
        falla(HttpStatus.CONFLICT, MODIFICADO, () -> servicio.aplicar(41, BloqueCompraService.Accion.CONFIRMAR, List.of(v(1))));
        falla(HttpStatus.UNPROCESSABLE_ENTITY, "No hay líneas a las que aplicar la acción.",
                () -> servicio.aplicar(41, BloqueCompraService.Accion.CONFIRMAR, List.of()));
    }

    @Test void borrarExigeElBloqueEnteroPendienteYLoBorra() {
        bloque41(lb(1, "pendiente"), lb(2, "pendiente"));
        when(bloqueDao.borrarSiVacio(41)).thenReturn(true);
        servicio.aplicar(41, BloqueCompraService.Accion.BORRAR, List.of(v(1), v(2)));
        verify(compraDao).borrarPendiente(1);
        verify(compraDao).borrarPendiente(2);
        verify(bloqueDao).borrarSiVacio(41);
    }

    @Test void borrarConUnaLineaNoPendienteEs409() {
        bloque41(lb(1, "pendiente"), lb(2, "en_camino"));
        falla(HttpStatus.CONFLICT, MODIFICADO, () -> servicio.aplicar(41, BloqueCompraService.Accion.BORRAR, List.of(v(1), v(2))));
        verify(compraDao, never()).borrarPendiente(anyInt());
    }

    @Test void borrarConOtraVersionEs409() {
        bloque41(lb(1, "pendiente"), lb(2, "pendiente"));
        falla(HttpStatus.CONFLICT, MODIFICADO, () -> servicio.aplicar(41, BloqueCompraService.Accion.BORRAR,
                List.of(v(1), new LineaVista(2, AT.plusSeconds(1)))));
        verify(compraDao, never()).borrarPendiente(anyInt());
    }

    @Test void borrarSiElBloqueNoQuedaVacioEs409() {
        bloque41(lb(1, "pendiente"), lb(2, "pendiente"));
        when(bloqueDao.borrarSiVacio(41)).thenReturn(false);
        falla(HttpStatus.CONFLICT, MODIFICADO, () -> servicio.aplicar(41, BloqueCompraService.Accion.BORRAR, List.of(v(1), v(2))));
    }

    @Test void separarSiElOrigenQuedaVacioEs409() {
        bloque41(lb(1, "pendiente"), lb(2, "pendiente"));
        when(bloqueDao.crear(2)).thenReturn(90);
        when(bloqueDao.borrarSiVacio(41)).thenReturn(true);
        falla(HttpStatus.CONFLICT, MODIFICADO, () -> servicio.separar(41, List.of(v(1))));
    }

    @Test void juntarSiElBloqueNoQuedaVacioEs409() {
        bloque41(lb(1, "pendiente"), lb(2, "pendiente"));
        when(bloqueDao.getById(40)).thenReturn(Optional.of(bloque(40, 2)));
        when(bloqueDao.borrarSiVacio(41)).thenReturn(false);
        falla(HttpStatus.CONFLICT, MODIFICADO, () -> servicio.juntar(41, 40, List.of(v(1), v(2))));
    }

    @Test void moverSiLaLineaYaNoEstaEnElOrigenLeidoEs409() {
        when(compraDao.getById(5)).thenReturn(Optional.of(linea("pendiente", 41)));
        when(bloqueDao.getById(40)).thenReturn(Optional.of(bloque(40, 2)));
        doThrow(new ResponseStatusException(HttpStatus.CONFLICT, "El pedido cambió de bloque, vuelve a cargar"))
                .when(compraDao).moverABloque(5, 41, 40);
        falla(HttpStatus.CONFLICT, "El pedido cambió de bloque, vuelve a cargar", () -> servicio.mover(5, new DestinoPedido(40), AT));
        verify(bloqueDao, never()).borrarSiVacio(anyInt());
    }

    @Test void borrarConUnaListaIncompletaEs409() {
        bloque41(lb(1, "pendiente"), lb(2, "pendiente"));
        falla(HttpStatus.CONFLICT, MODIFICADO, () -> servicio.aplicar(41, BloqueCompraService.Accion.BORRAR, List.of(v(1))));
        verify(compraDao, never()).borrarPendiente(anyInt());
    }

    // ── separar, juntar y nota ──
    @Test void separarCreaUnBloqueDelProveedorYMueveLasMarcadas() {
        bloque41(lb(1, "pendiente"), lb(2, "en_camino"), lb(3, "pendiente"));
        when(bloqueDao.crear(2)).thenReturn(90);
        assertEquals(new BloqueCompraService.Separacion(41, 90, 2, List.of(1, 2)), servicio.separar(41, List.of(v(1), v(2))));
        verify(compraDao).moverABloque(1, 41, 90);
        verify(compraDao).moverABloque(2, 41, 90);
        verify(compraDao, never()).moverABloque(eq(3), any(), anyInt());
    }

    @Test void separarTodasEs422YUnaCambiadaEs409() {
        bloque41(lb(1, "pendiente"), lb(2, "pendiente"));
        falla(HttpStatus.UNPROCESSABLE_ENTITY, "Deja al menos una línea en el bloque.", () -> servicio.separar(41, List.of(v(1), v(2))));
        falla(HttpStatus.CONFLICT, MODIFICADO, () -> servicio.separar(41, List.of(new LineaVista(1, AT.plusSeconds(1)))));
        verify(bloqueDao, never()).crear(anyInt());
    }

    @Test void juntarMueveTodoAlDestinoUneLasNotasYBorraElBloque() {
        when(bloqueDao.getById(41)).thenReturn(Optional.of(new BloqueCompraDAO.Bloque(41, 2, AT, "Ped. 2", false, AT)));
        when(bloqueDao.lineas(41)).thenReturn(List.of(lb(1, "pendiente"), lb(2, "en_camino")));
        when(bloqueDao.getById(40)).thenReturn(Optional.of(new BloqueCompraDAO.Bloque(40, 2, AT, "Ped. 1", false, AT)));
        when(bloqueDao.borrarSiVacio(41)).thenReturn(true);
        assertEquals(new BloqueCompraService.Union(41, 40, List.of(1, 2)), servicio.juntar(41, 40, List.of(v(1), v(2))));
        verify(compraDao).moverABloque(1, 41, 40);
        verify(compraDao).moverABloque(2, 41, 40);
        verify(bloqueDao).cambiarNota(40, "Ped. 1 · Ped. 2");
        verify(bloqueDao).borrarSiVacio(41);
    }

    @Test void juntarConsigoMismoConOtroProveedorOIncompletoNoTocaNada() {
        bloque41(lb(1, "pendiente"), lb(2, "pendiente"));
        when(bloqueDao.getById(50)).thenReturn(Optional.of(bloque(50, 3)));
        falla(HttpStatus.UNPROCESSABLE_ENTITY, "Elige otro bloque.", () -> servicio.juntar(41, 41, List.of(v(1), v(2))));
        falla(HttpStatus.UNPROCESSABLE_ENTITY, "El bloque 50 es de otro proveedor.", () -> servicio.juntar(41, 50, List.of(v(1), v(2))));
        falla(HttpStatus.CONFLICT, MODIFICADO, () -> servicio.juntar(41, 40, List.of(v(1))));
        verify(compraDao, never()).moverABloque(anyInt(), any(), anyInt());
    }

    @Test void unirNotas() {
        assertEquals("A · B", BloqueCompraService.unirNotas("A", "B"));
        assertEquals("B", BloqueCompraService.unirNotas(null, "B"));
        assertEquals("A", BloqueCompraService.unirNotas("A", " "));
        assertNull(BloqueCompraService.unirNotas(null, null));
        assertEquals(200, BloqueCompraService.unirNotas("x".repeat(150), "y".repeat(150)).length());
    }

    @Test void cambiarNotaRecortaGuardaYVaciaANulo() {
        when(bloqueDao.getById(41)).thenReturn(Optional.of(bloque(41, 2)));
        servicio.cambiarNota(41, "  Ped. 123  ", AT);
        servicio.cambiarNota(41, "   ", AT);
        verify(bloqueDao).cambiarNota(41, "Ped. 123");
        verify(bloqueDao).cambiarNota(41, null);
    }

    @Test void notaLargaEs422YConOtraVersionEs409() {
        when(bloqueDao.getById(41)).thenReturn(Optional.of(bloque(41, 2)));
        falla(HttpStatus.UNPROCESSABLE_ENTITY, "La nota no puede superar los 200 caracteres.", () -> servicio.cambiarNota(41, "x".repeat(201), AT));
        falla(HttpStatus.CONFLICT, MODIFICADO, () -> servicio.cambiarNota(41, "Ped.", AT.plusSeconds(1)));
        falla(HttpStatus.CONFLICT, MODIFICADO, () -> servicio.cambiarNota(42, "Ped.", AT));
        verify(bloqueDao, never()).cambiarNota(anyInt(), any());
    }
}
