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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Lee la hoja «Maestro producto NSYS» del maestro de productos (spec pieza 1 §4.4). Solo los iPhone tienen código de
 *  modelo del ERP: un iPad 11 nunca es un iPhone 11. */
public final class LectorMaestro {

    public static final String HOJA = "Maestro producto NSYS";
    static final List<String> COLUMNAS = List.of("Product Name Calidad", "Familia", "Modelo", "Capacidad", "Color",
            "Calidad Detalle", "SKU (stock web)", "SKU (Amazon)", "SKU sin grado");
    /** La columna con el nombre del producto sin el grado (0.9.22). Opcional: un maestro sin ella se carga como antes. */
    static final String NOMBRE = "Product Name";
    private static final Logger log = LoggerFactory.getLogger(LectorMaestro.class);

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
                if (!columnas.containsKey(NOMBRE))
                    log.warn("NSYS: el maestro no trae la columna «{}»: se carga sin nombres de producto", NOMBRE);
                return;
            }
            String nombre = valor(celdas, "Product Name Calidad");
            if (nombre == null) return;
            String modeloMaestro = valor(celdas, "Modelo", AnchosNsys.MODELO_MAESTRO);
            String modelo = "iPhone".equals(valor(celdas, "Familia")) ? Normalizacion.modeloErp(modeloMaestro) : null;
            if (modelo == null && modeloMaestro != null && "iPhone".equals(valor(celdas, "Familia"))) fuera.add(modeloMaestro);
            // La clave ya sale cortada al ancho de CLAVE (Normalizacion.clave), igual que al buscar un «Goods + Grade».
            ProductoMaestro p = new ProductoMaestro(Normalizacion.clave(nombre), modelo, modeloMaestro,
                    valor(celdas, "Capacidad", AnchosNsys.CAPACIDAD), valor(celdas, "Color", AnchosNsys.COLOR),
                    valor(celdas, "Calidad Detalle", AnchosNsys.CALIDAD_DETALLE),
                    valor(celdas, "SKU (stock web)", AnchosNsys.SKU), valor(celdas, "SKU (Amazon)", AnchosNsys.SKU),
                    valor(celdas, "SKU sin grado", AnchosNsys.SKU), Normalizacion.esEsim(nombre),
                    valor(celdas, NOMBRE, AnchosNsys.NOMBRE));
            ProductoMaestro previo = productos.get(p.clave());
            if (previo == null) {
                productos.put(p.clave(), p);
            } else if (!Objects.equals(previo.skuWeb(), p.skuWeb())) {
                conflictos.add(nombre);
                productos.put(p.clave(), new ProductoMaestro(previo.clave(), previo.modelo(), previo.modeloMaestro(),
                        previo.capacidad(), previo.color(), previo.calidadDetalle(), null, null, null, previo.esEsim(),
                        previo.nombre()));
            }
        }

        private String valor(List<String> celdas, String columna) {
            return HojaXlsx.valor(celdas, columnas.get(columna));
        }

        /** Cortado al ancho de su columna en Nsys_producto. */
        private String valor(List<String> celdas, String columna, int ancho) {
            return AnchosNsys.corta(valor(celdas, columna), ancho);
        }
    }
}
