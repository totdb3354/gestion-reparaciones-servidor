package com.reparaciones.servidor.nsys;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Lee el fichero Items de NSYS (spec pieza 1 §5.1): valida las 19 columnas por su nombre y se queda con las que usa el
 *  ERP. Desde la 0.9.22 lee también el coste («Total cost»); los precios de venta no se leen. */
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

    /** Tope de DECIMAL(10,2): el valor absoluto del coste es menor que esto. */
    private static final BigDecimal MAX_COSTE = new BigDecimal("100000000");

    /** El coste de NSYS («Total cost»): un número con punto o coma decimal, a dos decimales; null si no viene, no es un número o
     *  no cabe en DECIMAL(10,2) (un IMEI pegado en la celda, por ejemplo), para que no tumbe el guardado de todos los costes. */
    static BigDecimal coste(String texto) {
        if (texto == null || texto.isBlank()) return null;
        try {
            BigDecimal coste = new BigDecimal(texto.strip().replace(',', '.')).setScale(2, RoundingMode.HALF_UP);
            return coste.abs().compareTo(MAX_COSTE) < 0 ? coste : null;
        } catch (NumberFormatException noEsUnNumero) {
            return null;
        }
    }

    private static final class Recolector implements HojaXlsx.Filas {
        Map<String, Integer> columnas;
        final List<FilaItems> filas = new ArrayList<>();

        @Override public void fila(List<String> celdas) {
            if (columnas == null) {
                columnas = HojaXlsx.columnas(celdas, CABECERAS, "el fichero de NSYS");
                return;
            }
            String imei = valor(celdas, "IMEI/SN", AnchosNsys.IMEI_SN);
            if (imei == null) return;                       // filas vacías o de pie
            String lote = valor(celdas, "Batch", AnchosNsys.BATCH);
            filas.add(new FilaItems(imei, lote == null ? "" : lote, valor(celdas, "Goods", AnchosNsys.GOODS),
                    valor(celdas, "Grade", AnchosNsys.GRADE), valor(celdas, "Status", AnchosNsys.STATUS),
                    valor(celdas, "Location", AnchosNsys.LOCATION), valor(celdas, "Supplier", AnchosNsys.SUPPLIER),
                    fecha(valor(celdas, "Receiving date")), fecha(valor(celdas, "Sold date")),
                    coste(valor(celdas, "Total cost"))));
        }

        private String valor(List<String> celdas, String columna) {
            return HojaXlsx.valor(celdas, columnas.get(columna));
        }

        /** Cortado al ancho de su columna en Nsys_movil: la clave y las comparaciones van con lo que guarda la base. */
        private String valor(List<String> celdas, String columna, int ancho) {
            return AnchosNsys.corta(valor(celdas, columna), ancho);
        }
    }
}
