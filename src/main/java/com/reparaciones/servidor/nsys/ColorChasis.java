package com.reparaciones.servidor.nsys;

import com.reparaciones.servidor.util.CatalogoPiezas;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/** El color de un móvil a partir del SKU de un chasis o una tapa trasera (spec 0.9.21 «SKU del taller», pieza 2, §2.2). Los
 *  candidatos son los colores del maestro para el modelo, la capacidad y la eSIM del móvil, cada uno con su nombre en
 *  inglés: el del Goods más usado de ese color en NSYS, detrás de la capacidad y normalizado («(PRODUCT)RED» → productred).
 *  Encaja el que tiene el mismo nombre que el color del SKU o, si ninguno, el mismo color base ({@link #BASE}); los alias
 *  de Color_equivalencia valen en los dos lados. Siempre por palabra entera, nunca por trozos («productred» contiene
 *  «pro» y no por eso encaja). Solo vale uno: con ninguno o varios, no se deduce. Es puro. */
public final class ColorChasis {

    /** Color oficial → color base: la tabla fija de los SKU de chasis (los tokens de colores.ts de la web). Un SKU con el
     *  color base encaja con el oficial («chai13red» con «(PRODUCT)RED»). */
    static final Map<String, String> BASE = Map.ofEntries(
            Map.entry("productred", "red"),
            Map.entry("pacificblue", "blue"), Map.entry("sierrablue", "blue"), Map.entry("bluetitanium", "blue"),
            Map.entry("mistblue", "blue"), Map.entry("skyblue", "blue"), Map.entry("deepblue", "blue"),
            Map.entry("alpinegreen", "green"), Map.entry("deeppurple", "purple"),
            Map.entry("spaceblack", "black"), Map.entry("blacktitanium", "black"),
            Map.entry("whitetitanium", "white"), Map.entry("cloudwhite", "white"),
            Map.entry("lightgold", "gold"));

    private final Map<String, String> nombres;
    private final Map<String, String> alias;

    /** {@code nombres}: color del maestro → su nombre en inglés normalizado (un color sin nombre no está).
     *  {@code alias}: texto normalizado → color oficial normalizado. */
    private ColorChasis(Map<String, String> nombres, Map<String, String> alias) {
        this.nombres = nombres;
        this.alias = alias;
    }

    /** Con los colores del maestro del modelo ({@link Maestro#colores}), el Goods más usado de cada uno
     *  ({@link NsysDAO#goodsMasUsados}), la capacidad del móvil y Color_equivalencia (texto → color oficial). */
    public static ColorChasis de(List<String> colores, Map<String, String> goodsPorColor, String capacidad,
                                 Map<String, String> alias) {
        Map<String, String> goods = new HashMap<>();
        goodsPorColor.forEach((color, g) -> {
            if (color != null) goods.put(color.strip(), g);
        });
        Map<String, String> nombres = new LinkedHashMap<>();
        for (String color : colores) {
            String nombre = nombreDelGoods(goods.get(color.strip()), capacidad);
            if (nombre != null) nombres.put(color, nombre);
        }
        Map<String, String> limpios = new HashMap<>();
        alias.forEach((texto, oficial) -> {
            String t = normalizar(texto);
            String o = normalizar(oficial);
            if (!t.isEmpty() && !o.isEmpty()) limpios.put(t, o);
        });
        return new ColorChasis(nombres, limpios);
    }

    /** El color del maestro de este SKU, si encaja con uno solo. */
    public Optional<String> deducir(String sku) {
        String token = token(sku);
        if (token.isEmpty()) return Optional.empty();
        Set<String> delSku = conAlias(token);
        List<String> exactos = candidatos(nombre -> !Collections.disjoint(conAlias(nombre), delSku));
        if (!exactos.isEmpty()) return exactos.size() == 1 ? Optional.of(exactos.get(0)) : Optional.empty();
        Set<String> bases = bases(delSku);
        List<String> porBase = candidatos(nombre -> !Collections.disjoint(bases(conAlias(nombre)), bases));
        return porBase.size() == 1 ? Optional.of(porBase.get(0)) : Optional.empty();
    }

    /** El color de un SKU de chasis o tapa, como leerSku de la web: lo que queda entre el modelo (el más largo del catálogo)
     *  y un «esim» final («chai16ultramarineesim» → ultramarine). */
    static String token(String sku) {
        if (sku == null) return "";
        String s = sku.strip().toLowerCase(Locale.ROOT);
        String prefijo = CatalogoPiezas.prefijo(s);
        String modelo = CatalogoPiezas.modelo(s, prefijo);
        String resto = s.length() > prefijo.length() ? s.substring(prefijo.length()) : "";
        if (resto.startsWith("i")) resto = resto.substring(1);
        if (modelo != null) resto = resto.substring(modelo.length());
        if (resto.endsWith("esim")) resto = resto.substring(0, resto.length() - "esim".length());
        return normalizar(resto);
    }

    /** El nombre en inglés de un Goods: lo que va detrás de la capacidad, sin un «eSIM» final («Apple iPhone 13 128GB
     *  (PRODUCT)RED» → productred). null si el Goods no lleva la capacidad o no queda nada. */
    static String nombreDelGoods(String goods, String capacidad) {
        if (goods == null || capacidad == null) return null;
        String g = normalizar(goods);
        String cap = normalizar(capacidad);
        int i = cap.isEmpty() ? -1 : g.lastIndexOf(cap);
        if (i < 0) return null;
        String nombre = g.substring(i + cap.length());
        if (nombre.endsWith("esim")) nombre = nombre.substring(0, nombre.length() - "esim".length());
        return nombre.isEmpty() ? null : nombre;
    }

    /** En minúsculas y solo letras y cifras («Sierra Blue» → sierrablue). */
    static String normalizar(String texto) {
        return texto == null ? "" : texto.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private List<String> candidatos(Predicate<String> encaja) {
        return nombres.entrySet().stream().filter(e -> encaja.test(e.getValue())).map(Map.Entry::getKey).toList();
    }

    private Set<String> conAlias(String nombre) {
        Set<String> conjunto = new HashSet<>();
        conjunto.add(nombre);
        String oficial = alias.get(nombre);
        if (oficial != null) conjunto.add(oficial);
        return conjunto;
    }

    private static Set<String> bases(Set<String> nombres) {
        return nombres.stream().map(n -> BASE.getOrDefault(n, n)).collect(Collectors.toSet());
    }
}
