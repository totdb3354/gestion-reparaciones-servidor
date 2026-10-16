package com.reparaciones.servidor.nsys;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Lee el fichero Items de NSYS (spec pieza 1 §5.1): valida las 19 columnas por su nombre y se queda con las que usa el
 *  ERP. Los costes y precios no se leen. */
public final class LectorItems {

    static final List<String> CABECERAS = List.of("IMEI/SN", "Goods", "SKU", "Grade", "Status", "Warehouse", "Supplier",
            "Batch", "Release", "Reserve", "Diagnostics", "Total cost", "Selling price", "Sold price", "Margin",
            "Receiving date", "Sold date", "Tags", "Location");

    private static final List<DateTimeFormatter> FORMATOS = List.of(DateTimeFormatter.ofPattern("M/d/yyyy"),
            DateTimeFormatter.ofPattern("M/d/yy"), DateTimeFormatter.ISO_LOCAL_DATE);

    private LectorItems() {}

    public static List<FilaItems> leer(InputStream xlsx) throws IOException {
        Recolector r = new Recolector();
        HojaXlsx.leer(xlsx, null, r);
        if (r.columnas == null) throw new FormatoItemsException("El fichero de NSYS está vacío");
        return r.filas;
    }

    /** Las fechas de NSYS van en texto americano ("10/15/2026"); si traen hora, solo cuenta el día. null si no se entiende. */
    static LocalDate fecha(String texto) {
        if (texto == null) return null;
        String t = texto.strip();
        int corte = t.indexOf(' ');
        if (corte > 0) t = t.substring(0, corte);
        corte = t.indexOf('T');
        if (corte > 0) t = t.substring(0, corte);
        for (DateTimeFormatter formato : FORMATOS) {
            try {
                return LocalDate.parse(t, formato);
            } catch (DateTimeParseException otroFormato) {
                // se prueba el siguiente
            }
        }
        return null;
    }

    private static final class Recolector implements HojaXlsx.Filas {
        Map<String, Integer> columnas;
        final List<FilaItems> filas = new ArrayList<>();

        @Override public void fila(List<String> celdas) {
            if (columnas == null) {
                columnas = HojaXlsx.columnas(celdas, CABECERAS, "el fichero de NSYS");
                return;
            }
            String imei = valor(celdas, "IMEI/SN");
            if (imei == null) return;                       // filas vacías o de pie
            String lote = valor(celdas, "Batch");
            filas.add(new FilaItems(imei, lote == null ? "" : lote, valor(celdas, "Goods"), valor(celdas, "Grade"),
                    valor(celdas, "Status"), valor(celdas, "Location"), valor(celdas, "Supplier"),
                    fecha(valor(celdas, "Receiving date")), fecha(valor(celdas, "Sold date"))));
        }

        private String valor(List<String> celdas, String columna) {
            return HojaXlsx.valor(celdas, columnas.get(columna));
        }
    }
}
