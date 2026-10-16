package com.reparaciones.servidor.nsys;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TraductorTest {

    private final ProductoMaestro azulA = new ProductoMaestro(Normalizacion.clave("Apple iPhone 13 128GB Blue A"),
            "13", "13", "128GB", "Azul", "A", "i13128AzulA", "i13128AzulA", "i13128Azul", false);
    private final Traductor t = new Traductor(List.of(azulA), List.of(
            new UbicacionNsys("", "En revisión", true, false),
            new UbicacionNsys("CAJA LISTOS", "Listos", true, false)));

    @Test void encuentraElProductoPorGoodsYGrade() {
        assertSame(azulA, t.producto("Apple iPhone 13 128GB  Blue", "A"));
        assertNull(t.producto("Apple iPhone 13 128GB Blue", "B"));
    }

    @Test void encuentraLaCajaSinEspaciosYSinCaja() {
        assertEquals("Listos", t.caja(" CAJA LISTOS").ubicacion());
        assertEquals("En revisión", t.caja(null).ubicacion());
        assertNull(t.caja("CAJA NUEVA"));
    }
}
