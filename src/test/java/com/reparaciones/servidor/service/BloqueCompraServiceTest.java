package com.reparaciones.servidor.service;

import com.reparaciones.servidor.dao.BloqueCompraDAO;
import com.reparaciones.servidor.dao.CompraComponenteDAO;
import com.reparaciones.servidor.model.CompraComponente;
import com.reparaciones.servidor.model.DestinoPedido;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
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
        verify(compraDao).moverABloque(5, 40);
        verify(bloqueDao).borrarSiVacio(41);
        assertEquals(new BloqueCompraService.Movimiento(5, "pendiente", 41, 40, false, 2, false), m);
    }

    @Test void moverABloqueNuevoCreaUnoDelMismoProveedor() {
        when(compraDao.getById(5)).thenReturn(Optional.of(linea("en_camino", 41)));
        when(bloqueDao.contarLineas(41)).thenReturn(2);
        when(bloqueDao.crear(2)).thenReturn(90);
        assertEquals(new BloqueCompraService.Movimiento(5, "en_camino", 41, 90, true, 2, false),
                servicio.mover(5, new DestinoPedido(null), AT));
        verify(compraDao).moverABloque(5, 90);
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
        verify(compraDao, never()).moverABloque(anyInt(), anyInt());
        verify(bloqueDao, never()).crear(anyInt());
    }

    @Test void moverAUnBloqueDeOtroProveedorEs422() {
        when(compraDao.getById(5)).thenReturn(Optional.of(linea("pendiente", 41)));
        when(bloqueDao.getById(40)).thenReturn(Optional.of(bloque(40, 3)));
        falla(HttpStatus.UNPROCESSABLE_ENTITY, "El bloque 40 es de otro proveedor.", () -> servicio.mover(5, new DestinoPedido(40), AT));
        verify(compraDao, never()).moverABloque(anyInt(), anyInt());
    }

    @Test void moverConOtraVersionEs409YUnaLineaInexistenteEs404() {
        when(compraDao.getById(5)).thenReturn(Optional.of(linea("pendiente", 41)));
        falla(HttpStatus.CONFLICT, "Dato modificado por otro usuario", () -> servicio.mover(5, new DestinoPedido(40), AT.plusSeconds(1)));
        falla(HttpStatus.NOT_FOUND, "El pedido no existe", () -> servicio.mover(6, new DestinoPedido(40), AT));
        verify(compraDao, never()).moverABloque(anyInt(), anyInt());
    }

    // ── editar cambiando el proveedor ──
    @Test void editarUnaPendienteAOtroProveedorVaASuAbierto() {
        when(compraDao.getById(5)).thenReturn(Optional.of(linea("pendiente", 41)));
        when(bloqueDao.abiertoMasReciente(3)).thenReturn(Optional.of(60));
        BloqueCompraService.Movimiento m = servicio.editarCambiandoProveedor(5, 3, 4, false, 12.0, "EUR", 12.0, AT, null);
        InOrder orden = inOrder(compraDao);
        orden.verify(compraDao).editar(5, 3, 4, false, 12.0, "EUR", 12.0, AT);
        orden.verify(compraDao).moverABloque(5, 60);
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
        verify(compraDao, never()).moverABloque(anyInt(), anyInt());
    }

    // ── borrar una línea pendiente ──
    @Test void borrarLaUltimaLineaBorraSuBloque() {
        when(compraDao.getById(5)).thenReturn(Optional.of(linea("pendiente", 41)));
        when(bloqueDao.borrarSiVacio(41)).thenReturn(true);
        assertEquals(Optional.of(41), servicio.borrarLineaPendiente(5));
        verify(compraDao).borrarPendiente(5);
    }

    @Test void borrarUnaLineaDeUnBloqueConMasNoLoBorra() {
        when(compraDao.getById(5)).thenReturn(Optional.of(linea("pendiente", 41)));
        assertEquals(Optional.empty(), servicio.borrarLineaPendiente(5));
        verify(compraDao).borrarPendiente(5);
    }
}
