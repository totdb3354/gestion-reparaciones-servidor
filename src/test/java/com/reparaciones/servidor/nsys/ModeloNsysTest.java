package com.reparaciones.servidor.nsys;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.*;

class ModeloNsysTest {

    private final NsysDAO dao = mock(NsysDAO.class);
    private final ModeloNsys modelos = new ModeloNsys(dao);

    @Test void elModeloSaleDelEspejo() {
        when(dao.modeloDe("350000000000001")).thenReturn("13pro");
        assertEquals("13pro", modelos.lookupModeloInterno(" 350000000000001 "));
    }

    @Test void sinImeiNoConsulta() {
        assertNull(modelos.lookupModeloInterno(" "));
        assertNull(modelos.lookupModeloInterno(null));
        verifyNoInteractions(dao);
    }
}
