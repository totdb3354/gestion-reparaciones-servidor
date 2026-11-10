package com.reparaciones.servidor.nsys;

import java.util.Locale;

/** El SKU de un móvil sin grado (spec 0.9.30 §3): el maestro no da SKU web para el grado «n/a», así que el programa pone el
 *  «SKU sin grado» de su fila (modelo + capacidad + color) con «NA» en el sitio del grado: «i13128AzulNA». Si el maestro da
 *  SKU, manda el del maestro. Solo para «n/a» y en blanco: un código desconocido sigue sin SKU. Es puro. */
public final class SkuNa {

    static final String SUFIJO = "NA";
    private static final String ESIM = "eSIM";

    private SkuNa() {}

    /** Sin grado en NSYS: «n/a» (sin distinguir mayúsculas ni espacios a los lados), en blanco o null. Más estrecho que
     *  {@code GradoTaller.Grupo.COMO_NA}, que incluye también los códigos desconocidos. */
    public static boolean esNa(String grade) {
        return grade == null || grade.isBlank() || grade.strip().equalsIgnoreCase("n/a");
    }

    /** El SKU de un móvil con la fila del maestro de su «Goods + Grade»: el SKU web si lo trae; si no, el calculado; si
     *  tampoco, lo que traiga el maestro (nada). null sin fila. */
    public static String de(ProductoMaestro p, String grade) {
        if (p == null) return null;
        if (p.skuWeb() != null && !p.skuWeb().isBlank()) return p.skuWeb();
        String calculado = calculado(p, grade);
        return calculado != null ? calculado : p.skuWeb();
    }

    /** El «SKU sin grado» de la fila + «NA», si el grado es «n/a» o en blanco. null si no lo es, si la fila no trae «SKU
     *  sin grado», si es un eSIM y no se sabe dónde va «eSIM», o si no cabe en la columna. */
    public static String calculado(ProductoMaestro p, String grade) {
        if (p == null || !esNa(grade) || p.skuSinGrado() == null || p.skuSinGrado().isBlank()) return null;
        String base = p.skuSinGrado().strip();
        if (p.esEsim()) base = conEsim(base, p.capacidad());
        if (base == null) return null;
        String sku = base + SUFIJO;
        return sku.length() > AnchosNsys.SKU ? null : sku;
    }

    /** El «SKU sin grado» de un eSIM no lleva «eSIM» («i14128Medianoche» para el SKU web «i14eSIM128MedianocheA100»): va
     *  justo delante de la capacidad (la última vez que aparece: «i12128Negro» → «i12eSIM128Negro»). null si la capacidad no
     *  aparece: mejor sin SKU que uno mal formado. */
    static String conEsim(String base, String capacidad) {
        String enMinusculas = base.toLowerCase(Locale.ROOT);
        if (enMinusculas.contains("esim")) return base;
        if (capacidad == null) return null;
        String cap = capacidad.replaceAll("\\s+", "").replaceFirst("(?i)GB$", "").toLowerCase(Locale.ROOT);
        if (cap.isEmpty()) return null;
        int en = enMinusculas.lastIndexOf(cap);
        return en < 0 ? null : base.substring(0, en) + ESIM + base.substring(en);
    }
}
