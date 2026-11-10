package com.reparaciones.servidor.nsys;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** El SKU de los móviles sin grado (spec 0.9.30 §3): el «SKU sin grado» del maestro + «NA». */
class SkuNaTest {

    private static ProductoMaestro fila(String capacidad, String skuWeb, String skuSinGrado, boolean esEsim) {
        return new ProductoMaestro("clave", "13", "13", capacidad, "Azul", "n/a", skuWeb, null, skuSinGrado, esEsim);
    }

    private static final ProductoMaestro AZUL = fila("128GB", null, "i13128Azul", false);

    @Test void naYEnBlancoSonSinGrado() {
        assertTrue(SkuNa.esNa("n/a"));
        assertTrue(SkuNa.esNa(" N/A "));
        assertTrue(SkuNa.esNa(""));
        assertTrue(SkuNa.esNa("  "));
        assertTrue(SkuNa.esNa(null));
        assertFalse(SkuNa.esNa("B"));
        assertFalse(SkuNa.esNa("XX"), "un código desconocido no es n/a");
        assertFalse(SkuNa.esNa("ID"));
    }

    @Test void elSkuSinGradoConNaDetras() {
        assertEquals("i13128AzulNA", SkuNa.calculado(AZUL, "n/a"));
        assertEquals("i13128AzulNA", SkuNa.calculado(AZUL, null));
        assertEquals("i13128AzulNA", SkuNa.calculado(fila("128GB", null, " i13128Azul ", false), "N/A"), "sin espacios");
    }

    @Test void conOtroGradoNoSeCalcula() {
        assertNull(SkuNa.calculado(AZUL, "B"));
        assertNull(SkuNa.calculado(AZUL, "XX"));
    }

    @Test void sinSkuSinGradoNoSeCalcula() {
        assertNull(SkuNa.calculado(fila("128GB", null, null, false), "n/a"));
        assertNull(SkuNa.calculado(fila("128GB", null, "  ", false), "n/a"));
        assertNull(SkuNa.calculado(null, "n/a"));
    }

    @Test void unEsimLlevaEsimDelanteDeLaCapacidad() {
        assertEquals("i14eSIM128MedianocheNA", SkuNa.calculado(fila("128GB", null, "i14128Medianoche", true), "n/a"));
        assertEquals("i12eSIM128NegroNA", SkuNa.calculado(fila("128GB", null, "i12128Negro", true), "n/a"),
                "la capacidad es el último «128», no el del modelo");
        assertEquals("i15eSIM1TBNegroNA", SkuNa.calculado(fila("1TB", null, "i151TBNegro", true), "n/a"));
        assertEquals("i14eSIM128MedianocheNA", SkuNa.calculado(fila("128GB", null, "i14eSIM128Medianoche", true), "n/a"),
                "si ya lo lleva, no se repite");
    }

    @Test void unEsimSinLaCapacidadEnElSkuSeQuedaSinSku() {
        assertNull(SkuNa.calculado(fila("256GB", null, "i14128Medianoche", true), "n/a"));
        assertNull(SkuNa.calculado(fila(null, null, "i14128Medianoche", true), "n/a"));
    }

    @Test void unSkuQueNoCabeEnLaColumnaNoSeCalcula() {
        String largo = "i13128" + "A".repeat(AnchosNsys.SKU - 7);            // 59 caracteres: con «NA» son 61
        assertNull(SkuNa.calculado(fila("128GB", null, largo, false), "n/a"));
        String justo = "i13128" + "A".repeat(AnchosNsys.SKU - 8);            // 58 caracteres: con «NA» son 60, cabe
        assertEquals(justo + "NA", SkuNa.calculado(fila("128GB", null, justo, false), "n/a"));
    }

    @Test void mandaElSkuDelMaestro() {
        assertEquals("i13128AzulX", SkuNa.de(fila("128GB", "i13128AzulX", "i13128Azul", false), "n/a"));
        assertEquals("i13128AzulNA", SkuNa.de(AZUL, "n/a"));
        assertEquals("i13128AzulNA", SkuNa.de(fila("128GB", " ", "i13128Azul", false), "n/a"), "un SKU en blanco no manda");
        assertNull(SkuNa.de(AZUL, "B"), "con otro grado, lo del maestro: nada");
        assertNull(SkuNa.de(null, "n/a"));
    }
}
