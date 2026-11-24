package com.reparaciones.servidor.pedidos;

import com.reparaciones.servidor.nsys.EstadoMovil;
import com.reparaciones.servidor.nsys.LoQueVale;
import com.reparaciones.servidor.nsys.MovilNsys;
import com.reparaciones.servidor.nsys.TrabajoAbierto;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AvisoImeiTest {

    private static final AceptaSku ACEPTA = new AceptaSku(List.of(
            AceptaSkuTest.producto("Azul", "A", "i13128AzulA", "i13128AzulA", null, null),
            AceptaSkuTest.producto("Azul", "MsA", "i13128AzulMs", "i13128AzulA", null, null),
            AceptaSkuTest.producto("Azul", "B", "i13128AzulB", "i13128AzulB", null, null)));
    private static final Aceptacion AMAZON_A = ACEPTA.para(Canal.AMAZON, "i13128AzulA");
    private static final Aceptacion WEB_A = ACEPTA.para(Canal.DISTRIBUIDORES, "i13128AzulA");
    private static final List<TrabajoAbierto> NINGUNO = List.of();

    private static MovilNsys movil(String modeloMaestro, String grade, EstadoMovil estado) {
        return new MovilNsys("350000000000001", "BT-9001", "Apple iPhone 13 128GB Blue", grade, "Available", "CAJA LISTOS",
                "Proveedor A", LocalDate.of(2026, 9, 1), null, "i13128AzulA", "13", modeloMaestro, "128GB", "Azul", grade, false,
                "Listos", "Pedidos", estado, null, null);
    }

    private static MovilNsys movil(String modeloMaestro, String grade) {
        return movil(modeloMaestro, grade, EstadoMovil.STOCK);
    }

    private static LoQueVale.Valor vale(String color, String grade, String sku, String gradoNsys) {
        return new LoQueVale.Valor(color, grade, sku, null, gradoNsys, null, gradoNsys != null);
    }

    private static MovilNsys movilEsim(String grade) {
        return new MovilNsys("350000000000001", "BT-9001", "Apple iPhone 13 128GB Blue", grade, "Available", "CAJA LISTOS",
                "Proveedor A", LocalDate.of(2026, 9, 1), null, "i13128AzulA", "13", "13", "128GB", "Azul", grade, true,
                "Listos", "Pedidos", EstadoMovil.STOCK, null, null);
    }

    private static void esperado(String nivel, String motivo, AvisoImei real) {
        assertEquals(nivel, real.nivel());
        assertEquals(motivo, real.motivo());
    }

    @Test void sinMovilEnNsysEsMalYSinSkuNiCaja() {
        assertEquals(new AvisoImei("MAL", "NO_EN_NSYS", null, null),
                AvisoImei.de(AMAZON_A, Canal.AMAZON, null, null, NINGUNO, false));
        assertEquals(new AvisoImei("MAL", "NO_EN_NSYS", null, null),
                AvisoImei.de(AMAZON_A, Canal.AMAZON, null, null, NINGUNO, true), "no estar en NSYS va antes que estar en otra fila");
    }

    @Test void enOtraFilaVivaEsMalYRellenaSkuYCaja() {
        AvisoImei r = AvisoImei.de(AMAZON_A, Canal.AMAZON, movil("13", "A"), vale("Azul", "A", "i13128AzulA", null), NINGUNO, true);
        assertEquals(new AvisoImei("MAL", "EN_OTRA_FILA", "i13128AzulA", "Listos"), r);
    }

    @Test void modeloQueNoCuadraEsMal() {
        esperado("MAL", "MODELO", AvisoImei.de(AMAZON_A, Canal.AMAZON, movil("13 mini", "A"), vale("Azul", "A", "x", null), NINGUNO, false));
    }

    @Test void gradoNoVendibleEsMal() {
        esperado("MAL", "GRADO_NO_VENDIBLE", AvisoImei.de(AMAZON_A, Canal.AMAZON, movil("13", "Fd"), vale("Azul", "Fd", null, null), NINGUNO, false));
    }

    @Test void skuDesconocidoEsMal() {
        esperado("MAL", "SKU_DESCONOCIDO", AvisoImei.de(null, Canal.AMAZON, movil("13", "A"), vale("Azul", "A", "i13128AzulA", null), NINGUNO, false));
    }

    @Test void necesitaTallerEsMalColorOGrado() {
        esperado("MAL", "COLOR", AvisoImei.de(AMAZON_A, Canal.AMAZON, movil("13", "A"), vale("Rojo", "A", "i13128RojoA", null), NINGUNO, false));
        esperado("MAL", "GRADO", AvisoImei.de(AMAZON_A, Canal.AMAZON, movil("13", "C"), vale("Azul", "C", "i13128AzulA", null), NINGUNO, false));
    }

    @Test void enTallerEsAvisoSoloSiCuadraYQuienLoTieneVaSiempre() {
        TrabajoAbierto glass = new TrabajoAbierto("GLASS", "Tecnico 1", LocalDateTime.of(2026, 11, 1, 9, 0), null);
        AvisoImei cuadra = AvisoImei.de(AMAZON_A, Canal.AMAZON, movil("13", "A"), vale("Azul", "A", "i13128AzulA", null), List.of(glass), false);
        esperado("AVISO", "EN_TALLER", cuadra);
        assertEquals(List.of(glass), cuadra.detalle().trabajos());
        AvisoImei grado = AvisoImei.de(AMAZON_A, Canal.AMAZON, movil("13", "C"), vale("Azul", "C", "i13128AzulA", null), List.of(glass), false);
        esperado("MAL", "GRADO", grado);
        assertEquals(List.of(glass), grado.detalle().trabajos(), "el grado que no cuadra y quién lo tiene");
        AvisoImei modelo = AvisoImei.de(AMAZON_A, Canal.AMAZON, movil("14", "A"), vale("Azul", "A", "x", null), List.of(glass), false);
        esperado("MAL", "MODELO", modelo);
        assertEquals(List.of(glass), modelo.detalle().trabajos());
        assertEquals(List.of(glass), grado.detalle().validado().trabajos(), "al validar el SKU se sigue viendo quién lo tiene");
    }

    @Test void conAvisoMsYBPorA() {
        AvisoImei ms = AvisoImei.de(AMAZON_A, Canal.AMAZON, movil("13", "MsA"), vale("Azul", "MsA", "i13128AzulMs", null), NINGUNO, false);
        esperado("AVISO", null, ms);
        assertTrue(ms.detalle().ms());
        esperado("AVISO", "B_POR_A", AvisoImei.de(AMAZON_A, Canal.AMAZON, movil("13", "B"), vale("Azul", "B", "i13128AzulB", null), NINGUNO, false));
    }

    @Test void cuadraEsOkSinMotivo() {
        assertEquals(new AvisoImei("OK", null, "i13128AzulA", "Listos"),
                AvisoImei.de(WEB_A, Canal.DISTRIBUIDORES, movil("13", "A"), vale("Azul", "A", "i13128AzulA", null), NINGUNO, false));
    }

    @Test void laCajaDeUnMovilDePedidoEsLaUltimaUbicacion() {
        assertEquals("Pedidos", AvisoImei.de(WEB_A, Canal.DISTRIBUIDORES, movil("13", "A", EstadoMovil.DE_PEDIDO),
                vale("Azul", "A", "i13128AzulA", null), NINGUNO, false).caja());
    }

    @Test void lasSenalesYElNivel() {
        AvisoImei sim = AvisoImei.de(AMAZON_A, Canal.AMAZON, movilEsim("A"), vale("Azul", "A", "i13128AzulA", null), NINGUNO, false);
        assertEquals("AVISO", sim.nivel());
        assertNull(sim.motivo());
        assertEquals("ESIM", sim.detalle().sim());

        AvisoImei na = AvisoImei.de(AMAZON_A, Canal.AMAZON, movil("13", "n/a"), vale("Rosa", "n/a", null, null), NINGUNO, false);
        assertEquals("MAL", na.nivel());
        assertEquals("COLOR", na.motivo());
        assertTrue(na.detalle().na());
        assertEquals("Rosa", na.detalle().dato());

        TrabajoAbierto glass = new TrabajoAbierto("GLASS", "Ana", LocalDateTime.of(2026, 11, 1, 9, 0), null);
        AvisoImei taller = AvisoImei.de(AMAZON_A, Canal.AMAZON, movil("13", "A"), vale("Azul", "A", "i13128AzulA", null), List.of(glass), false);
        assertEquals("AVISO", taller.nivel());
        assertEquals("EN_TALLER", taller.motivo());
        assertEquals(List.of(glass), taller.detalle().trabajos());

        AvisoImei bien = AvisoImei.de(AMAZON_A, Canal.AMAZON, movil("13", "A"), vale("Azul", "A", "i13128AzulA", null), NINGUNO, false);
        assertEquals("OK", bien.nivel());
        assertNull(bien.detalle(), "sin nada que decir, sin detalle");
    }

    @Test void losMotivosDeSkuNuevosSeValidan() {
        for (String m : List.of("MODELO", "CAPACIDAD", "COLOR", "GRADO", "COLOR_O_GRADO", "GRADO_NO_VENDIBLE", "SKU_DESCONOCIDO"))
            assertTrue(new AvisoImei("MAL", m, null, null).esDeSku(), m);
        assertFalse(new AvisoImei("AVISO", "B_POR_A", null, null).esDeSku());
    }
}
