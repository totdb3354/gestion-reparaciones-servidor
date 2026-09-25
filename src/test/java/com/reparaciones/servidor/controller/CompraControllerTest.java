package com.reparaciones.servidor.controller;

import com.reparaciones.servidor.dao.ComponenteDAO;
import com.reparaciones.servidor.dao.CompraComponenteDAO;
import com.reparaciones.servidor.dao.LogDAO;
import com.reparaciones.servidor.dao.ProveedorDAO;
import com.reparaciones.servidor.dao.TipoCambioDAO;
import com.reparaciones.servidor.model.CompraComponente;
import com.reparaciones.servidor.model.Proveedor;
import com.reparaciones.servidor.security.UsuarioPrincipal;
import com.reparaciones.servidor.service.ConversionEur;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

/** CompraController (spec 4b §4.1-4.3): log de confirmar-alterado, 422 de alta, edición y recepción y precioEur. */
class CompraControllerTest {

    private static final LocalDateTime AT = LocalDateTime.of(2026, 9, 25, 10, 0);

    private final CompraComponenteDAO dao = mock(CompraComponenteDAO.class);
    private final LogDAO logDao = mock(LogDAO.class);
    private final ComponenteDAO componenteDao = mock(ComponenteDAO.class);
    private final ProveedorDAO proveedorDao = mock(ProveedorDAO.class);
    private final TipoCambioDAO tipoCambio = mock(TipoCambioDAO.class);
    private final CompraController ctl = new CompraController(dao, logDao, componenteDao, proveedorDao,
            new ConversionEur(tipoCambio));
    private final UsuarioPrincipal super7 = new UsuarioPrincipal(7, "tecnico1", "", "SUPERTECNICO", 3);

    CompraControllerTest() {
        when(componenteDao.getBasico(1)).thenReturn(Optional.of(new ComponenteDAO.Basico(1, 1, "lcd-x-negro", true)));
        when(componenteDao.getBasico(9)).thenReturn(Optional.of(new ComponenteDAO.Basico(9, 9, "bat-x", false)));
        when(proveedorDao.getById(2)).thenReturn(Optional.of(new Proveedor(2, "Proveedor A", true, "EUR", null, "COMPONENTES")));
        when(proveedorDao.getById(8)).thenReturn(Optional.of(new Proveedor(8, "ACME", false, "EUR", null, "COMPONENTES")));
        when(componenteDao.getTipoById(1)).thenReturn("lcd-x-negro");
        when(proveedorDao.getNombreById(2)).thenReturn("Proveedor A");
    }

    /** precioEur = precioUnidad: con EUR es lo que calcula el servidor desde la Task 3, así que estos tests no cambian. */
    private static CompraController.InsertarRequest alta(int idCom, int idProv, int cantidad, double precio, String divisa) {
        return new CompraController.InsertarRequest(idCom, idProv, cantidad, false, precio, divisa, precio);
    }

    private static CompraController.EditarRequest edicion(int idProv, int cantidad, double precio, String divisa) {
        return new CompraController.EditarRequest(idProv, cantidad, false, precio, divisa, precio, AT);
    }

    private static CompraComponente pedido(String estado, int cantidad, Integer recibida) {
        return new CompraComponente(5, 1, "lcd-x-negro", 2, "Proveedor A", cantidad, recibida, false,
                AT, null, 10.0, "EUR", 10.0, estado, AT);
    }

    private static String falla422(Runnable accion) {
        ResponseStatusException e = assertThrows(ResponseStatusException.class, accion::run);
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, e.getStatusCode());
        return e.getReason();
    }

    private void nadaEscritoNiRegistrado() {
        verify(dao, never()).insertar(anyInt(), anyInt(), anyInt(), anyBoolean(), anyDouble(), any(), anyDouble());
        verify(dao, never()).editar(anyInt(), anyInt(), anyInt(), anyBoolean(), anyDouble(), any(), anyDouble(), any());
        verify(dao, never()).confirmarParcial(anyInt(), anyInt(), any());
        verify(dao, never()).recibirResto(anyInt(), anyInt(), any());
        verifyNoInteractions(logDao);
    }

    // ── Task 1: log de confirmar-alterado ──
    @Test void confirmarAlteradoRegistraLog() {
        ctl.confirmarAlterado(5, new CompraController.UpdatedAtRequest(AT), super7);
        verify(dao).confirmarAlterado(5, AT);
        verify(logDao).insertar(7, "CONFIRMAR_ALTERADO", "ID_COMPRA: 5");
    }

    @Test void confirmarAlteradoCon409NoRegistraLog() {
        doThrow(new ResponseStatusException(HttpStatus.CONFLICT, "El pedido ya no está en recepción parcial"))
                .when(dao).confirmarAlterado(5, AT);
        assertThrows(ResponseStatusException.class,
                () -> ctl.confirmarAlterado(5, new CompraController.UpdatedAtRequest(AT), super7));
        verifyNoInteractions(logDao);
    }

    // ── Task 2: alta ──
    @Test void altaConCantidadNoPositivaEs422() {
        assertEquals("Cantidad no válida (debe ser > 0).", falla422(() -> ctl.insertar(alta(1, 2, 0, 10.0, "EUR"), super7)));
        assertEquals("Cantidad no válida (debe ser > 0).", falla422(() -> ctl.insertar(alta(1, 2, -1, 10.0, "EUR"), super7)));
        nadaEscritoNiRegistrado();
    }

    @Test void altaConPrecioNegativoEs422() {
        assertEquals("Precio no válido.", falla422(() -> ctl.insertar(alta(1, 2, 3, -0.01, "EUR"), super7)));
        nadaEscritoNiRegistrado();
    }

    /** Minor 8 de la revisión final: 1e400 lo parsea Jackson como Infinity; debe rechazarse igual que un negativo. */
    @Test void altaConPrecioInfinitoEs422() {
        assertEquals("Precio no válido.",
                falla422(() -> ctl.insertar(alta(1, 2, 3, Double.POSITIVE_INFINITY, "EUR"), super7)));
        nadaEscritoNiRegistrado();
    }

    @Test void altaConDivisaDesconocidaEs422() {
        assertEquals("Divisa no válida (EUR o USD).", falla422(() -> ctl.insertar(alta(1, 2, 3, 10.0, "GBP"), super7)));
        assertEquals("Divisa no válida (EUR o USD).", falla422(() -> ctl.insertar(alta(1, 2, 3, 10.0, null), super7)));
        nadaEscritoNiRegistrado();
    }

    @Test void altaConComponenteInactivoOInexistenteEs422() {
        assertEquals("El componente no está activo.", falla422(() -> ctl.insertar(alta(9, 2, 3, 10.0, "EUR"), super7)));
        assertEquals("El componente no está activo.", falla422(() -> ctl.insertar(alta(99, 2, 3, 10.0, "EUR"), super7)));
        nadaEscritoNiRegistrado();
    }

    @Test void altaConProveedorInactivoOInexistenteEs422() {
        assertEquals("El proveedor no está activo.", falla422(() -> ctl.insertar(alta(1, 8, 3, 10.0, "EUR"), super7)));
        assertEquals("El proveedor no está activo.", falla422(() -> ctl.insertar(alta(1, 99, 3, 10.0, "EUR"), super7)));
        nadaEscritoNiRegistrado();
    }

    @Test void altaValidaNormalizaLaDivisaYEscribe() {
        ctl.insertar(alta(1, 2, 3, 0.0, " eur "), super7);
        verify(dao).insertar(1, 2, 3, false, 0.0, "EUR", 0.0);
        verify(logDao).insertar(7, "CREAR_PEDIDO", "COMPONENTE: lcd-x-negro, PROVEEDOR: Proveedor A, CANT: 3");
    }

    // ── Task 2: edición ──
    @Test void editarValidaCantidadPrecioDivisaYProveedor() {
        assertEquals("Cantidad no válida (debe ser > 0).", falla422(() -> ctl.editar(5, edicion(2, 0, 10.0, "EUR"), super7)));
        assertEquals("Precio no válido.", falla422(() -> ctl.editar(5, edicion(2, 3, -1.0, "EUR"), super7)));
        assertEquals("Divisa no válida (EUR o USD).", falla422(() -> ctl.editar(5, edicion(2, 3, 10.0, "CNY"), super7)));
        assertEquals("El proveedor no está activo.", falla422(() -> ctl.editar(5, edicion(8, 3, 10.0, "EUR"), super7)));
        nadaEscritoNiRegistrado();
    }

    @Test void editarUnRecibidoCambiandoLaCantidadEs422() {
        when(dao.getById(5)).thenReturn(Optional.of(pedido("recibido", 4, 4)));
        assertEquals("No se puede cambiar la cantidad de un pedido recibido.",
                falla422(() -> ctl.editar(5, edicion(2, 5, 10.0, "EUR"), super7)));
        nadaEscritoNiRegistrado();
    }

    @Test void editarUnRecibidoSinCambiarLaCantidadEscribe() {
        when(dao.getById(5)).thenReturn(Optional.of(pedido("recibido", 4, 4)));
        ctl.editar(5, edicion(2, 4, 12.5, "EUR"), super7);
        verify(dao).editar(5, 2, 4, false, 12.5, "EUR", 12.5, AT);
        verify(logDao).insertar(7, "EDITAR_PEDIDO", "ID_COMPRA: 5");
    }

    /** Important 4 de la revisión final (alcance de P2): en_camino sí permite cambiar la cantidad, solo
     *  recibido la bloquea. */
    @Test void editarEnCaminoConCantidadDistintaEscribe() {
        when(dao.getById(5)).thenReturn(Optional.of(pedido("en_camino", 4, null)));
        ctl.editar(5, edicion(2, 7, 12.5, "EUR"), super7);
        verify(dao).editar(5, 2, 7, false, 12.5, "EUR", 12.5, AT);
        verify(logDao).insertar(7, "EDITAR_PEDIDO", "ID_COMPRA: 5");
    }

    /** Important 4 (alcance de P2): dao.getById(5) sin stubbear (Optional.empty() por defecto de Mockito),
     *  un id sin compra guardada no bloquea la edición: cantidadEditable se salta y se llega a dao.editar. */
    @Test void editarConGetByIdVacioNoBloqueaYEscribe() {
        ctl.editar(5, edicion(2, 4, 12.5, "EUR"), super7);
        verify(dao).editar(5, 2, 4, false, 12.5, "EUR", 12.5, AT);
        verify(logDao).insertar(7, "EDITAR_PEDIDO", "ID_COMPRA: 5");
    }

    // ── Task 2: recepción parcial y resto ──
    @Test void parcialFueraDeRangoEs422() {
        when(dao.getById(5)).thenReturn(Optional.of(pedido("en_camino", 5, null)));
        for (int cantidad : new int[]{0, -1, 5, 6}) {
            assertEquals("La cantidad debe ser mayor que 0 y menor que 5.", falla422(() ->
                    ctl.confirmarParcial(5, new CompraController.ConfirmarParcialRequest(cantidad, AT), super7)));
        }
        nadaEscritoNiRegistrado();
    }

    @Test void parcialDentroDeRangoEscribe() {
        when(dao.getById(5)).thenReturn(Optional.of(pedido("en_camino", 5, null)));
        ctl.confirmarParcial(5, new CompraController.ConfirmarParcialRequest(1, AT), super7);
        ctl.confirmarParcial(5, new CompraController.ConfirmarParcialRequest(4, AT), super7);
        verify(dao).confirmarParcial(5, 1, AT);
        verify(dao).confirmarParcial(5, 4, AT);
    }

    @Test void restoConCeroONegativoEs422() {
        when(dao.getById(5)).thenReturn(Optional.of(pedido("parcial", 5, 2)));
        assertEquals("La cantidad debe ser mayor que 0.", falla422(() ->
                ctl.recibirResto(5, new CompraController.RecibirRestoRequest(0, AT), super7)));
        assertEquals("La cantidad debe ser mayor que 0.", falla422(() ->
                ctl.recibirResto(5, new CompraController.RecibirRestoRequest(-2, AT), super7)));
        nadaEscritoNiRegistrado();
    }

    @Test void restoQueSuperaLoPedidoEs422YElJustoEscribe() {
        when(dao.getById(5)).thenReturn(Optional.of(pedido("parcial", 5, 2)));
        assertEquals("No puedes recibir más de lo pedido. Faltan 3 unidad(es).", falla422(() ->
                ctl.recibirResto(5, new CompraController.RecibirRestoRequest(4, AT), super7)));
        nadaEscritoNiRegistrado();
        ctl.recibirResto(5, new CompraController.RecibirRestoRequest(3, AT), super7);
        verify(dao).recibirResto(5, 3, AT);
        verify(logDao).insertar(7, "RECIBIR_RESTO", "ID_COMPRA: 5");
    }

    // ── Task 3: precioEur lo calcula el servidor ──
    @Test void altaEnDolaresCalculaElEurYIgnoraElDeLaPeticion() {
        when(tipoCambio.getTasa("USD")).thenReturn(1.1367);
        ctl.insertar(new CompraController.InsertarRequest(1, 2, 3, false, 10.0, "USD", 999.0), super7);
        verify(dao).insertar(1, 2, 3, false, 10.0, "USD", 8.8);
    }

    @Test void editarEnDolaresCalculaElEurYIgnoraElDeLaPeticion() {
        when(tipoCambio.getTasa("USD")).thenReturn(1.1367);
        ctl.editar(5, new CompraController.EditarRequest(2, 4, false, 10.0, "usd", 999.0, AT), super7);
        verify(dao).editar(5, 2, 4, false, 10.0, "USD", 8.8, AT);
    }

    @Test void sinTasaEs503SinEscribirNiRegistrar() {
        when(tipoCambio.getTasa("USD")).thenThrow(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "No se pudo obtener el tipo de cambio de USD. Inténtalo de nuevo."));
        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> ctl.insertar(new CompraController.InsertarRequest(1, 2, 3, false, 10.0, "USD", 999.0), super7));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, e.getStatusCode());
        nadaEscritoNiRegistrado();
    }

    /** Important 4 de la revisión final: el 503 de la tasa no estaba probado en editar (solo en insertar). */
    @Test void editarSinTasaEs503SinEscribirNiRegistrar() {
        when(tipoCambio.getTasa("USD")).thenThrow(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "No se pudo obtener el tipo de cambio de USD. Inténtalo de nuevo."));
        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> ctl.editar(5, new CompraController.EditarRequest(2, 4, false, 10.0, "USD", 999.0, AT), super7));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, e.getStatusCode());
        nadaEscritoNiRegistrado();
    }
}
