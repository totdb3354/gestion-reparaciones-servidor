package com.reparaciones.servidor.nsys;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Cómo escribe NSYS el nombre de cada color (spec 0.9.22 §2.1): datos inventados con las grafías raras que tiene NSYS. */
class GrafiasNsysTest {

    @Test void elColorEsLoQueVaDetrasDeLaCapacidadSinElEsim() {
        assertEquals("Midnight", GrafiasNsys.colorDelGoods("Apple iPhone 14 eSIM 256GB Midnight"));
        assertEquals("Cosmic Orange", GrafiasNsys.colorDelGoods("Apple iPhone 17 Pro Max eSIM 1TB Cosmic Orange"));
        assertEquals("Blue", GrafiasNsys.colorDelGoods("Apple iPhone 13 128GB Blue eSIM"));
        assertEquals("Blue", GrafiasNsys.colorDelGoods("Apple iPhone 13 128 GB eSIM Blue"));
        assertNull(GrafiasNsys.colorDelGoods("Apple iPhone 13 Blue"), "sin capacidad no se sabe dónde empieza");
        assertNull(GrafiasNsys.colorDelGoods("Apple iPhone 13 128GB"));
        assertNull(GrafiasNsys.colorDelGoods("Apple iPhone 13 128GB eSIM"));
        assertNull(GrafiasNsys.colorDelGoods(null));
    }

    @Test void cambiarElColorConservaElModeloLaCapacidadYElEsimDelMovil() {
        assertEquals("Apple iPhone 14 eSIM 256GB Yellow", GrafiasNsys.cambiarColor("Apple iPhone 14 eSIM 256GB Midnight", "Yellow"));
        assertEquals("Apple iphone 15 pro 128GB Blue Titanium",
                GrafiasNsys.cambiarColor("Apple iphone 15 pro 128GB black titanium", "Blue Titanium"), "el modelo, como lo tiene el móvil");
        assertEquals("Apple iPhone 13 128GB Pink eSIM", GrafiasNsys.cambiarColor("Apple iPhone 13 128GB Blue eSIM", "Pink"));
        assertEquals("Apple iPhone 13 128GB eSIM Pink", GrafiasNsys.cambiarColor("Apple iPhone 13 128GB eSIM Blue", "Pink"));
        assertNull(GrafiasNsys.cambiarColor("Apple iPhone 13 Blue", "Pink"));
        assertNull(GrafiasNsys.cambiarColor(null, "Pink"));
        assertNull(GrafiasNsys.cambiarColor("Apple iPhone 13 128GB Blue", null));
    }

    @Test void ganaLaGrafiaDelMismoModeloYDentroLaMasUsada() {
        GrafiasNsys g = GrafiasNsys.de(List.of(
                new GrafiasNsys.GoodsContado("17 PRO MAX eSIM", "Apple iPhone 17 Pro Max eSIM 256GB Deep Blue", 9),
                new GrafiasNsys.GoodsContado("17 PRO MAX eSIM", "Apple iPhone 17 Pro Max eSIM 512GB Deep blue", 2),
                new GrafiasNsys.GoodsContado("17 PRO", "Apple iPhone 17 Pro 256GB deep blue", 50),
                new GrafiasNsys.GoodsContado(null, "Apple iPhone 15 128GB Blue", 3)));
        assertEquals("Deep Blue", g.color("17 PRO MAX eSIM", "Deep blue"), "en su modelo, la más usada");
        assertEquals("deep blue", g.color("17 AIR", "DEEP BLUE"), "sin móviles en su modelo, la más usada de todos");
        assertEquals("Blue", g.color(null, "blue"));
        assertNull(g.color("15", "Ultramarine"), "NSYS nunca lo ha escrito");
        assertNull(g.color("15", null));
        assertNull(g.color("15", " "));
    }

    @Test void aIgualDeMovilesGanaLaPrimeraPorOrdenAlfabetico() {
        GrafiasNsys g = GrafiasNsys.de(List.of(new GrafiasNsys.GoodsContado("13", "Apple iPhone 13 128GB blue", 2),
                new GrafiasNsys.GoodsContado("13", "Apple iPhone 13 256GB Blue", 2)));
        assertEquals("Blue", g.color("13", "BLUE"));
    }
}
