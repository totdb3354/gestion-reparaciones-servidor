package com.reparaciones.servidor.nsys;

import java.util.Map;
import java.util.function.Supplier;

/** El producto («Goods») que hay que poner en NSYS cuando el taller cambia el color de un móvil (spec 0.9.22 §2.1). Es puro. */
final class ProductoNsys {

    private ProductoNsys() {}

    /** Para el color {@code color} del maestro: 1) el Goods más usado de ese color entre los móviles del mismo modelo, capacidad
     *  y eSIM ({@code goodsMismoModelo}, de NsysDAO#goodsMasUsados); 2-3) si no hay, el Goods del propio móvil con el color
     *  cambiado por su nombre en inglés del maestro (Maestro#nombreColor) escrito como lo escribe NSYS en otra capacidad del
     *  mismo modelo o en otro modelo (GrafiasNsys#color). null si el maestro no da el nombre o NSYS nunca lo ha escrito: la web
     *  dice «búscalo a mano» (el nombre del maestro tal cual llega con la pieza 2). El maestro y las grafías solo se leen si
     *  hacen falta. */
    static String paraColor(MovilNsys m, String color, Map<String, String> goodsMismoModelo, Supplier<Maestro> maestro,
                            Supplier<GrafiasNsys> grafias) {
        if (color == null) return null;
        String exacto = goodsMismoModelo.get(color);
        if (exacto != null) return exacto;
        if (GrafiasNsys.colorDelGoods(m.goods()) == null) return null;
        String nombre = maestro.get().nombreColor(m, color);
        if (nombre == null) return null;
        String grafia = grafias.get().color(m.modeloMaestro(), nombre);
        return grafia == null ? null : GrafiasNsys.cambiarColor(m.goods(), grafia);
    }
}
