package com.reparaciones.servidor.pedidos;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CanalTest {

    @Test void elAjusteDaElCanalDeCadaMarketplaceYSaltaLoMalEscrito() {
        assertEquals(Map.of("100", Canal.AMAZON, "200", Canal.AMAZON_STOCKROOM, "300", Canal.DISTRIBUIDORES),
                Canal.deAjuste(" 100:AMAZON, 200:amazon_stockroom ,300:DISTRIBUIDORES, 400:OTRO, sin-dos-puntos, 500:SIMULADOR"));
        assertEquals(Map.of(), Canal.deAjuste(""));
        assertEquals(Map.of(), Canal.deAjuste(null));
    }

    @Test void amazonYWebs() {
        assertTrue(Canal.AMAZON_STOCKROOM.esAmazon());
        assertFalse(Canal.DISTRIBUIDORES.esAmazon());
        assertTrue(Canal.BACKIPHONE.esWeb());
        assertFalse(Canal.AMAZON.esWeb());
        assertEquals("Amazon Stockroom", Canal.AMAZON_STOCKROOM.etiqueta());
    }
}
