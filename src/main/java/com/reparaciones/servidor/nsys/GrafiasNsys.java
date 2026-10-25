package com.reparaciones.servidor.nsys;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Cómo escribe NSYS el nombre de cada color (spec 0.9.22 §2.1, pasos 2 y 3): NSYS crea un producto nuevo si cambia una
 *  mayúscula, así que el color de un producto a poner se escribe como ya lo escribe NSYS. Se arma con los Goods del espejo
 *  (NsysDAO#goodsContados): el color es lo que va detrás de la capacidad, sin «eSIM». Para un nombre en inglés (el del maestro,
 *  comparado sin mayúsculas ni espacios), vale la grafía más usada en el mismo modelo del maestro y, si no hay, la más usada de
 *  todos; a igual número, la primera por orden alfabético. Es puro. */
public final class GrafiasNsys {

    /** Un Goods del espejo, el modelo del maestro de sus móviles (null si no lo tienen) y cuántos móviles lo llevan. */
    public record GoodsContado(String modeloMaestro, String goods, int n) {}

    static final Pattern CAPACIDAD = Pattern.compile("\\b(\\d+)\\s?(GB|TB)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern FABRICANTE = Pattern.compile("^(?i)apple\\s+");
    private static final Pattern ESIM_DELANTE = Pattern.compile("^(?i)(esim)\\s+");
    private static final Pattern ESIM_DETRAS = Pattern.compile("\\s+(?i)(esim)$");

    /** Modelo del maestro → nombre normalizado → grafía → móviles. */
    private final Map<String, Map<String, Map<String, Integer>>> porModelo;
    /** Nombre normalizado → grafía → móviles, de todos los modelos. */
    private final Map<String, Map<String, Integer>> todos;

    private GrafiasNsys(Map<String, Map<String, Map<String, Integer>>> porModelo, Map<String, Map<String, Integer>> todos) {
        this.porModelo = porModelo;
        this.todos = todos;
    }

    public static GrafiasNsys de(List<GoodsContado> filas) {
        Map<String, Map<String, Map<String, Integer>>> porModelo = new HashMap<>();
        Map<String, Map<String, Integer>> todos = new HashMap<>();
        for (GoodsContado f : filas) {
            String color = colorDelGoods(f.goods());
            if (color == null) continue;
            String clave = ColorChasis.normalizar(color);
            todos.computeIfAbsent(clave, k -> new HashMap<>()).merge(color, f.n(), Integer::sum);
            if (f.modeloMaestro() != null)
                porModelo.computeIfAbsent(f.modeloMaestro().strip(), k -> new HashMap<>())
                        .computeIfAbsent(clave, k -> new HashMap<>()).merge(color, f.n(), Integer::sum);
        }
        return new GrafiasNsys(porModelo, todos);
    }

    /** La grafía de NSYS del color {@code nombre} (en inglés): la más usada en el modelo {@code modeloMaestro} y, si no hay, la
     *  más usada de todos; null si NSYS nunca lo ha escrito. */
    public String color(String modeloMaestro, String nombre) {
        String clave = ColorChasis.normalizar(nombre);
        if (clave.isEmpty()) return null;
        Map<String, Map<String, Integer>> delModelo = modeloMaestro == null ? Map.of()
                : porModelo.getOrDefault(modeloMaestro.strip(), Map.of());
        String grafia = masUsada(delModelo.get(clave));
        return grafia != null ? grafia : masUsada(todos.get(clave));
    }

    private static String masUsada(Map<String, Integer> grafias) {
        if (grafias == null) return null;
        return grafias.entrySet().stream()
                .min(Comparator.comparing((Map.Entry<String, Integer> e) -> -e.getValue()).thenComparing(Map.Entry::getKey))
                .map(Map.Entry::getKey).orElse(null);
    }

    /** El color de un Goods: lo que va detrás de la capacidad, sin «eSIM» delante ni detrás («Apple iPhone 14 eSIM 256GB
     *  Midnight» → «Midnight»). null si no hay capacidad o no queda nada. */
    static String colorDelGoods(String goods) {
        if (goods == null) return null;
        Matcher c = CAPACIDAD.matcher(goods);
        if (!c.find()) return null;
        String resto = goods.substring(c.end()).strip();
        resto = ESIM_DETRAS.matcher(ESIM_DELANTE.matcher(resto).replaceFirst("")).replaceFirst("").strip();
        return resto.isEmpty() || resto.equalsIgnoreCase("esim") ? null : resto;
    }

    /** El Goods {@code goods} con el color cambiado por {@code grafia}: el modelo y la capacidad como los tiene el móvil, y su
     *  «eSIM» donde estaba («Apple iPhone 13 128GB Blue eSIM» + «Pink» → «Apple iPhone 13 128GB Pink eSIM»). null sin
     *  capacidad o sin grafía. */
    static String cambiarColor(String goods, String grafia) {
        if (goods == null || grafia == null) return null;
        Matcher c = CAPACIDAD.matcher(goods);
        if (!c.find()) return null;
        String resto = goods.substring(c.end()).strip();
        Matcher delante = ESIM_DELANTE.matcher(resto);
        Matcher detras = ESIM_DETRAS.matcher(resto);
        return goods.substring(0, c.end()) + " " + (delante.find() ? delante.group(1) + " " : "") + grafia
                + (detras.find() ? " " + detras.group(1) : "");
    }

    /** El modelo de un Goods como lo lleva la plantilla de NSYS (spec 0.9.22 §4, columna C): lo que va antes de la capacidad, sin
     *  el fabricante («Apple iPhone 14 eSIM 256GB Midnight» → «iPhone 14 eSIM»). null sin capacidad. */
    static String modeloDelGoods(String goods) {
        if (goods == null) return null;
        Matcher c = CAPACIDAD.matcher(goods);
        if (!c.find()) return null;
        String modelo = FABRICANTE.matcher(goods.substring(0, c.start()).strip()).replaceFirst("").strip();
        return modelo.isEmpty() ? null : modelo;
    }

    /** La capacidad de un Goods con espacio y la unidad en mayúsculas, como la lleva la plantilla (columna D; con espacio, NSYS la
     *  leyó bien en la subida del 22/10): «256GB» → «256 GB». null sin capacidad. */
    static String capacidadDelGoods(String goods) {
        if (goods == null) return null;
        Matcher c = CAPACIDAD.matcher(goods);
        return c.find() ? c.group(1) + " " + c.group(2).toUpperCase(Locale.ROOT) : null;
    }
}
