package com.reparaciones.servidor.nsys;

import org.apache.poi.ooxml.POIXMLException;
import org.apache.poi.openxml4j.exceptions.OpenXML4JException;
import org.apache.poi.openxml4j.exceptions.OpenXML4JRuntimeException;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.openxml4j.opc.PackageAccess;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.util.XMLHelper;
import org.apache.poi.xssf.eventusermodel.ReadOnlySharedStringsTable;
import org.apache.poi.xssf.eventusermodel.XSSFReader;
import org.apache.poi.xssf.eventusermodel.XSSFSheetXMLHandler;
import org.apache.poi.xssf.model.StylesTable;
import org.apache.poi.xssf.usermodel.XSSFComment;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Recorre una hoja de un .xlsx fila a fila (SAX), sin cargar el libro entero en memoria. */
final class HojaXlsx {

    /** Recibe cada fila como lista de textos: null en las celdas vacías; los números y fechas, tal como se ven. */
    interface Filas {
        void fila(List<String> celdas);
    }

    private HojaXlsx() {}

    /** Recorre la hoja {@code nombreHoja}, o la primera si es null. El flujo se pasa antes a un fichero temporal: abrir el
     *  paquete desde un InputStream descomprime todas las partes en memoria (decenas de MB de XML con 70.000 filas),
     *  mientras que desde un fichero cada parte se lee del zip solo cuando se recorre. Un fichero roto es contenido
     *  (FormatoItemsException); un fallo al escribir el temporal es de infraestructura (IOException). */
    static void leer(InputStream xlsx, String nombreHoja, Filas destino) throws IOException {
        Path temporal = Files.createTempFile("nsys-", ".xlsx");
        try {
            Files.copy(xlsx, temporal, StandardCopyOption.REPLACE_EXISTING);
            recorrer(temporal, nombreHoja, destino);
        } finally {
            Files.deleteIfExists(temporal);
        }
    }

    private static void recorrer(Path fichero, String nombreHoja, Filas destino) throws IOException {
        try (OPCPackage paquete = OPCPackage.open(fichero.toFile(), PackageAccess.READ)) {
            XSSFReader lector = new XSSFReader(paquete);
            ReadOnlySharedStringsTable textos = new ReadOnlySharedStringsTable(paquete);
            StylesTable estilos = lector.getStylesTable();
            XSSFReader.SheetIterator hojas = (XSSFReader.SheetIterator) lector.getSheetsData();
            while (hojas.hasNext()) {
                try (InputStream hoja = hojas.next()) {
                    if (nombreHoja != null && !nombreHoja.equals(hojas.getSheetName())) continue;
                    XMLReader xml = XMLHelper.newXMLReader();
                    xml.setContentHandler(new XSSFSheetXMLHandler(estilos, textos, new Recolector(destino),
                            new DataFormatter(Locale.ROOT), false));
                    xml.parse(new InputSource(hoja));
                    return;
                }
            }
            throw new FormatoItemsException(nombreHoja == null ? "El fichero no tiene hojas" : "Falta la hoja «" + nombreHoja + "»");
        } catch (IOException | OpenXML4JException | SAXException | ParserConfigurationException | POIXMLException | OpenXML4JRuntimeException
                 | IllegalArgumentException e) {
            // zip truncado o corrupto, XML roto, referencia de celda inválida, no es OOXML...: el contenido no vale
            throw new FormatoItemsException("El fichero no es un .xlsx válido: " + e.getMessage());
        }
    }

    /** Índice de cada columna por su nombre (sin espacios a los lados); si falta alguna de {@code necesarias}, se rechaza. */
    static Map<String, Integer> columnas(List<String> cabecera, List<String> necesarias, String deQue) {
        Map<String, Integer> indices = new HashMap<>();
        for (int i = 0; i < cabecera.size(); i++) {
            String nombre = cabecera.get(i);
            if (nombre != null) indices.putIfAbsent(nombre.strip(), i);
        }
        List<String> faltan = necesarias.stream().filter(c -> !indices.containsKey(c)).toList();
        if (!faltan.isEmpty()) throw new FormatoItemsException("Faltan columnas en " + deQue + ": " + String.join(", ", faltan));
        return indices;
    }

    static String valor(List<String> celdas, Integer indice) {
        return indice == null || indice >= celdas.size() ? null : celdas.get(indice);
    }

    private static final class Recolector implements XSSFSheetXMLHandler.SheetContentsHandler {
        private final Filas destino;
        private final List<String> fila = new ArrayList<>();
        private int siguiente;

        Recolector(Filas destino) {
            this.destino = destino;
        }

        @Override public void startRow(int numero) {
            fila.clear();
            siguiente = 0;
        }

        @Override public void endRow(int numero) {
            destino.fila(new ArrayList<>(fila));
        }

        @Override public void cell(String referencia, String valor, XSSFComment comentario) {
            int columna = referencia == null ? siguiente : new CellReference(referencia).getCol();
            while (fila.size() < columna) fila.add(null);
            fila.add(valor == null || valor.isBlank() ? null : valor.strip());
            siguiente = columna + 1;
        }

        @Override public void headerFooter(String texto, boolean esCabecera, String etiqueta) {}
    }
}
