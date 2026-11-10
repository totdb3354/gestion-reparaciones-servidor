package com.reparaciones.servidor.nsys;

import org.junit.jupiter.api.Test;

import java.util.List;

import static com.reparaciones.servidor.nsys.TallerDePrueba.*;
import static org.junit.jupiter.api.Assertions.*;

/** El maestro visto desde un móvil (spec 0.9.21 §2.1 y §2.3): SKU por modelo, capacidad, eSIM, color y grado. */
class MaestroTest {

    private static final MovilNsys AZUL = movil("350000000000001", "Azul", "B");

    @Test void conUnaFilaSuSku() {
        Maestro maestro = new Maestro(List.of(producto("Blue", "Azul", "A100"), producto("(PRODUCT)RED", "Rojo", "A100")));
        assertEquals("i13128RojoA100", maestro.sku(AZUL, "Rojo", "A100"));
        assertEquals("i13128AzulA100", maestro.sku(AZUL, " Azul ", "A100"), "sin espacios a los lados");
    }

    @Test void sinNingunaFilaNoHaySku() {
        assertNull(new Maestro(List.of(producto("Blue", "Azul", "A100"))).sku(AZUL, "Verde", "B"));
    }

    @Test void variasFilasConSkuDistintoNoDanSkuYConElMismoSi() {
        ProductoMaestro otro = new ProductoMaestro("otraclave", "13", "13", "128GB", "Azul", "B", "i13128AzulB-otro", null,
                null, false);
        ProductoMaestro igual = new ProductoMaestro("otraclave", "13", "13", "128GB", "Azul", "B", "i13128AzulB", null, null,
                false);
        assertNull(new Maestro(List.of(producto("Blue", "Azul", "B"), otro)).sku(AZUL, "Azul", "B"));
        assertEquals("i13128AzulB", new Maestro(List.of(producto("Blue", "Azul", "B"), igual)).sku(AZUL, "Azul", "B"));
    }

    @Test void unaFilaSinSkuNoCuenta() {
        ProductoMaestro conflicto = new ProductoMaestro("conflicto", "13", "13", "128GB", "Azul", "B", null, null, null, false);
        assertNull(new Maestro(List.of(conflicto)).sku(AZUL, "Azul", "B"), "los conflictos del maestro vienen sin SKU");
        assertEquals("i13128AzulB", new Maestro(List.of(conflicto, producto("Blue", "Azul", "B"))).sku(AZUL, "Azul", "B"));
    }

    @Test void unSkuEnBlancoNoCuenta() {
        ProductoMaestro blanco = new ProductoMaestro("blanco", "13", "13", "128GB", "Azul", "B", "  ", null, null, false);
        assertNull(new Maestro(List.of(blanco)).sku(AZUL, "Azul", "B"));
        assertEquals("i13128AzulB", new Maestro(List.of(blanco, producto("Blue", "Azul", "B"))).sku(AZUL, "Azul", "B"));
        assertTrue(new Maestro(List.of(blanco)).skus(AZUL).isEmpty());
        assertEquals(1, new Maestro(List.of(blanco, producto("Blue", "Azul", "B"))).skus(AZUL).size());
    }

    @Test void sinModeloEnElMaestroNiSkuNiColores() {
        Maestro maestro = new Maestro(List.of(producto("Blue", "Azul", "B")));
        MovilNsys sin = sinMaestro("350000000000001", "B");
        assertNull(maestro.sku(sin, "Azul", "B"));
        assertTrue(maestro.colores(sin).isEmpty());
        assertTrue(maestro.skus(sin).isEmpty());
    }

    @Test void otroModeloCapacidadOEsimNoCuentan() {
        ProductoMaestro ipad = new ProductoMaestro("ipad", null, "13", "128GB", "Verde", "B", "ipad13VerdeB", null, null, false);
        ProductoMaestro grande = new ProductoMaestro("256", "13", "13", "256GB", "Verde", "B", "i13256VerdeB", null, null, false);
        ProductoMaestro esim = new ProductoMaestro("esim", "13", "13", "128GB", "Verde", "B", "i13128VerdeBe", null, null, true);
        Maestro maestro = new Maestro(List.of(ipad, grande, esim));
        assertNull(maestro.sku(AZUL, "Verde", "B"), "un iPad con el mismo modelo del maestro no es un iPhone");
        assertTrue(maestro.colores(AZUL).isEmpty());
    }

    @Test void losColoresDelModeloSinRepetirYPorOrden() {
        Maestro maestro = new Maestro(List.of(producto("(PRODUCT)RED", "Rojo", "A100"), producto("Blue", "Azul", "B"),
                producto("Blue", "Azul", "A100"), producto("Midnight", "Medianoche", "B")));
        assertEquals(List.of("Azul", "Medianoche", "Rojo"), maestro.colores(AZUL));
    }

    @Test void losSkuPorColorYGradoParaLaVistaPrevia() {
        ProductoMaestro otro = new ProductoMaestro("otraclave", "13", "13", "128GB", "Rojo", "B", "i13128RojoB-otro", null,
                null, false);
        Maestro maestro = new Maestro(List.of(producto("Blue", "Azul", "A100"), producto("(PRODUCT)RED", "Rojo", "B"), otro));
        assertEquals(List.of(new SkuMaestro("Azul", "A100", "i13128AzulA100")), maestro.skus(AZUL),
                "Rojo B tiene dos SKU distintos: no sale");
    }

    @Test void elNombreDeUnColorSaleDelProductNameDetrasDeLaCapacidad() {
        Maestro maestro = new Maestro(List.of(productoConNombre("(PRODUCT)RED", "Rojo", "A"),
                productoConNombre("(PRODUCT)RED", "Rojo", "B"), producto("Blue", "Azul", "B")));
        assertEquals("(PRODUCT)RED", maestro.nombreColor(AZUL, "Rojo"));
        assertEquals("(PRODUCT)RED", maestro.nombreColor(AZUL, " Rojo "));
        assertNull(maestro.nombreColor(AZUL, "Azul"), "sin «Product Name»");
        assertNull(maestro.nombreColor(AZUL, "Verde"));
        assertNull(maestro.nombreColor(sinMaestro("350000000000002", "B"), "Rojo"), "sin modelo en el maestro");
    }

    private static final MovilNsys SIN_GRADO = movil("350000000000003", "Azul", "n/a");

    private static ProductoMaestro na(String color, String skuWeb, String skuSinGrado, boolean esEsim) {
        return new ProductoMaestro("na" + color + skuSinGrado, "13", "13", "128GB", color, "n/a", skuWeb, null, skuSinGrado, esEsim);
    }

    @Test void unNaSinSkuEnElMaestroLlevaElSkuSinGradoConNa() {
        Maestro maestro = new Maestro(List.of(na("Azul", null, "i13128Azul", false), na("Rojo", null, "i13128Rojo", false)));
        assertEquals("i13128AzulNA", maestro.sku(SIN_GRADO, "Azul", "n/a"));
        assertEquals("i13128RojoNA", maestro.sku(SIN_GRADO, "Rojo", "n/a"), "con el color que le pone el taller");
    }

    @Test void siElMaestroDaSkuAlNaMandaElMaestro() {
        Maestro maestro = new Maestro(List.of(na("Azul", "i13128AzulX", "i13128Azul", false), na("Azul", null, "i13128Azul", false)));
        assertEquals("i13128AzulX", maestro.sku(SIN_GRADO, "Azul", "n/a"));
    }

    @Test void unGradoDesconocidoSinSkuSigueSinSku() {
        ProductoMaestro raro = new ProductoMaestro("raro", "13", "13", "128GB", "Azul", "XX", null, null, "i13128Azul", false);
        assertNull(new Maestro(List.of(raro)).sku(SIN_GRADO, "Azul", "XX"));
        assertTrue(new Maestro(List.of(raro)).skus(SIN_GRADO).isEmpty());
    }

    @Test void variasFilasNaConSkuSinGradoDistintoNoDanSku() {
        Maestro maestro = new Maestro(List.of(na("Azul", null, "i13128Azul", false), na("Azul", null, "i13128Azulado", false)));
        assertNull(maestro.sku(SIN_GRADO, "Azul", "n/a"));
        assertTrue(maestro.skus(SIN_GRADO).isEmpty());
    }

    @Test void unEsimNaLlevaEsimEnElSku() {
        Maestro maestro = new Maestro(List.of(na("Azul", null, "i13128Azul", true)));
        assertEquals("i13eSIM128AzulNA", maestro.sku(esim("350000000000004", "Azul", "n/a"), "Azul", "n/a"));
    }

    @Test void losSkuPorColorYGradoLlevanElDeLosNa() {
        Maestro maestro = new Maestro(List.of(producto("Blue", "Azul", "B"), na("Azul", null, "i13128Azul", false)));
        assertEquals(List.of(new SkuMaestro("Azul", "B", "i13128AzulB"), new SkuMaestro("Azul", "n/a", "i13128AzulNA")),
                maestro.skus(SIN_GRADO));
    }
}
