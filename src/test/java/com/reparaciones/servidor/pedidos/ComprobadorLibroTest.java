package com.reparaciones.servidor.pedidos;

import com.reparaciones.servidor.nsys.EstadoMovil;
import com.reparaciones.servidor.nsys.LoQueVale;
import com.reparaciones.servidor.nsys.MovilNsys;
import com.reparaciones.servidor.nsys.NsysDAO;
import com.reparaciones.servidor.nsys.ProductoMaestro;
import com.reparaciones.servidor.nsys.TallerNsys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.HashMap;
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

    @Test void otroModeloEsOtroModeloYDiceQueFallaQueEsElMovilYDondeEsta() {
        when(nsysDao.porImeis(anyCollection())).thenReturn(List.of(movil(X, "13 mini")));
        assertEquals(Map.of(X, new ComprobadorLibro.Rechazo("OTRO_MODELO",
                        "modelo 13 mini · el pedido es i13128AzulA y el móvil es i13128AzulA · en Listos")),
                comprobador.rechazos(Canal.AMAZON, Map.of(X, "i13128AzulA")));
    }

    /** Caso de producción (24/11): el SKU de NSYS dice A100 pero el grado es C100 y el móvil espera pieza. */
    @Test void unGradoCSinRepararDiceElGradoDeVerdadYLaCaja() {
        MovilNsys c = new MovilNsys(X, "BT-9001", "Apple iPhone 13 128GB Blue", "C100", "Available", "AFaltaRepuestos",
                "Proveedor A", LocalDate.of(2026, 9, 1), null, "i13128AzulA100", "13", "13", "128GB", "Azul", "C100", false,
                "Esperando pieza", null, EstadoMovil.STOCK, null, null);
        when(nsysDao.porImeis(anyCollection())).thenReturn(List.of(c));
        when(taller.valedor(anyCollection())).thenReturn(m -> new LoQueVale.Valor("Azul", "C100", m.sku(), null, null, null, false));
        assertEquals("grado C100 · el pedido es i13128AzulA y el móvil es i13128AzulA100 · en Esperando pieza",
                comprobador.rechazos(Canal.AMAZON, Map.of(X, "i13128AzulA")).get(X).detalle());
    }

    @Test void otroModeloConPedidoSinSkuEscribeRayaYNoNull() {
        when(nsysDao.porImeis(anyCollection())).thenReturn(List.of(movil(X, "13 mini")));
        Map<String, String> pedido = new HashMap<>();
        pedido.put(X, null);
        ComprobadorLibro.Rechazo r = comprobador.rechazos(Canal.AMAZON, pedido).get(X);
        assertNotNull(r);
        assertEquals("el SKU del pedido no está en el maestro · el pedido es — y el móvil es i13128AzulA · en Listos", r.detalle());
    }

    private static MovilNsys conEsim(MovilNsys m, boolean esim) {
        return new MovilNsys(m.imeiSn(), m.batch(), m.goods(), m.grade(), m.status(), m.location(), m.supplier(), m.receivingDate(),
                m.soldDate(), m.sku(), m.modelo(), m.modeloMaestro(), m.capacidad(), m.color(), m.gradoDetalle(), esim, m.ubicacion(),
                m.ubicacionUltima(), m.estado(), m.vistoPrimero(), m.cambiado());
    }

    @Test void unMovilEsimEnUnPedidoSinEsimNoSeRechaza() {
        when(nsysDao.porImeis(anyCollection())).thenReturn(List.of(conEsim(movil(X, "13"), true)));
        assertTrue(comprobador.rechazos(Canal.AMAZON, Map.of(X, "i13128AzulA")).isEmpty());
    }

    @Test void unMovilConSimFisicaEnUnPedidoEsimNoSeRechaza() {
        when(nsysDao.productosPorSkuDeCanal(anyCollection())).thenReturn(List.of(new ProductoMaestro("clave-esim", "13", "13", "128GB",
                "Azul", "A", "i13128AzulAE", "i13128AzulAE", "i13128Azul", true, "Apple iPhone 13 128GB Azul eSIM", "i13128AzulAE",
                null, null)));
        when(nsysDao.porImeis(anyCollection())).thenReturn(List.of(conEsim(movil(X, "13"), false)));
        assertTrue(comprobador.rechazos(Canal.AMAZON, Map.of(X, "i13128AzulAE")).isEmpty());
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
