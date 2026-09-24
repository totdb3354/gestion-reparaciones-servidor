package com.reparaciones.servidor.controller;

import com.reparaciones.servidor.dao.CompraComponenteDAO;
import com.reparaciones.servidor.dao.ComponenteDAO;
import com.reparaciones.servidor.dao.LogDAO;
import com.reparaciones.servidor.dao.ProveedorDAO;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** La respuesta pasa de Map<String,Object> a ValorEntero: misma forma JSON {"value": n}, pero el contrato OpenAPI
 *  la tipa como entero y la web no tiene que convertirla a mano. */
class CompraControllerEnCaminoTest {

    @Test void cantidadEnCaminoDevuelveValorEnteroTipado() {
        CompraComponenteDAO dao = mock(CompraComponenteDAO.class);
        when(dao.getCantidadEnCaminoPorComponente(12)).thenReturn(5);
        CompraController ctl = new CompraController(dao, mock(LogDAO.class), mock(ComponenteDAO.class), mock(ProveedorDAO.class));

        assertEquals(5, ctl.getCantidadEnCamino(12).value());
    }
}
