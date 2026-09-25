package com.reparaciones.servidor.controller;

import com.reparaciones.servidor.dao.CompraOtroDAO;
import com.reparaciones.servidor.dao.LogDAO;
import com.reparaciones.servidor.dao.ProveedorDAO;
import com.reparaciones.servidor.model.CompraOtro;
import com.reparaciones.servidor.model.Proveedor;
import com.reparaciones.servidor.security.UsuarioPrincipal;
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

/** CompraOtroController (spec 4b §4.1-4.3): log de confirmar-alterado, 422 de alta, edición y recepción y precioEur. */
class CompraOtroControllerTest {

    private static final LocalDateTime AT = LocalDateTime.of(2026, 9, 25, 10, 0);
    private static final String CINTA = "Cinta de embalar";

    private final CompraOtroDAO dao = mock(CompraOtroDAO.class);
    private final LogDAO logDao = mock(LogDAO.class);
    private final ProveedorDAO proveedorDao = mock(ProveedorDAO.class);
    private final CompraOtroController ctl = new CompraOtroController(dao, logDao, proveedorDao);
    private final UsuarioPrincipal super7 = new UsuarioPrincipal(7, "tecnico1", "", "SUPERTECNICO", 3);

    CompraOtroControllerTest() {
        when(proveedorDao.getById(2)).thenReturn(Optional.of(new Proveedor(2, "Proveedor A", true, "EUR", null, "COMPONENTES")));
        when(proveedorDao.getById(8)).thenReturn(Optional.of(new Proveedor(8, "ACME", false, "EUR", null, "COMPONENTES")));
        when(proveedorDao.getNombreById(2)).thenReturn("Proveedor A");
    }

    private static CompraOtroController.InsertarRequest alta(int idProv, String concepto, int cantidad, double precio, String divisa) {
        return new CompraOtroController.InsertarRequest(idProv, concepto, cantidad, false, precio, divisa, precio);
    }

    private static CompraOtroController.EditarRequest edicion(int idProv, String concepto, int cantidad, double precio) {
        return new CompraOtroController.EditarRequest(idProv, concepto, cantidad, false, precio, "EUR", precio, AT);
    }

    private static CompraOtro pedido(String estado, int cantidad, Integer recibida) {
        return new CompraOtro(5, 2, "Proveedor A", CINTA, cantidad, recibida, false, AT, null, 1.5, "EUR", 1.5, estado, AT);
    }

    private static String falla422(Runnable accion) {
        ResponseStatusException e = assertThrows(ResponseStatusException.class, accion::run);
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, e.getStatusCode());
        return e.getReason();
    }

    private void nadaEscritoNiRegistrado() {
        verify(dao, never()).insertar(anyInt(), any(), anyInt(), anyBoolean(), anyDouble(), any(), anyDouble());
        verify(dao, never()).editar(anyInt(), anyInt(), any(), anyInt(), anyBoolean(), anyDouble(), any(), anyDouble(), any());
        verify(dao, never()).confirmarParcial(anyInt(), anyInt(), any());
        verify(dao, never()).recibirResto(anyInt(), anyInt(), any());
        verifyNoInteractions(logDao);
    }

    // ── Task 1: log de confirmar-alterado ──
    @Test void confirmarAlteradoRegistraLog() {
        ctl.confirmarAlterado(5, new CompraOtroController.UpdatedAtRequest(AT), super7);
        verify(dao).confirmarAlterado(5, AT);
        verify(logDao).insertar(7, "CONFIRMAR_ALTERADO_OTRO", "ID_COMPRA_OTRO: 5");
    }

    @Test void confirmarAlteradoCon409NoRegistraLog() {
        doThrow(new ResponseStatusException(HttpStatus.CONFLICT, "El pedido ya no está en recepción parcial"))
                .when(dao).confirmarAlterado(5, AT);
        assertThrows(ResponseStatusException.class,
                () -> ctl.confirmarAlterado(5, new CompraOtroController.UpdatedAtRequest(AT), super7));
        verifyNoInteractions(logDao);
    }

    // ── Task 2 ──
    @Test void altaConConceptoVacioEs422() {
        assertEquals("El concepto no puede estar vacío.", falla422(() -> ctl.insertar(alta(2, null, 3, 1.5, "EUR"), super7)));
        assertEquals("El concepto no puede estar vacío.", falla422(() -> ctl.insertar(alta(2, "", 3, 1.5, "EUR"), super7)));
        assertEquals("El concepto no puede estar vacío.", falla422(() -> ctl.insertar(alta(2, "   ", 3, 1.5, "EUR"), super7)));
        nadaEscritoNiRegistrado();
    }

    @Test void altaConCantidadPrecioODivisaNoValidosEs422() {
        assertEquals("Cantidad no válida (debe ser > 0).", falla422(() -> ctl.insertar(alta(2, CINTA, 0, 1.5, "EUR"), super7)));
        assertEquals("Precio no válido.", falla422(() -> ctl.insertar(alta(2, CINTA, 3, -1.0, "EUR"), super7)));
        assertEquals("Divisa no válida (EUR o USD).", falla422(() -> ctl.insertar(alta(2, CINTA, 3, 1.5, "CNY"), super7)));
        nadaEscritoNiRegistrado();
    }

    @Test void altaConProveedorInactivoOInexistenteEs422() {
        assertEquals("El proveedor no está activo.", falla422(() -> ctl.insertar(alta(8, CINTA, 3, 1.5, "EUR"), super7)));
        assertEquals("El proveedor no está activo.", falla422(() -> ctl.insertar(alta(99, CINTA, 3, 1.5, "EUR"), super7)));
        nadaEscritoNiRegistrado();
    }

    @Test void altaValidaEscribeYRegistra() {
        ctl.insertar(alta(2, CINTA, 3, 1.5, "EUR"), super7);
        verify(dao).insertar(2, CINTA, 3, false, 1.5, "EUR", 1.5);
        verify(logDao).insertar(7, "CREAR_PEDIDO_OTRO", "CONCEPTO: Cinta de embalar, PROVEEDOR: Proveedor A, CANT: 3");
    }

    @Test void editarValidaConceptoYProveedor() {
        assertEquals("El concepto no puede estar vacío.", falla422(() -> ctl.editar(5, edicion(2, " ", 3, 1.5), super7)));
        assertEquals("El proveedor no está activo.", falla422(() -> ctl.editar(5, edicion(8, CINTA, 3, 1.5), super7)));
        nadaEscritoNiRegistrado();
    }

    @Test void editarUnRecibidoCambiandoLaCantidadEs422() {
        when(dao.getById(5)).thenReturn(Optional.of(pedido("recibido", 4, 4)));
        assertEquals("No se puede cambiar la cantidad de un pedido recibido.",
                falla422(() -> ctl.editar(5, edicion(2, CINTA, 2, 1.5), super7)));
        nadaEscritoNiRegistrado();
    }

    @Test void parcialFueraDeRangoEs422() {
        when(dao.getById(5)).thenReturn(Optional.of(pedido("en_camino", 5, null)));
        assertEquals("La cantidad debe ser mayor que 0 y menor que 5.", falla422(() ->
                ctl.confirmarParcial(5, new CompraOtroController.ConfirmarParcialRequest(0, AT), super7)));
        assertEquals("La cantidad debe ser mayor que 0 y menor que 5.", falla422(() ->
                ctl.confirmarParcial(5, new CompraOtroController.ConfirmarParcialRequest(5, AT), super7)));
        nadaEscritoNiRegistrado();
    }

    @Test void restoFueraDeRangoEs422() {
        when(dao.getById(5)).thenReturn(Optional.of(pedido("parcial", 5, 2)));
        assertEquals("La cantidad debe ser mayor que 0.", falla422(() ->
                ctl.recibirResto(5, new CompraOtroController.RecibirRestoRequest(0, AT), super7)));
        assertEquals("No puedes recibir más de lo pedido. Faltan 3 unidad(es).", falla422(() ->
                ctl.recibirResto(5, new CompraOtroController.RecibirRestoRequest(4, AT), super7)));
        nadaEscritoNiRegistrado();
    }
}
