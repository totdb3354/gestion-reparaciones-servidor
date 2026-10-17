package com.reparaciones.servidor.controller;

import com.reparaciones.servidor.dao.EnvioDAO;
import com.reparaciones.servidor.dao.LogDAO;
import com.reparaciones.servidor.dao.MovimientoDAO;
import com.reparaciones.servidor.dao.RevisionDAO;
import com.reparaciones.servidor.dao.TelefonoDAO;
import com.reparaciones.servidor.nsys.ModeloNsys;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

/** GET /api/telefonos/{imei}/modelo (0.9.17): el modelo del taller y, si no lo tiene, el del espejo de NSYS. Sustituye a
 *  IMEIcheck: sin llamada externa ni freno. */
class TelefonoControllerModeloTest {

    private final TelefonoDAO dao = mock(TelefonoDAO.class);
    private final ModeloNsys nsys = mock(ModeloNsys.class);
    private final TelefonoController controller = new TelefonoController(dao, nsys, mock(LogDAO.class),
            mock(RevisionDAO.class), mock(EnvioDAO.class), mock(MovimientoDAO.class));

    @Test void conModeloEnElTallerNoMiraNsys() {
        when(dao.getModelo("350000000000001")).thenReturn("12");
        assertEquals("12", controller.getModelo("350000000000001").value());
        verifyNoInteractions(nsys);
    }

    @Test void sinModeloEnElTallerLoDaNsys() {
        when(nsys.lookupModeloInterno("350000000000001")).thenReturn("13pro");
        assertEquals("13pro", controller.getModelo("350000000000001").value());
    }

    @Test void siNadieLoSabeVaVacio() {
        assertEquals("", controller.getModelo("350000000000001").value());
    }
}
