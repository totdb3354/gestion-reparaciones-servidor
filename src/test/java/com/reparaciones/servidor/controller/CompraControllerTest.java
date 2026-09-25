package com.reparaciones.servidor.controller;

import com.reparaciones.servidor.dao.ComponenteDAO;
import com.reparaciones.servidor.dao.CompraComponenteDAO;
import com.reparaciones.servidor.dao.LogDAO;
import com.reparaciones.servidor.dao.ProveedorDAO;
import com.reparaciones.servidor.security.UsuarioPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

/** CompraController (spec 4b §4.1-4.3): log de confirmar-alterado, 422 de alta, edición y recepción y precioEur. */
class CompraControllerTest {

    private static final LocalDateTime AT = LocalDateTime.of(2026, 9, 25, 10, 0);

    private final CompraComponenteDAO dao = mock(CompraComponenteDAO.class);
    private final LogDAO logDao = mock(LogDAO.class);
    private final ComponenteDAO componenteDao = mock(ComponenteDAO.class);
    private final ProveedorDAO proveedorDao = mock(ProveedorDAO.class);
    private final CompraController ctl = new CompraController(dao, logDao, componenteDao, proveedorDao);
    private final UsuarioPrincipal super7 = new UsuarioPrincipal(7, "tecnico1", "", "SUPERTECNICO", 3);

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
}
