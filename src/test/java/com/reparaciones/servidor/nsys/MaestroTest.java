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
}
