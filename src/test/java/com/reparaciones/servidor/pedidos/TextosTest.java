package com.reparaciones.servidor.pedidos;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TextosTest {

    @Test void pareceTelefonoSiEmpiezaPorIYUnaCifra() {
        assertTrue(Textos.pareceTelefono("i13128AzulA"));
        assertTrue(Textos.pareceTelefono("I16Proesim256NegroA"));
        assertFalse(Textos.pareceTelefono("GLS24"));
        assertFalse(Textos.pareceTelefono("BoxIphoneSmall"));
        assertFalse(Textos.pareceTelefono(null));
    }

    @Test void elSkuSeComparaSinMayusculasNiEspacios() {
        assertEquals("i13128azula", Textos.sku(" i13128AzulA "));
        assertNull(Textos.sku(""));
    }

    /** El unico sinonimo de color, como el verificador de SKU del bot: en las hojas se teclea «Ultramarino» y el maestro lo llama «Azul». */
    @Test void ultramarinoSeComparaComoAzul() {
        assertEquals("i16esim128azula", Textos.sku("i16eSIM128UltramarinoA"));
        assertEquals("i16plusesim256azula100", Textos.sku(" i16Plusesim256ULTRAMARINOA100 "));
        assertEquals("i16eSIM128AzulA", Textos.equivalente("i16eSIM128UltramarinoA"));
        assertEquals("i13128VerdeA", Textos.equivalente("i13128VerdeA"));
    }

    @Test void unNombreSeComparaSinMayusculasTildesNiEspaciosRepetidos() {
        assertEquals("cliente web a", Textos.nombre("  Cliente   Web	Á "));
        assertEquals(Textos.nombre("Camión Ñandú"), Textos.nombre("camion  nandu"));
        assertNull(Textos.nombre("   "));
        assertNull(Textos.nombre(null));
    }
}
