package com.reparaciones.servidor.util;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Tipo y modelo de una referencia (spec 2026-10-12 §4.1): los mismos casos que los tests de la web (taller/lib/modelos.test.ts). */
class CatalogoPiezasTest {

    @Test void elCatalogoDeModelosEsElDeLaWeb() {
        assertEquals(39, CatalogoPiezas.MODELOS_ORDENADOS.size());
        assertEquals("6s", CatalogoPiezas.MODELOS_ORDENADOS.get(0));
        assertEquals("17promax", CatalogoPiezas.MODELOS_ORDENADOS.get(38));
        assertEquals(39, Set.copyOf(CatalogoPiezas.MODELOS_ORDENADOS).size());
        assertTrue(CatalogoPiezas.MODELOS_ORDENADOS.indexOf("16e") < CatalogoPiezas.MODELOS_ORDENADOS.indexOf("16plus"));
        assertTrue(CatalogoPiezas.MODELOS_ORDENADOS.indexOf("17") < CatalogoPiezas.MODELOS_ORDENADOS.indexOf("air"));
    }

    @Test void prefijoEsLoQueVaAntesDeLaPrimeraI() {
        assertEquals("bat", CatalogoPiezas.prefijo("bati13promax"));
        assertEquals("cha", CatalogoPiezas.prefijo("chai14midnight"));
        assertEquals("tapa", CatalogoPiezas.prefijo("tapai15black"));
        assertEquals("g", CatalogoPiezas.prefijo("gi15negra"));
        assertEquals("mc", CatalogoPiezas.prefijo("mci13mininegra"));
        assertEquals("lcd", CatalogoPiezas.prefijo("lcdi14negraic"));
        assertEquals("cam", CatalogoPiezas.prefijo("cami13"));
        assertEquals("otro", CatalogoPiezas.prefijo("otroi13"));
        assertEquals("flexboton", CatalogoPiezas.prefijo("flexboton"));
    }

    @Test void ganaElCodigoMasLargoQueSeaPrefijoDelResto() {
        assertEquals("13promax", CatalogoPiezas.modelo("bati13promaxneg", "bat"));
        assertEquals("13pro", CatalogoPiezas.modelo("bati13pro", "bat"));
        assertEquals("13", CatalogoPiezas.modelo("bati13", "bat"));
        assertEquals("xsmax", CatalogoPiezas.modelo("lcdixsmaxnegra", "lcd"));
        assertEquals("xs", CatalogoPiezas.modelo("lcdixsnegra", "lcd"));
        assertEquals("6splus", CatalogoPiezas.modelo("lcdi6splus", "lcd"));
        assertEquals("16e", CatalogoPiezas.modelo("bati16e", "bat"));
    }

    @Test void quitaElPrefijoYLaIYNoDistingueMayusculas() {
        assertEquals("14plus", CatalogoPiezas.modelo("BATI14PLUS", "bat"));
        assertEquals("13", CatalogoPiezas.modelo("chai13negro", "cha"));
        assertEquals("13", CatalogoPiezas.modelo("cami13", "cam"));
        assertEquals("se2020", CatalogoPiezas.modelo("batise2020", "bat"));
        assertEquals("air", CatalogoPiezas.modelo("batair", "bat"));
        assertEquals("13", CatalogoPiezas.modelo("gi13", "g"));
        assertEquals("14promax", CatalogoPiezas.modelo("gi14promaxnegra", "g"));
        assertEquals("13", CatalogoPiezas.modelo("mci13", "mc"));
    }

    @Test void sinCoincidenciaDevuelveNull() {
        assertNull(CatalogoPiezas.modelo("batuniversal", "bat"));
        assertNull(CatalogoPiezas.modelo("bat", "bat"));
        assertNull(CatalogoPiezas.modelo("", "bat"));
    }

    @Test void ordenaTiposYModelosSinRepetidos() {
        assertEquals(List.of("bat", "cha", "tapa", "g", "mc", "lcd", "cam", "zzz"),
                CatalogoPiezas.ordenarTipos(List.of("zzz", "lcd", "cam", "g", "bat", "tapa", "mc", "cha", "bat")));
        assertEquals(List.of("12", "12mini", "13", "16e", "air"), CatalogoPiezas.ordenarModelos(Set.of("air", "13", "12mini", "16e", "12")));
        assertTrue(CatalogoPiezas.esModelo("16e"));
        assertFalse(CatalogoPiezas.esModelo("13x"));
    }

    @Test void vendeConTodosLosModelosConExcepcionesYSinModelo() {
        Map<String, Set<String>> tipos = Map.of("bat", Set.of(), "g", Set.of("12mini", "13mini"));
        assertTrue(CatalogoPiezas.vende(tipos, "bati17e"));            // modelo nuevo: entra solo
        assertTrue(CatalogoPiezas.vende(tipos, "gi14negra"));
        assertFalse(CatalogoPiezas.vende(tipos, "gi13mininegra"));     // excluido
        assertTrue(CatalogoPiezas.vende(Map.of("g", Set.of("13")), "gi13mininegra"));   // 13 no excluye 13mini
        assertFalse(CatalogoPiezas.vende(tipos, "lcdi14negraic"));     // tipo que no vende
        assertTrue(CatalogoPiezas.vende(Map.of("bat", Set.of("13")), "batiuniversal"));   // sin modelo: las excepciones no le afectan
    }
}
