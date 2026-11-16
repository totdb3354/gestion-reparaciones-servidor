package com.reparaciones.servidor.pedidos;

import com.reparaciones.servidor.nsys.ImportadorNsys;
import org.apache.poi.ooxml.POIXMLException;
import org.apache.poi.openxml4j.exceptions.OpenXML4JException;
import org.apache.poi.openxml4j.exceptions.OpenXML4JRuntimeException;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.openxml4j.opc.PackageAccess;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.util.XMLHelper;
import org.apache.poi.xssf.eventusermodel.ReadOnlySharedStringsTable;
import org.apache.poi.xssf.eventusermodel.XSSFReader;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Lee la pestaña «Paso 1. Meter IMEI» de un libro de ventas de Amazon (.xlsx/.xlsm; spec «asociación de IMEI y pedido en Amazon»,
 *  «Lector de los libros»): cabecera en la fila 6, columnas por su nombre («order id», «purchase-date», «IMEI», sin mayusculas ni espacios
 *  de los extremos), una fila por movil. Lee en streaming, con el lector por eventos de POI (XSSFReader + SAX) sobre el fichero: solo se
 *  recorre esa pestaña, fila a fila, y solo se guardan los pares de las filas con fecha de compra desde {@code desde}. Las celdas se leen
 *  en crudo (el valor guardado, no el formateado): un IMEI numerico da sus 15 cifras aunque Excel lo guarde como 3.56789012345678E+14. */
public final class LibroVentas {

    public static final String PESTANA = "Paso 1. Meter IMEI";
    static final int FILA_CABECERA = 6;
    static final String ORDER_ID = "order id";
    static final String PURCHASE_DATE = "purchase-date";
    static final String IMEI = "IMEI";
    private static final List<String> COLUMNAS = List.of(ORDER_ID, PURCHASE_DATE, IMEI);
    private static final DateTimeFormatter DIA_MES_ANO = DateTimeFormatter.ofPattern("d/M/yyyy");

    private LibroVentas() {}

    /** Los pares pedido/IMEI de la pestaña, en el orden del libro, de las filas con «purchase-date» igual o posterior a {@code desde}.
     *  Se saltan las filas sin IMEI y las que no tienen una fecha que se entienda; el IMEI va sin espacios (los textos como
     *  «CANCELADO» pasan tal cual y los descarta la asociacion). Sin la pestaña, sin una cabecera o con un fichero que no se puede leer:
     *  {@link LibroVentasException}. */
    public static List<PeticionAsociaciones.Par> leer(Path fichero, LocalDate desde) throws IOException {
        try (OPCPackage paquete = OPCPackage.open(fichero.toFile(), PackageAccess.READ)) {
            XSSFReader lector = new XSSFReader(paquete);
            XSSFReader.SheetIterator hojas = (XSSFReader.SheetIterator) lector.getSheetsData();
            while (hojas.hasNext()) {
                try (InputStream hoja = hojas.next()) {
                    if (!PESTANA.equals(hojas.getSheetName())) continue;
                    Recorrido recorrido = new Recorrido(new ReadOnlySharedStringsTable(paquete), desde);
                    XMLReader xml = XMLHelper.newXMLReader();
                    xml.setContentHandler(recorrido);
                    xml.parse(new InputSource(hoja));
                    if (recorrido.columnas == null) recorrido.faltan(COLUMNAS);
                    return recorrido.pares;
                }
            }
            throw new LibroVentasException("falta la pestaña " + PESTANA);
        } catch (LibroVentasException e) {
            throw e;
        } catch (IOException | OpenXML4JException | SAXException | ParserConfigurationException | POIXMLException | OpenXML4JRuntimeException
                 | IllegalArgumentException e) {
            // zip truncado o corrupto, XML roto, no es OOXML...: el libro no se puede leer
            throw new LibroVentasException("el libro no se puede leer: " + e.getMessage());
        }
    }

    /** El IMEI de una celda: un numero, en sus cifras exactas a partir del valor crudo (3.56789012345678E+14 o 356789012345678.0 dan
     *  356789012345678; con decimales de verdad se deja como viene y lo descarta la asociacion); un texto, sin ningun espacio. */
    static String imei(Celda c) {
        if (c == null || c.texto() == null) return null;
        if (c.numerica()) return numero(c.texto());
        String sinEspacios = c.texto().replaceAll("[\\s\\u00A0]", "");
        return sinEspacios.isEmpty() ? null : sinEspacios;
    }

    /** El pedido de una celda: el texto recortado (o el numero en cifras, si Excel lo guardo como numero). */
    static String pedido(Celda c) {
        if (c == null || c.texto() == null) return null;
        String t = c.numerica() ? numero(c.texto()) : c.texto().strip();
        return t.isEmpty() ? null : t;
    }

    private static String numero(String crudo) {
        try {
            BigDecimal n = new BigDecimal(crudo.strip()).stripTrailingZeros();
            return n.scale() <= 0 ? n.toBigInteger().toString() : n.toPlainString();
        } catch (NumberFormatException e) {
            return crudo.strip();
        }
    }

    /** El dia de compra: una fecha de Excel (numero de serie) o un texto «2026-11-13T10:22:33+00:00» (con zona: el dia en Madrid),
     *  «2026-11-13» (con o sin hora detras) o «13/11/2026» (con o sin hora detras). null si no se entiende. */
    static LocalDate fecha(Celda c) {
        if (c == null || c.texto() == null || c.texto().isBlank()) return null;
        String t = c.texto().strip();
        if (c.numerica()) {
            try {
                double serie = Double.parseDouble(t);
                return DateUtil.isValidExcelDate(serie) ? DateUtil.getLocalDateTime(serie, false).toLocalDate() : null;
            } catch (NumberFormatException e) {
                return null;
            }
        }
        try {
            return OffsetDateTime.parse(t).atZoneSameInstant(ImportadorNsys.MADRID).toLocalDate();
        } catch (DateTimeParseException sinZona) {
            // se prueba sin zona
        }
        String dia = t.split("[ T]", 2)[0];
        try {
            return dia.contains("/") ? LocalDate.parse(dia, DIA_MES_ANO) : LocalDate.parse(dia);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /** Una celda en crudo: {@code texto} es el valor guardado (el numero tal cual en el XML si {@code numerica}). */
    record Celda(String texto, boolean numerica) {}

    private static String nombre(String cabecera) {
        return cabecera == null ? "" : cabecera.strip().toLowerCase(Locale.ROOT);
    }

    /** El SAX de la hoja: junta las celdas de cada fila (valor crudo, textos compartidos resueltos) y la trata al cerrarla. */
    private static final class Recorrido extends DefaultHandler {
        private final ReadOnlySharedStringsTable textos;
        private final LocalDate desde;
        final List<PeticionAsociaciones.Par> pares = new ArrayList<>();
        Map<String, Integer> columnas;

        private final Map<Integer, Celda> fila = new HashMap<>();
        private int numeroFila;
        private int columna;
        private int siguienteColumna;
        private String tipo;
        private final StringBuilder valor = new StringBuilder();
        private boolean conValor;
        private boolean juntando;

        Recorrido(ReadOnlySharedStringsTable textos, LocalDate desde) {
            this.textos = textos;
            this.desde = desde;
        }

        @Override public void startElement(String uri, String local, String qName, Attributes atributos) {
            switch (local) {
                case "row" -> {
                    String r = atributos.getValue("r");
                    numeroFila = r == null ? numeroFila + 1 : Integer.parseInt(r);
                    fila.clear();
                    siguienteColumna = 0;
                }
                case "c" -> {
                    String r = atributos.getValue("r");
                    columna = r == null ? siguienteColumna : new CellReference(r).getCol();
                    siguienteColumna = columna + 1;
                    tipo = atributos.getValue("t");
                    valor.setLength(0);
                    conValor = false;
                }
                case "v", "t" -> {
                    juntando = true;
                    conValor = true;
                }
                default -> { }
            }
        }

        @Override public void characters(char[] ch, int inicio, int largo) {
            if (juntando) valor.append(ch, inicio, largo);
        }

        @Override public void endElement(String uri, String local, String qName) {
            switch (local) {
                case "v", "t" -> juntando = false;
                case "c" -> {
                    if (conValor) fila.put(columna, celda());
                }
                case "row" -> fila();
                default -> { }
            }
        }

        private Celda celda() {
            String crudo = valor.toString();
            if (tipo == null || tipo.equals("n")) return new Celda(crudo, true);
            return switch (tipo) {
                case "s" -> new Celda(textos.getItemAt(Integer.parseInt(crudo.strip())).getString(), false);
                case "b" -> new Celda("1".equals(crudo.strip()) ? "TRUE" : "FALSE", false);
                case "e" -> new Celda(null, false);
                default -> new Celda(crudo, false);   // inlineStr, str (resultado de formula en texto)
            };
        }

        private void fila() {
            if (numeroFila < FILA_CABECERA) return;
            if (columnas == null) {
                if (numeroFila > FILA_CABECERA) faltan(COLUMNAS);   // la fila 6 no estaba (vacia)
                cabecera();
                return;
            }
            String pedido = pedido(fila.get(columnas.get(ORDER_ID)));
            String imei = imei(fila.get(columnas.get(IMEI)));
            if (imei == null) return;                       // sin IMEI aun (o fila vacia): nada que asociar
            LocalDate dia = fecha(fila.get(columnas.get(PURCHASE_DATE)));
            if (dia == null || dia.isBefore(desde)) return;
            pares.add(new PeticionAsociaciones.Par(pedido, imei));
        }

        private void cabecera() {
            Map<String, Integer> porNombre = new HashMap<>();
            fila.forEach((col, c) -> {
                if (c.texto() != null) porNombre.merge(nombre(c.texto()), col, Math::min);
            });
            List<String> faltan = COLUMNAS.stream().filter(n -> !porNombre.containsKey(nombre(n))).toList();
            if (!faltan.isEmpty()) faltan(faltan);
            columnas = new HashMap<>();
            for (String n : COLUMNAS) columnas.put(n, porNombre.get(nombre(n)));
        }

        void faltan(List<String> nombres) {
            throw new LibroVentasException(nombres.size() == 1
                    ? "falta la columna " + nombres.get(0) + " en " + PESTANA
                    : "faltan las columnas " + String.join(", ", nombres) + " en " + PESTANA);
        }
    }
}
