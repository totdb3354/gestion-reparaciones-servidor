package com.reparaciones.servidor.nsys;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static com.reparaciones.servidor.nsys.TallerDePrueba.*;
import static org.junit.jupiter.api.Assertions.*;

/** El producto que hay que poner en NSYS para un color (spec 0.9.22 §2.1, pasos 1 a 4). El móvil de prueba es un iPhone 13 128GB
 *  Blue. */
class ProductoNsysTest {

    private static final MovilNsys AZUL = movil("350000000000001", "Azul", "B");
    private static final Supplier<Maestro> MAESTRO = () -> new Maestro(List.of(productoConNombre("Blue", "Azul", "B"),
            productoConNombre("Pink", "Rosa", "B"), producto("Green", "Verde", "B")));
    private static final Supplier<GrafiasNsys> GRAFIAS = () -> GrafiasNsys.de(List.of(
            new GrafiasNsys.GoodsContado("13", "Apple iPhone 13 256GB Pink", 4)));

    private static <T> Supplier<T> prohibido() {
        return () -> { throw new AssertionError("no hacía falta leerlo"); };
    }

    @Test void conMovilesDeEseColorEnSuModeloYCapacidadValeEseGoods() {
        assertEquals(new ProductoNsys.Poner("Apple iPhone 13 128GB Pink", false), ProductoNsys.paraColor(AZUL, "Rosa",
                Map.of("Rosa", "Apple iPhone 13 128GB Pink"), prohibido(), prohibido()));
    }

    @Test void sinMovilesSeComponeConElNombreDelMaestroEscritoComoNsys() {
        assertEquals(new ProductoNsys.Poner("Apple iPhone 13 128GB Pink", false),
                ProductoNsys.paraColor(AZUL, "Rosa", Map.of(), MAESTRO, GRAFIAS));
    }

    @Test void siNsysNuncaLoHaEscritoSaleElNombreDelMaestroTalCualConCompruebalo() {
        assertEquals(new ProductoNsys.Poner("Apple iPhone 13 128GB Pink", true),
                ProductoNsys.paraColor(AZUL, "Rosa", Map.of(), MAESTRO, () -> GrafiasNsys.de(List.of())));
    }

    @Test void sinNombreEnElMaestroNoHayProducto() {
        assertNull(ProductoNsys.paraColor(AZUL, "Verde", Map.of(), MAESTRO, prohibido()), "el maestro no da el nombre");
        assertNull(ProductoNsys.paraColor(AZUL, null, Map.of(), prohibido(), prohibido()));
    }

    @Test void unMovilSinCapacidadEnSuGoodsNoSeCompone() {
        MovilNsys raro = new MovilNsys("350000000000003", "BT-9001", "Apple iPhone 13 Blue", "B", "Available", "CAJA LISTOS",
                "Proveedor A", AZUL.receivingDate(), null, null, "13", "13", "128GB", "Azul", "B", false, "Listos", "Listos",
                EstadoMovil.STOCK, HOY, HOY);
        assertNull(ProductoNsys.paraColor(raro, "Rosa", Map.of(), prohibido(), prohibido()));
    }
}
