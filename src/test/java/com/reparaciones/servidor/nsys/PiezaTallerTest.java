package com.reparaciones.servidor.nsys;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static com.reparaciones.servidor.nsys.PiezaTaller.Tipo.*;
import static org.junit.jupiter.api.Assertions.*;

/** Qué piezas cambian el móvil (spec 0.9.21 pieza 2 §2.1): por el prefijo del SKU. */
class PiezaTallerTest {

    @Test void cuentanChasisTapaYBateria() {
        assertEquals(Optional.of(CHASIS), PiezaTaller.Tipo.deSku("chai13red"));
        assertEquals(Optional.of(CHASIS), PiezaTaller.Tipo.deSku(" CHAI16TEALESIM "));
        assertEquals(Optional.of(TAPA), PiezaTaller.Tipo.deSku("tapai13red"));
        assertEquals(Optional.of(BATERIA), PiezaTaller.Tipo.deSku("bati13promax"));
    }

    @Test void elRestoNoCuenta() {
        for (String sku : new String[] {"lcdi13", "cami13", "gi13", "mci13", "otroi13", "", "  "})
            assertEquals(Optional.empty(), PiezaTaller.Tipo.deSku(sku), sku);
        assertEquals(Optional.empty(), PiezaTaller.Tipo.deSku(null));
    }

    @Test void soloChasisYTapaLlevanColor() {
        assertTrue(CHASIS.conColor());
        assertTrue(TAPA.conColor());
        assertFalse(BATERIA.conColor());
    }

    @Test void elTextoDeLaPiezaParaElRastro() {
        assertEquals("chasis chai13red nuevo", new PiezaTaller("R1", "chai13red", CHASIS, true).texto());
        assertEquals("chasis chai13red reutilizado", new PiezaTaller("R1", "chai13red", CHASIS, false).texto());
        assertEquals("tapa tapai13red reutilizada", new PiezaTaller("R1", "tapai13red", TAPA, false).texto());
        assertEquals("batería bati13 nueva", new PiezaTaller(null, "bati13", BATERIA, true).texto());
    }
}
