package com.reparaciones.servidor.util;

import java.util.*;

/**
 * Tipo de pieza y modelo de una referencia (0.9.9, spec 2026-10-12 §4.1). El tipo es el prefijo de la referencia (lo
 * que va antes de la primera «i»: {@code bati13} → {@code bat}) y el modelo, el código de {@link #MODELOS_ORDENADOS}
 * más largo que sea prefijo de lo que queda ({@code bati13promax} → {@code 13promax}, no {@code 13}).
 */
public final class CatalogoPiezas {

    /** Calco de MODELOS_ORDENADOS de la web (src/modules/taller/lib/modelos.ts): mismos códigos y mismo orden (el de la
     *  tienda de Apple). Un modelo nuevo (p. ej. el 17e) se añade en los DOS sitios. */
    public static final List<String> MODELOS_ORDENADOS = List.of(
            "6s", "6splus", "7", "7plus", "8", "8plus", "se2020",
            "x", "xr", "xs", "xsmax",
            "11", "11pro", "11promax",
            "12", "12mini", "12pro", "12promax",
            "13", "13mini", "13pro", "13promax",
            "14", "14plus", "14pro", "14promax",
            "15", "15plus", "15pro", "15promax",
            "16", "16e", "16plus", "16pro", "16promax",
            "17", "air", "17pro", "17promax");

    /** Orden de los tipos del formulario de reparación; los demás van detrás, por orden alfabético. */
    public static final List<String> ORDEN_TIPOS = List.of("bat", "cha", "tapa", "g", "mc", "lcd");

    private CatalogoPiezas() {}

    /** Prefijo del tipo de pieza: lo que va antes de la primera «i»; «otro…» es «otro». Sin «i», la referencia entera. */
    public static String prefijo(String tipo) {
        if (tipo.startsWith("otro")) return "otro";
        int idx = tipo.indexOf('i');
        return idx > 0 ? tipo.substring(0, idx) : tipo;
    }

    /** Código de modelo (calco de extraerModelo de la web): sin el prefijo y sin la «i» que lo sigue, gana el código más
     *  largo que sea prefijo de lo que queda. {@code null} si ninguno lo es. */
    public static String modelo(String tipo, String prefijo) {
        String minusculas = tipo.toLowerCase(Locale.ROOT);
        String resto = minusculas.length() > prefijo.length() ? minusculas.substring(prefijo.length()) : "";
        if (resto.startsWith("i")) resto = resto.substring(1);
        String mejor = null;
        for (String m : MODELOS_ORDENADOS) {
            if (resto.startsWith(m) && (mejor == null || m.length() > mejor.length())) mejor = m;
        }
        return mejor;
    }

    public static boolean esModelo(String codigo) {
        return MODELOS_ORDENADOS.contains(codigo);
    }

    /** Los tipos en el orden del formulario y, detrás, el resto por orden alfabético; sin repetidos. */
    public static List<String> ordenarTipos(Collection<String> tipos) {
        List<String> lista = new ArrayList<>(new TreeSet<>(tipos));
        lista.sort(Comparator.comparingInt(p -> posicion(ORDEN_TIPOS, p)));
        return lista;
    }

    /** Los modelos en el orden de la tienda; sin repetidos. */
    public static List<String> ordenarModelos(Collection<String> modelos) {
        List<String> lista = new ArrayList<>(new TreeSet<>(modelos));
        lista.sort(Comparator.comparingInt(m -> posicion(MODELOS_ORDENADOS, m)));
        return lista;
    }

    /** Si un proveedor con estos tipos (prefijo → modelos excluidos) vende la pieza {@code tipo} (spec §4.2): tiene su
     *  tipo y su modelo no está excluido. Una pieza sin modelo reconocible la vende quien tenga su tipo. */
    public static boolean vende(Map<String, Set<String>> tipos, String tipo) {
        String p = prefijo(tipo);
        Set<String> excluidos = tipos.get(p);
        if (excluidos == null) return false;
        String m = modelo(tipo, p);
        return m == null || !excluidos.contains(m);
    }

    /** Posición en la lista; lo que no está, al final (la ordenación es estable: conserva el orden alfabético). */
    private static int posicion(List<String> orden, String valor) {
        int i = orden.indexOf(valor);
        return i >= 0 ? i : orden.size();
    }
}
