package com.reparaciones.servidor.controller;

import com.reparaciones.servidor.dao.LogDAO;
import com.reparaciones.servidor.dao.ProveedorDAO;
import com.reparaciones.servidor.security.UsuarioPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

/** Guards que hasta el 4a solo hacía el cliente JavaFX (spec 4a §4.2-4.3): el servidor borraba a ciegas (500 por clave
 *  foránea en carrera) y aceptaba nombres vacíos y cualquier divisa. Los textos son los del cliente. */
class ProveedorControllerTest {

    private final ProveedorDAO dao = mock(ProveedorDAO.class);
    private final LogDAO logDao = mock(LogDAO.class);
    private final ProveedorController ctl = new ProveedorController(dao, logDao);
    private final UsuarioPrincipal super7 = new UsuarioPrincipal(7, "tecnico_f", "", "SUPERTECNICO", 3);

    private static ResponseStatusException falla(Runnable r) {
        return assertThrows(ResponseStatusException.class, r::run);
    }

    @Test void altaConNombreEnBlancoEs422() {
        ResponseStatusException e = falla(() -> ctl.insertar(new ProveedorController.AltaRequest("   ", null, "COMPONENTES")));
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, e.getStatusCode());
        assertEquals("El nombre no puede estar vacío.", e.getReason());
        verifyNoInteractions(dao);
    }

    @Test void altaConNombreDeMasDe100Es422() {
        ResponseStatusException e = falla(() -> ctl.insertar(new ProveedorController.AltaRequest("x".repeat(101), null, null)));
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, e.getStatusCode());
        assertEquals("El nombre no puede superar los 100 caracteres.", e.getReason());
        verifyNoInteractions(dao);
        // 100 justos valen
        ctl.insertar(new ProveedorController.AltaRequest("x".repeat(100), null, null));
        verify(dao).insertar("x".repeat(100), null, null);
    }

    @Test void altaConDivisaDesconocidaEs422YConNulaOEnBlancoVale() {
        ResponseStatusException e = falla(() -> ctl.insertar(new ProveedorController.AltaRequest("ACME", "CNY", null)));
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, e.getStatusCode());
        assertEquals("Divisa no válida (EUR o USD).", e.getReason());
        // Nula o en blanco: se pasa null y el DAO pone EUR (como hoy)
        ctl.insertar(new ProveedorController.AltaRequest("ACME", null, "COMPONENTES"));
        ctl.insertar(new ProveedorController.AltaRequest("ACME", "", "COMPONENTES"));
        ctl.insertar(new ProveedorController.AltaRequest("ACME", "  ", "COMPONENTES"));
        verify(dao, times(3)).insertar("ACME", null, "COMPONENTES");
    }

    @Test void altaRecortaElNombre() {
        ctl.insertar(new ProveedorController.AltaRequest("  ACME  ", "USD", "COMPONENTES"));
        verify(dao).insertar("ACME", "USD", "COMPONENTES");
    }

    @Test void editarValidaNombreYDivisa() {
        ResponseStatusException e1 = falla(() -> ctl.editar(4, new ProveedorController.EditarRequest("", "EUR", "")));
        assertEquals("El nombre no puede estar vacío.", e1.getReason());
        ResponseStatusException e2 = falla(() -> ctl.editar(4, new ProveedorController.EditarRequest("ACME", "GBP", "")));
        assertEquals("Divisa no válida (EUR o USD).", e2.getReason());
        // El PUT es estricto: sin divisa también es 422 (el JavaFX siempre manda la del combo)
        ResponseStatusException e3 = falla(() -> ctl.editar(4, new ProveedorController.EditarRequest("ACME", null, "")));
        assertEquals("Divisa no válida (EUR o USD).", e3.getReason());
        ResponseStatusException e4 = falla(() -> ctl.editar(4, new ProveedorController.EditarRequest("x".repeat(101), "EUR", "")));
        assertEquals("El nombre no puede superar los 100 caracteres.", e4.getReason());
        verify(dao, never()).editar(anyInt(), any(), any(), any());
        ctl.editar(4, new ProveedorController.EditarRequest(" ACME ", "usd", "nota"));
        verify(dao).editar(4, "ACME", "USD", "nota");
    }

    @Test void borrarConPedidosEs409SinTocarNada() {
        when(dao.tienePedidos(4)).thenReturn(true);
        ResponseStatusException e = falla(() -> ctl.borrar(4, super7));
        assertEquals(HttpStatus.CONFLICT, e.getStatusCode());
        assertEquals("El proveedor tiene pedidos y no se puede borrar.", e.getReason());
        verify(dao, never()).borrar(anyInt());
        verifyNoInteractions(logDao);
    }

    @Test void borrarSinPedidosBorraYRegistraLog() {
        when(dao.tienePedidos(4)).thenReturn(false);
        when(dao.getNombreById(4)).thenReturn("ACME");
        ctl.borrar(4, super7);
        verify(dao).borrar(4);
        verify(logDao).insertar(7, "BORRAR_PROVEEDOR", "ID_PROV: 4, NOMBRE: ACME");
    }

    /** Id inexistente: 204 sin log, como antes del 4a (decisión 2). getNombreById usa queryForObject y lanza
     *  EmptyResultDataAccessException; el controlador lo tolera y borrar(99) sigue siendo un no-op. */
    @Test void borrarInexistenteEs204SinLog() {
        when(dao.tienePedidos(99)).thenReturn(false);
        when(dao.getNombreById(99)).thenThrow(new EmptyResultDataAccessException(1));
        ctl.borrar(99, super7);
        verify(dao).borrar(99);
        verifyNoInteractions(logDao);
    }
}
