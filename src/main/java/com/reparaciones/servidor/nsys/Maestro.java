package com.reparaciones.servidor.nsys;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** El maestro de productos visto desde un móvil (spec 0.9.21 «SKU del taller» §2.1 y §2.3): su SKU con un color y un grado,
 *  los colores de su modelo y los SKU de cada color y grado. Se busca con los datos del espejo: MODELO (el código del ERP,
 *  null fuera del catálogo: así un iPad 11 no pasa por un iPhone 11, como en LectorMaestro), MODELO_MAESTRO, CAPACIDAD y
 *  ES_ESIM. Sin MODELO_MAESTRO el móvil no tiene modelo en el maestro: ni SKU ni colores. Es puro. */
public final class Maestro {

    private final List<ProductoMaestro> productos;

    public Maestro(List<ProductoMaestro> productos) {
        this.productos = List.copyOf(productos);
    }

    /** El SKU del maestro para el modelo del móvil con este color y este grado (CALIDAD_DETALLE = el grado de NSYS): si las
     *  filas que casan tienen un solo SKU_WEB, ese; si tienen SKU distintos (los conflictos del maestro ya vienen sin SKU),
     *  null: «sin SKU en el maestro». Si no traen ninguno y el grado es «n/a» o en blanco, el «SKU sin grado» + «NA»
     *  ({@link SkuNa}, 0.9.30). */
    public String sku(MovilNsys m, String color, String grado) {
        return unSku(delModelo(m)
                .filter(p -> Normalizacion.mismoTexto(p.color(), color) && Normalizacion.mismoTexto(p.calidadDetalle(), grado))
                .toList(), grado);
    }

    /** Los colores del maestro para el modelo del móvil, sin repetir y por orden alfabético. */
    public List<String> colores(MovilNsys m) {
        return delModelo(m).map(ProductoMaestro::color).filter(c -> c != null && !c.isBlank()).map(String::strip).distinct()
                .sorted(Comparator.comparing((String c) -> c.toLowerCase(Locale.ROOT))).toList();
    }

    /** El nombre en inglés de un color del maestro para el modelo del móvil (spec 0.9.22 §2.1): lo que va detrás de la
     *  capacidad en el «Product Name» de sus filas (el más repetido; a igual número, el primero). null si ninguna fila de ese
     *  color trae el nombre. */
    public String nombreColor(MovilNsys m, String color) {
        Map<String, Long> nombres = delModelo(m).filter(p -> Normalizacion.mismoTexto(p.color(), color))
                .map(p -> GrafiasNsys.colorDelGoods(p.nombre())).filter(Objects::nonNull)
                .collect(Collectors.groupingBy(n -> n, LinkedHashMap::new, Collectors.counting()));
        return nombres.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(null);
    }

    /** Los SKU del modelo por color y grado, solo los que tienen uno (las mismas reglas que {@link #sku}). */
    public List<SkuMaestro> skus(MovilNsys m) {
        Map<List<String>, List<ProductoMaestro>> porCombinacion = new LinkedHashMap<>();
        delModelo(m).filter(p -> p.color() != null && p.calidadDetalle() != null).forEach(p ->
                porCombinacion.computeIfAbsent(List.of(p.color().strip(), p.calidadDetalle().strip()), k -> new ArrayList<>())
                        .add(p));
        List<SkuMaestro> skus = new ArrayList<>();
        porCombinacion.forEach((k, filas) -> {
            String sku = unSku(filas, k.get(1));
            if (sku != null) skus.add(new SkuMaestro(k.get(0), k.get(1), sku));
        });
        return skus;
    }

    /** El SKU de las filas de un color y un grado: su único SKU_WEB; con varios distintos, ninguno; sin ninguno, el de los
     *  «n/a» si las filas coinciden en uno. */
    private static String unSku(List<ProductoMaestro> filas, String grado) {
        List<String> web = filas.stream().map(ProductoMaestro::skuWeb).filter(s -> s != null && !s.isBlank()).distinct().toList();
        if (web.size() == 1) return web.get(0);
        if (!web.isEmpty()) return null;
        List<String> na = filas.stream().map(p -> SkuNa.calculado(p, grado)).filter(Objects::nonNull).distinct().toList();
        return na.size() == 1 ? na.get(0) : null;
    }

    private Stream<ProductoMaestro> delModelo(MovilNsys m) {
        if (m.modeloMaestro() == null) return Stream.empty();
        return productos.stream().filter(p -> Objects.equals(p.modelo(), m.modelo())
                && Objects.equals(p.modeloMaestro(), m.modeloMaestro()) && Objects.equals(p.capacidad(), m.capacidad())
                && p.esEsim() == m.esEsim());
    }
}
