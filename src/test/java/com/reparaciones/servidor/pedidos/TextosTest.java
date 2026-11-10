package com.reparaciones.servidor.pedidos;

import com.fasterxml.jackson.databind.node.DoubleNode;
import com.fasterxml.jackson.databind.node.LongNode;
import com.fasterxml.jackson.databind.node.TextNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TextosTest {

    @Test void elClienteSeComparaSinMayusculasTildesNiEspaciosDeMas() {
        assertEquals("tienda norte sl", Textos.cliente("  Tiénda   NORTE SL "));
        assertNull(Textos.cliente("   "));
        assertNull(Textos.cliente(null));
    }

    @Test void elImeiSonQuinceCifrasVengaComoTextoOComoNumero() {
        assertEquals("350000000000001", Textos.imei("350000000000001"));
        assertEquals("350000000000001", Textos.imei(" 350000000000001 "));
        assertEquals("350000000000001", Textos.imei(new TextNode("350000000000001")));
        assertEquals("350000000000001", Textos.imei(LongNode.valueOf(350000000000001L)));
        assertEquals("350000000000001", Textos.imei(DoubleNode.valueOf(3.50000000000001E14)));
        assertNull(Textos.imei("cancelado"));
        assertNull(Textos.imei("35000000000001"));
        assertNull(Textos.imei(null));
    }

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
}
