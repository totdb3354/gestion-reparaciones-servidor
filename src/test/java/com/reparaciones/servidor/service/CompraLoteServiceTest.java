package com.reparaciones.servidor.service;

import com.reparaciones.servidor.dao.CompraComponenteDAO;
import com.reparaciones.servidor.dao.CompraOtroDAO;
import com.reparaciones.servidor.dao.ReparacionComponenteDAO;
import com.reparaciones.servidor.dao.SolicitudStockDAO;
import com.reparaciones.servidor.model.DestinoPedido;
import com.reparaciones.servidor.model.LoteCompras;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.dao.DataAccessResourceFailureException;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Reglas del guardado por lotes de pedidos con las DAO mockeadas (sin transacción real; la frontera transaccional
 *  la prueba CompraLoteServiceTransaccionTest). */
class CompraLoteServiceTest {

    private final CompraComponenteDAO compraDao = mock(CompraComponenteDAO.class);
    private final CompraOtroDAO compraOtroDao = mock(CompraOtroDAO.class);
    private final ReparacionComponenteDAO reparacionComponenteDao = mock(ReparacionComponenteDAO.class);
    private final SolicitudStockDAO solicitudStockDao = mock(SolicitudStockDAO.class);
    private final DestinoBloque destinoBloque = mock(DestinoBloque.class);
    private final CompraLoteService servicio =
            new CompraLoteService(compraDao, compraOtroDao, reparacionComponenteDao, solicitudStockDao, destinoBloque);

    CompraLoteServiceTest() {
        when(destinoBloque.resolver(eq(2), isNull(), eq(true))).thenReturn(new DestinoBloque.Destino(90, false));
    }

    private static CompraLoteService.LineaCompra linea(int idCom, int cantidad) {
        return new CompraLoteService.LineaCompra(idCom, 2, cantidad, false, 10.0, "USD", 8.8);
    }

    private static CompraLoteService.LineaCompra lineaDe(int idProv, int idCom) {
        return new CompraLoteService.LineaCompra(idCom, idProv, 1, false, 10.0, "EUR", 10.0);
    }

    @Test void insertaCadaLineaEnOrdenEnSuBloqueYDevuelveSusIds() {
        when(compraDao.insertar(1, 2, 3, false, 10.0, "USD", 8.8, 90)).thenReturn(41);
        when(compraDao.insertar(7, 2, 1, false, 10.0, "USD", 8.8, 90)).thenReturn(42);

        CompraLoteService.ResultadoLote r = servicio.guardarCompras(List.of(linea(1, 3), linea(7, 1)), List.of(), List.of(), Map.of());

        assertEquals(List.of(41, 42), r.respuesta().idsCreados());
        assertEquals(List.of(), r.bloquesCreados());
        InOrder orden = inOrder(compraDao);
        orden.verify(compraDao).insertar(1, 2, 3, false, 10.0, "USD", 8.8, 90);
        orden.verify(compraDao).insertar(7, 2, 1, false, 10.0, "USD", 8.8, 90);
        verifyNoInteractions(reparacionComponenteDao, solicitudStockDao, compraOtroDao);
    }

    /** El destino se resuelve una vez por proveedor, con lo que eligió la web o con la regla por defecto. */
    @Test void cadaProveedorVaASuDestinoYLosBloquesNuevosSeDevuelven() {
        when(destinoBloque.resolver(eq(3), eq(new DestinoPedido(null)), eq(true))).thenReturn(new DestinoBloque.Destino(91, true));

        CompraLoteService.ResultadoLote r = servicio.guardarCompras(
                List.of(lineaDe(2, 1), lineaDe(3, 4), lineaDe(2, 7)), List.of(), List.of(), Map.of(3, new DestinoPedido(null)));

        verify(compraDao).insertar(1, 2, 1, false, 10.0, "EUR", 10.0, 90);
        verify(compraDao).insertar(4, 3, 1, false, 10.0, "EUR", 10.0, 91);
        verify(compraDao).insertar(7, 2, 1, false, 10.0, "EUR", 10.0, 90);
        verify(destinoBloque, times(1)).resolver(eq(2), isNull(), eq(true));
        verify(destinoBloque, times(1)).resolver(eq(3), eq(new DestinoPedido(null)), eq(true));
        assertEquals(List.of(new CompraLoteService.BloqueCreado(91, 3)), r.bloquesCreados());
    }

    @Test void marcaLasSolicitudesComoGestionadasDespuesDeInsertar() {
        when(compraDao.insertar(anyInt(), anyInt(), anyInt(), anyBoolean(), anyDouble(), any(), anyDouble(), anyInt())).thenReturn(41);

        servicio.guardarCompras(List.of(linea(1, 2)), List.of(11, 12), List.of(21), Map.of());

        InOrder orden = inOrder(compraDao, reparacionComponenteDao, solicitudStockDao);
        orden.verify(compraDao).insertar(1, 2, 2, false, 10.0, "USD", 8.8, 90);
        orden.verify(reparacionComponenteDao).actualizarEstadoSolicitud(11, "GESTIONADA");
        orden.verify(reparacionComponenteDao).actualizarEstadoSolicitud(12, "GESTIONADA");
        orden.verify(solicitudStockDao).actualizarEstado(21, "GESTIONADA");
    }

    @Test void unFalloEnLaSegundaLineaSePropagaSinMarcarSolicitudes() {
        when(compraDao.insertar(anyInt(), anyInt(), anyInt(), anyBoolean(), anyDouble(), any(), anyDouble(), anyInt()))
                .thenReturn(41)
                .thenThrow(new DataAccessResourceFailureException("BD caída"));

        assertThrows(DataAccessResourceFailureException.class,
                () -> servicio.guardarCompras(List.of(linea(1, 1), linea(7, 1)), List.of(11), List.of(21), Map.of()));

        verifyNoInteractions(reparacionComponenteDao, solicitudStockDao);
    }

    // ── Task 5 (4b): otros pedidos ──
    private static CompraLoteService.LineaOtro otro(String concepto, int cantidad) {
        return new CompraLoteService.LineaOtro(2, concepto, cantidad, false, 1.5, "EUR", 1.5);
    }

    @Test void guardarOtrosInsertaCadaLineaEnOrdenYDevuelveSusIds() {
        when(compraOtroDao.insertar(2, "Cinta de embalar", 3, false, 1.5, "EUR", 1.5)).thenReturn(51);
        when(compraOtroDao.insertar(2, "Bolsas", 1, false, 1.5, "EUR", 1.5)).thenReturn(52);

        LoteCompras.Respuesta r = servicio.guardarOtros(List.of(otro("Cinta de embalar", 3), otro("Bolsas", 1)));

        assertEquals(List.of(51, 52), r.idsCreados());
        InOrder orden = inOrder(compraOtroDao);
        orden.verify(compraOtroDao).insertar(2, "Cinta de embalar", 3, false, 1.5, "EUR", 1.5);
        orden.verify(compraOtroDao).insertar(2, "Bolsas", 1, false, 1.5, "EUR", 1.5);
        verifyNoInteractions(compraDao, reparacionComponenteDao, solicitudStockDao, destinoBloque);
    }

    @Test void guardarOtrosPropagaUnFalloEnLaSegundaLinea() {
        when(compraOtroDao.insertar(anyInt(), any(), anyInt(), anyBoolean(), anyDouble(), any(), anyDouble()))
                .thenReturn(51)
                .thenThrow(new DataAccessResourceFailureException("BD caída"));
        assertThrows(DataAccessResourceFailureException.class,
                () -> servicio.guardarOtros(List.of(otro("Cinta de embalar", 1), otro("Bolsas", 1))));
    }
}
