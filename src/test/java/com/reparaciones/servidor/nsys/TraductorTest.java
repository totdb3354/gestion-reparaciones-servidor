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

    @Test void unProductoDeNombreMuyLargoSeEncuentraConLaClaveQueGuardaLaBaseDeDatos() {
        String goods = "Apple" + "x".repeat(145);                    // 150, el ancho de GOODS
        String grade = "A-100-revisado-bat";
        String clave = Normalizacion.clave(goods + " " + grade);
        String enLaBase = clave.length() > 160 ? clave.substring(0, 160) : clave;   // Nsys_producto.CLAVE es VARCHAR(160)
        ProductoMaestro largo = new ProductoMaestro(enLaBase, "13", "13", "128GB", "Azul", "A", "w", "a", "s", false);
        assertSame(largo, new Traductor(List.of(largo), List.of()).producto(goods, grade));
    }

    @Test void encuentraLaCajaSinEspaciosYSinCaja() {
        assertEquals("Listos", t.caja(" CAJA LISTOS").ubicacion());
        assertEquals("En revisión", t.caja(null).ubicacion());
        assertNull(t.caja("CAJA NUEVA"));
    }
}
