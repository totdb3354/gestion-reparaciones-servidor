package com.reparaciones.servidor.pedidos;

import com.reparaciones.servidor.nsys.ProductoMaestro;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Los productos del maestro que acepta el SKU de un pedido (spec pieza 2 §6.4.1) y, en Amazon, los que acepta con el aviso
 *  «B por A». Es pura. */
public record Aceptacion(List<ProductoMaestro> productos, List<ProductoMaestro> productosBporA) {

    public Aceptacion {
        productos = List.copyOf(productos);
        productosBporA = List.copyOf(productosBporA);
    }

    /** ¿Es uno de los nuestros (SKU (stock web)) que acepta el pedido? */
    public boolean acepta(String sku) {
        return contiene(productos, sku);
    }

    public boolean aceptaBporA(String sku) {
        return contiene(productosBporA, sku);
    }

    /** Para un móvil sin SKU en el maestro: ¿hay un producto aceptado con ese color y ese grado (Calidad Detalle)? */
    public boolean aceptaColorYGrado(String color, String grado) {
        return productos.stream().anyMatch(p -> igual(p.color(), color) && igual(p.calidadDetalle(), grado));
    }

    /** Lo que pide el pedido, para la tabla de diferencias: el modelo, la capacidad y la eSIM de sus productos, y el color y el
     *  grado del producto de grado A si lo hay (si no, del primero). */
    public Atributos pide() {
        ProductoMaestro base = productos.stream().filter(p -> igual(p.calidadDetalle(), "A")).findFirst().orElse(productos.get(0));
        String color = productos.stream().map(ProductoMaestro::color).filter(c -> c != null && !c.isBlank())
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting())).entrySet().stream()
                .max(Map.Entry.<String, Long>comparingByValue().thenComparing(Map.Entry.comparingByKey(Comparator.reverseOrder())))
                .map(Map.Entry::getKey).orElse(base.color());
        return new Atributos(base.modeloMaestro(), base.capacidad(), color, base.calidadDetalle(), base.esEsim());
    }

    private static boolean contiene(List<ProductoMaestro> lista, String sku) {
        String s = Textos.sku(sku);
        return s != null && lista.stream().anyMatch(p -> s.equals(Textos.sku(p.skuWeb())));
    }

    private static boolean igual(String a, String b) {
        return a != null && b != null && a.strip().equalsIgnoreCase(b.strip());
    }
}
