package com.reparaciones.servidor.pedidos;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class UnidadesDeImeiTest {

    private final PedidosDAO dao = mock(PedidosDAO.class);

    @Test void elPedidoMasAntiguoDeCadaImeiYSiEstaEnDos() {
        when(dao.unidadesAbiertasDeImeis(any())).thenReturn(List.of(
                new PedidosDAO.UnidadDeImei("350000000000001", "402-0000000-0000001", "AMAZON", "i13128AzulA"),
                new PedidosDAO.UnidadDeImei("350000000000001", "6480", "SIMULADOR", "i13128AzulA"),
                new PedidosDAO.UnidadDeImei("350000000000002", "18832", "DISTRIBUIDORES", "i14256AzulA")));

        Map<String, UnidadesDeImei.PedidoDeImei> m = new UnidadesDeImei(dao, true).abiertasPorImei(List.of("350000000000001", "350000000000002"));

        assertEquals(new UnidadesDeImei.PedidoDeImei("402-0000000-0000001", "AMAZON", "i13128AzulA", true), m.get("350000000000001"));
        assertFalse(m.get("350000000000002").repetido());
    }

    @Test void conLaLecturaApagadaNoPreguntaNada() {
        assertEquals(Map.of(), new UnidadesDeImei(dao, false).abiertasPorImei(List.of("350000000000001")));
        verifyNoInteractions(dao);
    }
}
