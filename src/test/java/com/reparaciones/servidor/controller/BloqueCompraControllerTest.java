package com.reparaciones.servidor.controller;

import com.reparaciones.servidor.dao.BloqueCompraDAO;
import com.reparaciones.servidor.dao.LogDAO;
import com.reparaciones.servidor.dao.ProveedorDAO;
import com.reparaciones.servidor.model.DestinoPedido;
import com.reparaciones.servidor.model.LineaVista;
import com.reparaciones.servidor.model.TipoLinea;
import com.reparaciones.servidor.security.UsuarioPrincipal;
import com.reparaciones.servidor.service.BloqueCompraService;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

/** Logs de BloqueCompraController (0.9.9, spec «Pedidos por bloques» §5.4) con el servicio mockeado. Los roles los
 *  prueba RolesBloquesCompraTest con la cadena de seguridad real. */
class BloqueCompraControllerTest {

    static final LocalDateTime AT = LocalDateTime.of(2026, 10, 9, 10, 0, 0);
    final BloqueCompraService servicio = mock(BloqueCompraService.class);
    final LogDAO logDao = mock(LogDAO.class);
    final ProveedorDAO proveedorDao = mock(ProveedorDAO.class);
    final BloqueCompraController ctl = new BloqueCompraController(servicio, new RegistroBloques(logDao, proveedorDao), logDao);
    final UsuarioPrincipal super7 = new UsuarioPrincipal(7, "tecnico1", "", "SUPERTECNICO", 3);

    BloqueCompraControllerTest() {
        when(proveedorDao.getNombreById(2)).thenReturn("Proveedor A");
    }

    @Test void moverRegistraElMovimiento() {
        when(servicio.mover(5, new DestinoPedido(40), AT))
                .thenReturn(new BloqueCompraService.Movimiento(5, "pendiente", 41, 40, false, 2, false));
        ctl.mover(5, new BloqueCompraController.MoverRequest(40, AT), super7);
        verify(logDao).insertar(7, "MOVER_LINEA_BLOQUE", "ID_COMPRA: 5, ESTADO: pendiente, DE: 41 A: 40");
        verifyNoMoreInteractions(logDao);
    }

    @Test void moverABloqueNuevoQueVaciaElOrigenRegistraLosTresEnOrden() {
        when(servicio.mover(5, new DestinoPedido(null), AT))
                .thenReturn(new BloqueCompraService.Movimiento(5, "en_camino", 41, 90, true, 2, true));
        ctl.mover(5, new BloqueCompraController.MoverRequest(null, AT), super7);
        InOrder orden = inOrder(logDao);
        orden.verify(logDao).insertar(7, "CREAR_BLOQUE", "BLOQUE: 90, PROVEEDOR: Proveedor A");
        orden.verify(logDao).insertar(7, "MOVER_LINEA_BLOQUE", "ID_COMPRA: 5, ESTADO: en_camino, DE: 41 A: 90");
        orden.verify(logDao).insertar(7, "BORRAR_BLOQUE", "BLOQUE: 41 (se quedó sin líneas)");
    }

    @Test void unFalloDelServicioNoRegistraNada() {
        when(servicio.mover(5, new DestinoPedido(40), AT))
                .thenThrow(new ResponseStatusException(HttpStatus.CONFLICT, "Dato modificado por otro usuario"));
        assertThrows(ResponseStatusException.class,
                () -> ctl.mover(5, new BloqueCompraController.MoverRequest(40, AT), super7));
        verifyNoInteractions(logDao);
    }

    static BloqueCompraDAO.LineaBloque lb(int id, String tipo, int cantidad) {
        return new BloqueCompraDAO.LineaBloque(id, tipo, cantidad, "en_camino", AT);
    }

    @Test void recibirRegistraCadaLineaConSuStockYElBloque() {
        List<LineaVista> vistas = List.of(new LineaVista(1, AT, null), new LineaVista(2, AT, null));
        when(servicio.aplicar(41, BloqueCompraService.Accion.RECIBIR, vistas))
                .thenReturn(new BloqueCompraService.Aplicada(41, List.of(lb(1, "lcd-x-negro", 3), lb(2, "bat-x", 1))));
        ctl.recibir(41, new BloqueCompraController.LineasRequest(vistas), super7);
        InOrder orden = inOrder(logDao);
        orden.verify(logDao).insertar(7, "RECIBIR_PEDIDO", "ID_COMPRA: 1, COMPONENTE: lcd-x-negro, CANT: 3");
        orden.verify(logDao).insertar(7, "RECIBIR_PEDIDO", "ID_COMPRA: 2, COMPONENTE: bat-x, CANT: 1");
        orden.verify(logDao).insertar(7, "RECIBIR_BLOQUE", "BLOQUE: 41, ID_COMPRA: 1, 2");
    }

    @Test void confirmarCancelarYBorrarRegistranSusLogs() {
        List<LineaVista> vistas = List.of(new LineaVista(1, AT, null));
        BloqueCompraService.Aplicada una = new BloqueCompraService.Aplicada(41, List.of(lb(1, "lcd-x-negro", 3)));
        when(servicio.aplicar(eq(41), any(), eq(vistas))).thenReturn(una);
        ctl.confirmar(41, new BloqueCompraController.LineasRequest(vistas), super7);
        ctl.cancelar(41, new BloqueCompraController.LineasRequest(vistas), super7);
        ctl.borrar(41, new BloqueCompraController.LineasRequest(vistas), super7);
        verify(logDao).insertar(7, "CONFIRMAR_PEDIDO", "ID_COMPRA: 1");
        verify(logDao).insertar(7, "CONFIRMAR_BLOQUE", "BLOQUE: 41, ID_COMPRA: 1");
        verify(logDao).insertar(7, "CANCELAR_PEDIDO", "ID_COMPRA: 1");
        verify(logDao).insertar(7, "CANCELAR_BLOQUE", "BLOQUE: 41, ID_COMPRA: 1");
        verify(logDao).insertar(7, "BORRAR_PEDIDO", "ID_COMPRA: 1");
        verify(logDao).insertar(7, "BORRAR_BLOQUE", "BLOQUE: 41, ID_COMPRA: 1");
    }

    @Test void separarRegistraElBloqueNuevoYLaSeparacion() {
        List<LineaVista> vistas = List.of(new LineaVista(1, AT, null), new LineaVista(2, AT, null));
        when(servicio.separar(41, vistas)).thenReturn(new BloqueCompraService.Separacion(41, 90, 2, List.of(1, 2)));
        assertEquals(90, ctl.separar(41, new BloqueCompraController.LineasRequest(vistas), super7).idBloque());
        verify(logDao).insertar(7, "CREAR_BLOQUE", "BLOQUE: 90, PROVEEDOR: Proveedor A");
        verify(logDao).insertar(7, "SEPARAR_BLOQUE", "DE: 41 A: 90, ID_COMPRA: 1, 2");
    }

    @Test void juntarYNotaRegistranSusLogs() {
        List<LineaVista> vistas = List.of(new LineaVista(1, AT, null));
        when(servicio.juntar(41, 40, vistas)).thenReturn(new BloqueCompraService.Union(41, 40, List.of(1)));
        ctl.juntar(41, new BloqueCompraController.JuntarRequest(40, vistas), super7);
        ctl.nota(41, new BloqueCompraController.NotaRequest("Ped. 123", AT), super7);
        verify(servicio).cambiarNota(41, "Ped. 123", AT);
        verify(logDao).insertar(7, "JUNTAR_BLOQUE", "DE: 41 A: 40, ID_COMPRA: 1");
        verify(logDao).insertar(7, "EDITAR_NOTA_BLOQUE", "BLOQUE: 41");
    }

    // ── 0.9.15: líneas de otro ──
    static BloqueCompraDAO.LineaBloque lo(int id, String concepto, int cantidad) {
        return new BloqueCompraDAO.LineaBloque(id, concepto, cantidad, "en_camino", AT, TipoLinea.OTRO);
    }

    @Test void recibirUnBloqueMixtoRegistraCadaLineaConSuAccionYElBloqueConLosIdsPorTipo() {
        List<LineaVista> vistas = List.of(new LineaVista(1, AT, TipoLinea.PIEZA), new LineaVista(88, AT, TipoLinea.OTRO));
        when(servicio.aplicar(41, BloqueCompraService.Accion.RECIBIR, vistas))
                .thenReturn(new BloqueCompraService.Aplicada(41, List.of(lb(1, "lcd-x-negro", 3), lo(88, "Cinta de embalar", 2))));
        ctl.recibir(41, new BloqueCompraController.LineasRequest(vistas), super7);
        InOrder orden = inOrder(logDao);
        orden.verify(logDao).insertar(7, "RECIBIR_PEDIDO", "ID_COMPRA: 1, COMPONENTE: lcd-x-negro, CANT: 3");
        orden.verify(logDao).insertar(7, "RECIBIR_PEDIDO_OTRO", "ID_COMPRA_OTRO: 88, CONCEPTO: Cinta de embalar, CANT: 2");
        orden.verify(logDao).insertar(7, "RECIBIR_BLOQUE", "BLOQUE: 41, ID_COMPRA: 1, ID_COMPRA_OTRO: 88");
    }

    @Test void confirmarCancelarYBorrarUnOtroUsanLosLogsDeOtros() {
        List<LineaVista> vistas = List.of(new LineaVista(88, AT, TipoLinea.OTRO));
        BloqueCompraService.Aplicada uno = new BloqueCompraService.Aplicada(41, List.of(lo(88, "Cinta de embalar", 2)));
        when(servicio.aplicar(eq(41), any(), eq(vistas))).thenReturn(uno);
        ctl.confirmar(41, new BloqueCompraController.LineasRequest(vistas), super7);
        ctl.cancelar(41, new BloqueCompraController.LineasRequest(vistas), super7);
        ctl.borrar(41, new BloqueCompraController.LineasRequest(vistas), super7);
        verify(logDao).insertar(7, "CONFIRMAR_PEDIDO_OTRO", "ID_COMPRA_OTRO: 88");
        verify(logDao).insertar(7, "CONFIRMAR_BLOQUE", "BLOQUE: 41, ID_COMPRA_OTRO: 88");
        verify(logDao).insertar(7, "CANCELAR_PEDIDO_OTRO", "ID_COMPRA_OTRO: 88");
        verify(logDao).insertar(7, "CANCELAR_BLOQUE", "BLOQUE: 41, ID_COMPRA_OTRO: 88");
        verify(logDao).insertar(7, "BORRAR_PEDIDO_OTRO", "ID_COMPRA_OTRO: 88");
        verify(logDao).insertar(7, "BORRAR_BLOQUE", "BLOQUE: 41, ID_COMPRA_OTRO: 88");
    }

    @Test void separarYJuntarConOtrosListanLosIdsPorTipo() {
        List<LineaVista> vistas = List.of(new LineaVista(1, AT, null), new LineaVista(88, AT, TipoLinea.OTRO));
        when(servicio.separar(41, vistas)).thenReturn(new BloqueCompraService.Separacion(41, 90, 2, List.of(1), List.of(88)));
        when(servicio.juntar(41, 40, vistas)).thenReturn(new BloqueCompraService.Union(41, 40, List.of(), List.of(88)));
        ctl.separar(41, new BloqueCompraController.LineasRequest(vistas), super7);
        ctl.juntar(41, new BloqueCompraController.JuntarRequest(40, vistas), super7);
        verify(logDao).insertar(7, "SEPARAR_BLOQUE", "DE: 41 A: 90, ID_COMPRA: 1, ID_COMPRA_OTRO: 88");
        verify(logDao).insertar(7, "JUNTAR_BLOQUE", "DE: 41 A: 40, ID_COMPRA_OTRO: 88");
    }

    @Test void conIdsSoloPoneLaParteDeCadaTipoSiTieneIds() {
        assertEquals("BLOQUE: 41, ID_COMPRA: 12, 13", BloqueCompraController.conIds("BLOQUE: 41", List.of(12, 13), List.of()));
        assertEquals("BLOQUE: 41, ID_COMPRA_OTRO: 88", BloqueCompraController.conIds("BLOQUE: 41", List.of(), List.of(88)));
        assertEquals("BLOQUE: 41, ID_COMPRA: 12, ID_COMPRA_OTRO: 88, 89",
                BloqueCompraController.conIds("BLOQUE: 41", List.of(12), List.of(88, 89)));
    }
}
