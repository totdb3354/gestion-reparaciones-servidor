package com.reparaciones.servidor.pedidos;

import com.reparaciones.servidor.nsys.ProductoMaestro;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Qué productos del maestro acepta el SKU de un pedido de cada canal (spec pieza 2 §6.4.1): Amazon, solo por «SKU (Amazon) con eSIM»
 *  (decisión del 04/11: Amazon distingue la eSIM del físico, y en el maestro «SKU (Amazon)» de una fila eSIM trae el SKU sin
 *  eSIM, así que mezclarlas haría que lo que pide el pedido saliera al azar); Particulares, por «SKU (stock fonestore.es) Conjunto»; BackiPhone, por «SKU BackiPhone»;
 *  Distribuidores y el Simulador, por el nuestro («SKU (stock web)»). Es puro: se construye con los productos que hagan falta
 *  ({@link com.reparaciones.servidor.nsys.NsysDAO#productosPorSkuDeCanal}). */
public final class AceptaSku {

    private final Map<String, List<ProductoMaestro>> nuestro = new HashMap<>();
    private final Map<String, List<ProductoMaestro>> amazon = new HashMap<>();
    private final Map<String, List<ProductoMaestro>> particulares = new HashMap<>();
    private final Map<String, List<ProductoMaestro>> backiphone = new HashMap<>();

    public AceptaSku(Collection<ProductoMaestro> productos) {
        for (ProductoMaestro p : productos) {
            poner(nuestro, p.skuWeb(), p);
            poner(amazon, p.skuAmazonEsim(), p);
            poner(particulares, p.skuParticulares(), p);
            poner(backiphone, p.skuBackiphone(), p);
        }
    }

    /** Lo que acepta el SKU {@code sku} de un pedido de {@code canal}; null si el maestro no lo conoce. */
    public Aceptacion para(Canal canal, String sku) {
        String s = Textos.sku(sku);
        if (s == null) return null;
        Map<String, List<ProductoMaestro>> columna = switch (canal) {
            case AMAZON, AMAZON_STOCKROOM -> amazon;
            case PARTICULARES -> particulares;
            case BACKIPHONE -> backiphone;
            case DISTRIBUIDORES, SIMULADOR -> nuestro;
        };
        List<ProductoMaestro> productos = columna.get(s);
        if (productos == null || productos.isEmpty()) return null;
        List<ProductoMaestro> b = canal.esAmazon() && s.endsWith("a") ? amazon.getOrDefault(varianteB(s), List.of()) : List.of();
        return new Aceptacion(productos, b);
    }

    /** Los SKU más sus variantes B (los que acaban en «A»), para cargar del maestro todo lo que puede hacer falta. */
    public static Set<String> conVariantes(Collection<String> skus) {
        Set<String> todos = new LinkedHashSet<>();
        for (String sku : skus) {
            if (sku == null || sku.isBlank()) continue;
            String s = sku.strip();
            todos.add(s);
            if (s.endsWith("A") || s.endsWith("a")) todos.add(s.substring(0, s.length() - 1) + "B");
        }
        return todos;
    }

    private static String varianteB(String skuEnMinusculas) {
        return skuEnMinusculas.substring(0, skuEnMinusculas.length() - 1) + "b";
    }

    private static void poner(Map<String, List<ProductoMaestro>> mapa, String sku, ProductoMaestro p) {
        String s = Textos.sku(sku);
        if (s != null) mapa.computeIfAbsent(s, k -> new ArrayList<>()).add(p);
    }
}
