package com.reparaciones.servidor.nsys;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/** Traduce lo que dice NSYS a lo que entiende el ERP: el producto del maestro y la ubicación de cada caja. */
public final class Traductor {

    private final Map<String, ProductoMaestro> productos = new HashMap<>();
    private final Map<String, UbicacionNsys> cajas = new HashMap<>();

    public Traductor(Collection<ProductoMaestro> productos, Collection<UbicacionNsys> cajas) {
        for (ProductoMaestro p : productos) this.productos.put(p.clave(), p);
        for (UbicacionNsys u : cajas) this.cajas.put(Normalizacion.caja(u.location()), u);
    }

    /** El producto del maestro para «Goods» + «Grade»; null si el maestro no lo tiene. */
    public ProductoMaestro producto(String goods, String grade) {
        return productos.get(Normalizacion.clave(goods, grade));
    }

    /** La traducción de una caja; null si no está en Nsys_ubicacion (caja nueva: se trata como «no se trae»). */
    public UbicacionNsys caja(String location) {
        return cajas.get(Normalizacion.caja(location));
    }
}
