package com.reparaciones.servidor.pedidos;

import com.reparaciones.servidor.nsys.EstadoMovil;
import com.reparaciones.servidor.nsys.LoQueVale;
import com.reparaciones.servidor.nsys.MovilNsys;
import com.reparaciones.servidor.nsys.NsysDAO;
import com.reparaciones.servidor.nsys.TallerNsys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Qué IMEIs del libro no cuadran con el SKU de su pedido (spec «el libro manda» §2.3). Datos inventados. */
class ComprobadorLibroTest {

    private static final String X = "350000000000001";
    private static final String Y = "350000000000002";
    private final NsysDAO nsysDao = mock(NsysDAO.class);
    private final TallerNsys taller = mock(TallerNsys.class);
    private final ComprobadorLibro comprobador = new ComprobadorLibro(nsysDao, taller);

    private static MovilNsys movil(String imei, String modeloMaestro) {
        return new MovilNsys(imei, "BT-9001", "Apple iPhone 13 128GB Blue", "A", "Available", "CAJA LISTOS",
                "Proveedor A", LocalDate.of(2026, 9, 1), null, "i13128AzulA", "13", modeloMaestro, "128GB", "Azul", "A", false,
                "Listos", "Pedidos", EstadoMovil.STOCK, null, null);
    }

    @BeforeEach void preparar() {
        when(nsysDao.productosPorSkuDeCanal(anyCollection())).thenReturn(List.of(
                AceptaSkuTest.producto("Azul", "A", "i13128AzulA", "i13128AzulA", null, null)));
        when(nsysDao.trabajosAbiertos(anyCollection())).thenReturn(Map.of());
        when(taller.valedor(anyCollection())).thenReturn(m -> new LoQueVale.Valor("Azul", "A", m.sku(), null, null, null, false));
    }

    @Test void sinNadaQueComprobarNoLeeNada() {
        assertTrue(comprobador.rechazos(Canal.AMAZON, Map.of()).isEmpty());
        verifyNoInteractions(nsysDao, taller);
    }

    @Test void unMovilQueCuadraNoSeRechaza() {
        when(nsysDao.porImeis(anyCollection())).thenReturn(List.of(movil(X, "13")));
        assertTrue(comprobador.rechazos(Canal.AMAZON, Map.of(X, "i13128AzulA")).isEmpty());
    }

    @Test void unImeiQueNoEstaEnNsysEsNoEnNsysSinDetalle() {
        when(nsysDao.porImeis(anyCollection())).thenReturn(List.of());
        assertEquals(Map.of(X, new ComprobadorLibro.Rechazo("NO_EN_NSYS", null)),
                comprobador.rechazos(Canal.AMAZON, Map.of(X, "i13128AzulA")));
    }

    @Test void otroModeloEsOtroModeloYDiceQueEsElMovil() {
        when(nsysDao.porImeis(anyCollection())).thenReturn(List.of(movil(X, "13 mini")));
        assertEquals(Map.of(X, new ComprobadorLibro.Rechazo("OTRO_MODELO", "el pedido es i13128AzulA y el móvil es i13128AzulA")),
                comprobador.rechazos(Canal.AMAZON, Map.of(X, "i13128AzulA")));
    }

    @Test void soloDevuelveLosQueNoValenYLeeUnaVez() {
        when(nsysDao.porImeis(anyCollection())).thenReturn(List.of(movil(X, "13"), movil(Y, "13 mini")));
        Map<String, String> pedidos = new LinkedHashMap<>();
        pedidos.put(X, "i13128AzulA");
        pedidos.put(Y, "i13128AzulA");
        assertEquals(List.of(Y), List.copyOf(comprobador.rechazos(Canal.AMAZON, pedidos).keySet()));
        verify(nsysDao, times(1)).porImeis(anyCollection());
        verify(taller, times(1)).valedor(anyCollection());
    }
}
