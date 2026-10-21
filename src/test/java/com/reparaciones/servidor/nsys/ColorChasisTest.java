package com.reparaciones.servidor.nsys;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/** El color del chasis → el color del móvil (spec 0.9.21 pieza 2 §2.2): deducido del maestro, nunca por trozos. */
class ColorChasisTest {

    /** El iPhone 13 128GB de prueba: Azul (Blue), Medianoche (Midnight), Rojo ((PRODUCT)RED) y Rosa, que ningún móvil de
     *  NSYS tiene. */
    private static ColorChasis delTrece(Map<String, String> alias) {
        return ColorChasis.de(List.of("Azul", "Medianoche", "Rojo", "Rosa"),
                Map.of("Azul", "Apple iPhone 13 128GB Blue", "Rojo", "Apple iPhone 13 128GB (PRODUCT)RED",
                        "Medianoche", "Apple iPhone 13 128GB Midnight"), "128GB", alias);
    }

    private static final ColorChasis TRECE = delTrece(Map.of());

    @Test void leeElColorDelSkuComoLaWeb() {
        assertEquals("ultramarine", ColorChasis.token("chai16ultramarineesim"));
        assertEquals("red", ColorChasis.token("tapai13red"));
        assertEquals("sierrablue", ColorChasis.token("chai13prosierrablue"), "gana el modelo más largo");
        assertEquals("black", ColorChasis.token("chai16eblackesim"));
        assertEquals("red", ColorChasis.token(" CHAI13RED "));
        assertEquals("", ColorChasis.token(null));
    }

    @Test void elNombreEnInglesSaleDelGoodsDetrasDeLaCapacidad() {
        assertEquals("productred", ColorChasis.nombreDelGoods("Apple iPhone 13 128GB (PRODUCT)RED", "128GB"));
        assertEquals("blacktitanium", ColorChasis.nombreDelGoods("Apple iPhone 15 Pro eSIM 256GB Black Titanium", "256GB"));
        assertEquals("blue", ColorChasis.nombreDelGoods("Apple iPhone 13 128GB Blue eSIM", "128GB"));
        assertNull(ColorChasis.nombreDelGoods("Apple iPhone 13 Blue", "128GB"), "sin la capacidad no se sabe dónde empieza");
        assertNull(ColorChasis.nombreDelGoods("Apple iPhone 13 128GB", "128GB"), "no queda nada");
        assertNull(ColorChasis.nombreDelGoods(null, "128GB"));
    }

    @Test void conElMismoNombreEncaja() {
        assertEquals(Optional.of("Azul"), TRECE.deducir("chai13blue"));
        assertEquals(Optional.of("Medianoche"), TRECE.deducir("tapai13midnightesim"));
    }

    @Test void conElColorBaseEncajaSiEsUnoSolo() {
        assertEquals(Optional.of("Rojo"), TRECE.deducir("chai13red"), "red es el color base de productred");
    }

    @Test void nuncaPorTrozos() {
        assertEquals("pro", ColorChasis.token("chaixpro"));
        assertEquals(Optional.empty(), TRECE.deducir("chaixpro"), "productred contiene pro y no por eso encaja");
        assertEquals(Optional.empty(), TRECE.deducir("chai13product"), "productred contiene product");
        assertEquals(Optional.empty(), TRECE.deducir("chai13ed"));
        assertEquals(Optional.empty(), TRECE.deducir("chai13mid"));
    }

    @Test void ningunoOUnColorSinMovilesEnNsysNoSeDeduce() {
        assertEquals(Optional.empty(), TRECE.deducir("chai13green"));
        assertEquals(Optional.empty(), TRECE.deducir("chai13pink"), "Rosa no tiene ningún móvil en NSYS: no se sabe su nombre");
    }

    @Test void variosNoSeDeduceSalvoQueUnoSeaExacto() {
        ColorChasis doce = ColorChasis.de(List.of("Azul Pacífico", "Azul Sierra"),
                Map.of("Azul Pacífico", "Apple iPhone 12 Pro 128GB Pacific Blue", "Azul Sierra", "Apple iPhone 12 Pro 128GB Sierra Blue"),
                "128GB", Map.of());
        assertEquals(Optional.empty(), doce.deducir("chai12problue"), "los dos tienen base blue");
        assertEquals(Optional.of("Azul Sierra"), doce.deducir("chai12prosierrablue"), "la coincidencia exacta gana");
    }

    @Test void dosColoresConElMismoNombreNoSeDeducen() {
        ColorChasis raro = ColorChasis.de(List.of("Negro", "Medianoche"),
                Map.of("Negro", "Apple iPhone 13 128GB Black", "Medianoche", "Apple iPhone 13 128GB Black"), "128GB", Map.of());
        assertEquals(Optional.empty(), raro.deducir("chai13black"));
    }

    @Test void losAliasDeColorEquivalenciaValen() {
        assertEquals(Optional.of("Rojo"), delTrece(Map.of("rojo", "(PRODUCT)RED")).deducir("chai13rojo"));
        assertEquals(Optional.empty(), TRECE.deducir("chai13rojo"), "sin el alias no encaja");
    }

    @Test void sinModeloEnElMaestroNoHayCandidatos() {
        assertEquals(Optional.empty(), ColorChasis.de(List.of(), Map.of(), "128GB", Map.of()).deducir("chai13red"));
    }
}
