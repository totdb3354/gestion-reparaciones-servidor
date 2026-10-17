package com.reparaciones.servidor.nsys;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NormalizacionTest {

    @Test void laClaveDelMaestroVaSinEspaciosYEnMinusculas() {
        assertEquals("appleiphone13128gbbluea-", Normalizacion.clave("Apple iPhone 13 128GB Blue", "A-"));
        assertEquals("appleiphone13128gbbluea-", Normalizacion.clave("  Apple iPhone 13  128GB Blue A- "));
        assertEquals("", Normalizacion.clave(null, null));
    }

    @Test void laClaveSeCortaAlAnchoDeNsysProducto() {
        // Goods (150) + Grade (20) sin espacios pueden pasar de los 160 de Nsys_producto.CLAVE.
        assertEquals(160, Normalizacion.clave("G".repeat(150), "R".repeat(20)).length());
        assertEquals(160, Normalizacion.clave("Apple iPhone 13 " + "x".repeat(200)).length());
    }

    @Test void laCajaVaSinEspaciosALosLadosYSinCajaEsVacia() {
        assertEquals("CAJA LISTOS", Normalizacion.caja(" CAJA LISTOS "));
        assertEquals("", Normalizacion.caja(null));
        assertEquals("", Normalizacion.caja("   "));
    }

    @Test void elModeloDelMaestroPasaAlCodigoDelErp() {
        assertEquals("13pro", Normalizacion.modeloErp("13 PRO"));
        assertEquals("14promax", Normalizacion.modeloErp("14 PRO MAX eSIM"));
        assertEquals("16e", Normalizacion.modeloErp("16 e"));
        assertEquals("se2020", Normalizacion.modeloErp("SE2020"));
        assertEquals("xsmax", Normalizacion.modeloErp("XS Max"));
        assertEquals("6splus", Normalizacion.modeloErp("6S Plus"));
        assertEquals("air", Normalizacion.modeloErp("Air"));
        assertEquals("13", Normalizacion.modeloErp("13"));
    }

    @Test void unModeloFueraDelCatalogoQuedaSinCodigo() {
        assertNull(Normalizacion.modeloErp("17 e eSIM"));
        assertNull(Normalizacion.modeloErp("SE2022"));
        assertNull(Normalizacion.modeloErp("5C"));
        assertNull(Normalizacion.modeloErp(null));
    }

    @Test void elEsimSaleDelNombreDelProducto() {
        assertTrue(Normalizacion.esEsim("Apple iPhone 15 128GB eSIM Black"));
        assertFalse(Normalizacion.esEsim("Apple iPhone 15 128GB Black"));
        assertFalse(Normalizacion.esEsim(null));
    }

    @Test void elCienDelGradoEsLaBateriaNueva() {
        assertEquals("A-", Normalizacion.gradoSinBateria("A-100"));
        assertEquals("C", Normalizacion.gradoSinBateria("C100"));
        assertEquals("A+", Normalizacion.gradoSinBateria("A+100"));
        assertEquals("A", Normalizacion.gradoSinBateria("A"));
        assertEquals("n/a", Normalizacion.gradoSinBateria("n/a"));
        assertEquals("", Normalizacion.gradoSinBateria(null));
        assertTrue(Normalizacion.bateriaNueva("B100"));
        assertFalse(Normalizacion.bateriaNueva("B"));
        assertFalse(Normalizacion.bateriaNueva("100"));
    }
}
