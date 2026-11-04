package com.reparaciones.servidor.pedidos;

import com.reparaciones.servidor.nsys.EstadoMovil;
import com.reparaciones.servidor.nsys.LoQueVale;
import com.reparaciones.servidor.nsys.MovilNsys;
import com.reparaciones.servidor.nsys.NsysDAO;
import com.reparaciones.servidor.nsys.TallerNsys;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class VistaPedidosTest {

    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");
    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 11, 4, 10, 0);
    private final PedidosDAO dao = mock(PedidosDAO.class);
    private final NsysDAO nsys = mock(NsysDAO.class);
    private final TallerNsys taller = mock(TallerNsys.class);
    private final VistaPedidos vista = new VistaPedidos(dao, nsys, taller, 14, Clock.fixed(AHORA.atZone(MADRID).toInstant(), MADRID));

    @Test void cadaUnidadConSuEstadoLosRepetidosYLosDelSimuladorViejosSoloSiSePiden() {
        when(dao.abiertos()).thenReturn(List.of(
                new PedidoGuardado(1, Canal.AMAZON, "402-0000000-0000001", "Cliente A", "DE", AHORA.minusDays(3), FuentePedidos.SHIPPYPRO),
                new PedidoGuardado(2, Canal.SIMULADOR, "6400", "Cliente B", null, AHORA.minusDays(20), FuentePedidos.SIMULADOR)));
        when(dao.unidades(any())).thenReturn(List.of(
                new UnidadGuardada(10, 1, 1, "i13128AzulA", "350000000000001", "PEDIDOS_2026", AHORA, true, null),
                new UnidadGuardada(11, 1, 2, "i13128AzulA", null, null, null, false, null),
                new UnidadGuardada(20, 2, 1, "i13128AzulA", "350000000000001", "SIMULADOR", AHORA, false, "6401")));
        MovilNsys m = new MovilNsys("350000000000001", "BT-9001", "Apple iPhone 13 128GB Blue", "A", "Available", "CAJA LISTOS",
                "Proveedor A", LocalDate.of(2026, 9, 1), null, "i13128AzulA", "13", "13", "128GB", "Azul", "A", false, "Listos",
                "Listos", EstadoMovil.STOCK, null, null);
        when(nsys.porImeis(any())).thenReturn(List.of(m));
        when(nsys.trabajosAbiertos(anyCollection())).thenReturn(Map.of());
        when(taller.valedor(any())).thenReturn(x -> new LoQueVale.Valor("Azul", "A", "i13128AzulA", null, null, null, false));
        when(nsys.productosPorSkuDeCanal(any())).thenReturn(List.of(
                AceptaSkuTest.producto("Azul", "A", "i13128AzulA", "i13128AzulA", null, null)));

        List<PedidoVista> sinViejos = vista.pedidos(false);
        verify(dao).unidades(java.util.Set.of(1));
        assertEquals(List.of("402-0000000-0000001"), sinViejos.stream().map(PedidoVista::numero).toList());
        UnidadVista con = sinViejos.get(0).unidades().get(0);
        assertEquals("CUADRA", con.estado());
        assertEquals("Listos", con.movil().ubicacion());
        assertTrue(con.sinPedidoConcreto());
        assertFalse(con.repetido(), "el pedido viejo del Simulador no se enseña, así que no cuenta para repetidos");
        assertEquals("SIN_MOVIL", sinViejos.get(0).unidades().get(1).estado());
        assertEquals(new Atributos("13", "128GB", "Azul", "A", false), sinViejos.get(0).unidades().get(1).pide());

        List<PedidoVista> conViejos = vista.pedidos(true);
        assertEquals(2, conViejos.size());
        assertTrue(conViejos.get(1).viejo());
        assertTrue(conViejos.get(0).unidades().get(0).repetido());
        assertEquals("6401", conViejos.get(1).unidades().get(0).otroOrderId());
    }

    @Test void lasFuentesConSuUltimaLecturaBuenaYSuNivel() {
        when(dao.ultimaLectura(eq(FuentePedidos.SHIPPYPRO), eq(java.util.Set.of(LecturaPedidos.OK)))).thenReturn(Optional.of(
                new LecturaGuardada(1, FuentePedidos.SHIPPYPRO, AHORA.minusMinutes(3), AHORA.minusMinutes(3), "OK", 12, "i99RaroA", null)));
        when(dao.ultimaLectura(eq(FuentePedidos.SHIPPYPRO), argThat(s -> s.size() > 1))).thenReturn(Optional.of(
                new LecturaGuardada(2, FuentePedidos.SHIPPYPRO, AHORA.minusMinutes(1), AHORA.minusMinutes(1), "ERROR", 0, null,
                        "java.io.IOException: con un id de hoja")));
        when(dao.ultimaLectura(eq(FuentePedidos.PEDIDOS_2026), any())).thenReturn(Optional.empty());
        when(dao.ultimaLectura(eq(FuentePedidos.SIMULADOR), any())).thenReturn(Optional.empty());

        List<FuentePedidosVista> f = vista.fuentes();

        assertEquals(List.of("SHIPPYPRO", "PEDIDOS_2026", "SIMULADOR"), f.stream().map(FuentePedidosVista::fuente).toList());
        assertEquals("OK", f.get(0).nivel());
        assertEquals("ERROR", f.get(0).resultado());
        assertEquals(VistaPedidos.MOTIVO_ERROR, f.get(0).motivo(), "un ERROR no saca su texto (puede llevar ids)");
        assertEquals("i99RaroA", f.get(0).skuDesconocidos());
        assertEquals("SIN_DATOS", f.get(1).nivel());
    }
}
