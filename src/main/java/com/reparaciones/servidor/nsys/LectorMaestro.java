package com.reparaciones.servidor.nsys;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/** Lee la hoja «Maestro producto NSYS» del maestro de productos (spec pieza 1 §4.4). Solo los iPhone tienen código de
 *  modelo del ERP: un iPad 11 nunca es un iPhone 11. */
public final class LectorMaestro {

    public static final String HOJA = "Maestro producto NSYS";
    static final List<String> COLUMNAS = List.of("Product Name Calidad", "Familia", "Modelo", "Capacidad", "Color",
            "Calidad Detalle", "SKU (stock web)", "SKU (Amazon)", "SKU sin grado");

    private LectorMaestro() {}

    public static ResultadoMaestro leer(InputStream xlsx) throws IOException {
        Recolector r = new Recolector();
        HojaXlsx.leer(xlsx, HOJA, r);
        if (r.columnas == null) throw new FormatoItemsException("La hoja «" + HOJA + "» está vacía");
        return new ResultadoMaestro(new ArrayList<>(r.productos.values()), r.conflictos, r.fuera);
    }

    private static final class Recolector implements HojaXlsx.Filas {
        Map<String, Integer> columnas;
        final Map<String, ProductoMaestro> productos = new LinkedHashMap<>();
        final List<String> conflictos = new ArrayList<>();
        final Set<String> fuera = new TreeSet<>();

        @Override public void fila(List<String> celdas) {
            if (columnas == null) {
                columnas = HojaXlsx.columnas(celdas, COLUMNAS, "el maestro");
                return;
            }
            String nombre = valor(celdas, "Product Name Calidad");
            if (nombre == null) return;
            String modeloMaestro = valor(celdas, "Modelo");
            String modelo = "iPhone".equals(valor(celdas, "Familia")) ? Normalizacion.modeloErp(modeloMaestro) : null;
            if (modelo == null && modeloMaestro != null && "iPhone".equals(valor(celdas, "Familia"))) fuera.add(modeloMaestro);
            ProductoMaestro p = new ProductoMaestro(Normalizacion.clave(nombre), modelo, modeloMaestro,
                    valor(celdas, "Capacidad"), valor(celdas, "Color"), valor(celdas, "Calidad Detalle"),
                    valor(celdas, "SKU (stock web)"), valor(celdas, "SKU (Amazon)"), valor(celdas, "SKU sin grado"),
                    Normalizacion.esEsim(nombre));
            ProductoMaestro previo = productos.get(p.clave());
            if (previo == null) {
                productos.put(p.clave(), p);
            } else if (!Objects.equals(previo.skuWeb(), p.skuWeb())) {
                conflictos.add(nombre);
                productos.put(p.clave(), new ProductoMaestro(previo.clave(), previo.modelo(), previo.modeloMaestro(),
                        previo.capacidad(), previo.color(), previo.calidadDetalle(), null, null, null, previo.esEsim()));
            }
        }

        private String valor(List<String> celdas, String columna) {
            return HojaXlsx.valor(celdas, columnas.get(columna));
        }
    }
}
