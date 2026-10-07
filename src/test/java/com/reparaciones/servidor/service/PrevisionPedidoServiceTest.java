package com.reparaciones.servidor.service;

import com.reparaciones.servidor.dao.ComponenteDAO;
import com.reparaciones.servidor.dao.ParametroDAO;
import com.reparaciones.servidor.model.Componente;
import com.reparaciones.servidor.util.PrevisionPedido.ConsumoDia;
import com.reparaciones.servidor.util.PrevisionPedido.Pesos;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PrevisionPedidoServiceTest {

    private static final LocalDate HOY = LocalDate.of(2026, 10, 7);
    private static final LocalDateTime T = LocalDateTime.of(2026, 9, 1, 10, 0);

    private final ComponenteDAO componenteDao = mock(ComponenteDAO.class);
    private final ParametroDAO parametroDao = mock(ParametroDAO.class);
    private final PrevisionPedidoService servicio = new PrevisionPedidoService(componenteDao, parametroDao);

    private static Componente comp(int id, Integer master, int stock, int minimo, boolean activo, int enCamino) {
        Componente c = new Componente(id, "bat-" + id, T, stock, minimo, activo, T);
        c.setIdComMaster(master);
        c.setEnCamino(enCamino);
        return c;
    }

    @Test void rellenaLosActivosConElConsumoDeSuMasterYDejaNulosLosDesactivados() {
        when(parametroDao.getPesosPrevision()).thenReturn(Pesos.POR_DEFECTO);
        // master 1: 12 / 8 / 5 en los tres tramos
        when(componenteDao.getConsumoDiario(HOY.minusDays(90), HOY)).thenReturn(List.of(
                new ConsumoDia(1, HOY.minusDays(3), 12),
                new ConsumoDia(1, HOY.minusDays(40), 8),
                new ConsumoDia(1, HOY.minusDays(70), 5)));
        Componente master = comp(1, null, 3, 2, true, 0);
        Componente slave = comp(5, 1, 3, 2, true, 0);         // el listado ya trae stock y mínimo del master
        Componente sinConsumo = comp(2, null, 0, 2, true, 0);
        Componente desactivado = comp(4, null, 0, 2, false, 0);

        servicio.rellenar(List.of(master, slave, sinConsumo, desactivado), HOY);

        assertEquals(0.31, master.getConsumoDiario());
        assertEquals(2, master.getPedir15());
        assertEquals(7, master.getPedir30());
        assertEquals(0.31, slave.getConsumoDiario());
        assertEquals(7, slave.getPedir30());
        assertEquals(0.0, sinConsumo.getConsumoDiario());
        assertEquals(2, sinConsumo.getPedir15());
        assertNull(desactivado.getConsumoDiario());
        assertNull(desactivado.getPedir15());
        assertNull(desactivado.getPedir30());
    }
}
