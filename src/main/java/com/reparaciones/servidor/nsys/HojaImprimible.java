package com.reparaciones.servidor.nsys;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.PrintSetup;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFPrintSetup;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.regex.Pattern;

/** Una hoja de Excel para imprimir (0.9.25, spec «Qué revisar» §10.3): una cabecera y filas de textos, tal cual las manda la web
 *  (§10.15: un entero de 1 a 9 cifras sin ceros a la izquierda va como número). No sabe nada de la lista que imprime.
 *  Cabecera en negrita y repetida en cada página, en vertical si la suma de anchos cabe en {@link #ANCHO_VERTICAL_MAXIMO} y apaisada si no, ajustado a una página de ancho,
 *  bordes finos en todas las celdas (también las vacías, para escribir a boli) y cada columna tan ancha como su texto más largo. */
final class HojaImprimible {

    static final int MAX_FILAS = 5000;
    static final int MAX_COLUMNAS = 12;
    static final int MAX_CELDA = 200;
    static final int MAX_HOJA = 31;
    /** Una columna no pasa de tantos caracteres de ancho: un texto más largo se corta al imprimir, no ensancha la hoja. */
    static final int ANCHO_MAXIMO = 60;
    /** Spec «Qué revisar» §10.15: con la suma de los anchos (en caracteres) en este valor o menos, la hoja sale en vertical; si pasa, apaisada. */
    static final int ANCHO_VERTICAL_MAXIMO = 90;
    /** Un entero de 1 a 9 cifras, sin ceros a la izquierda, signo ni espacios: lo único que se escribe como número (un IMEI o «007» siguen siendo texto). */
    private static final Pattern NUMERO = Pattern.compile("^(0|[1-9]\\d{0,8})$");
    /** Lo que Excel no admite en el nombre de una hoja. */
    private static final Pattern PROHIBIDOS = Pattern.compile("[\\[\\]:*?/\\\\]");

    private HojaImprimible() {}

    /** null si la petición vale; si no, el motivo en castellano, para el 400. */
    static String error(String hoja, List<String> columnas, List<List<String>> filas) {
        if (hoja == null || hoja.isBlank()) return "Falta el nombre de la hoja";
        if (hoja.length() > MAX_HOJA) return "El nombre de la hoja pasa de " + MAX_HOJA + " caracteres";
        if (PROHIBIDOS.matcher(hoja).find()) return "El nombre de la hoja no puede llevar [ ] : * ? / \\";
        if (hoja.startsWith("'") || hoja.endsWith("'")) return "El nombre de la hoja no puede empezar ni acabar con un apóstrofo";
        if (columnas == null || columnas.isEmpty() || columnas.size() > MAX_COLUMNAS)
            return "La hoja lleva de 1 a " + MAX_COLUMNAS + " columnas";
        for (String c : columnas) {
            if (c == null || c.isBlank()) return "Una columna no tiene nombre";
            if (c.length() > MAX_CELDA) return "El nombre de una columna pasa de " + MAX_CELDA + " caracteres";
        }
        if (filas == null) return "Faltan las filas";
        if (filas.size() > MAX_FILAS) return "Como mucho " + MAX_FILAS + " filas";
        for (int i = 0; i < filas.size(); i++) {
            List<String> fila = filas.get(i);
            if (fila == null || fila.size() != columnas.size())
                return "La fila " + (i + 1) + " no tiene " + columnas.size() + " celdas";
            for (String celda : fila) {
                if (celda == null) return "La fila " + (i + 1) + " lleva una celda nula";
                if (celda.length() > MAX_CELDA) return "Una celda de la fila " + (i + 1) + " pasa de " + MAX_CELDA + " caracteres";
            }
        }
        return null;
    }

    /** El xlsx; la petición ya ha pasado por {@link #error}. */
    static byte[] escribir(String nombreHoja, List<String> columnas, List<List<String>> filas) {
        try (XSSFWorkbook libro = new XSSFWorkbook(); ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            XSSFSheet hoja = libro.createSheet(nombreHoja);
            XSSFCellStyle cabecera = estilo(libro, true);
            XSSFCellStyle normal = estilo(libro, false);
            int[] anchos = new int[columnas.size()];
            escribirFila(hoja.createRow(0), columnas, cabecera, anchos, false);
            for (int i = 0; i < filas.size(); i++) escribirFila(hoja.createRow(i + 1), filas.get(i), normal, anchos, true);
            int suma = 0;
            for (int c = 0; c < anchos.length; c++) {
                int ancho = Math.min(anchos[c], ANCHO_MAXIMO) + 2;
                hoja.setColumnWidth(c, ancho * 256);
                suma += ancho;
            }
            hoja.setRepeatingRows(CellRangeAddress.valueOf("1:1"));
            hoja.setFitToPage(true);
            XSSFPrintSetup impresion = hoja.getPrintSetup();
            impresion.setLandscape(suma > ANCHO_VERTICAL_MAXIMO);
            impresion.setPaperSize(PrintSetup.A4_PAPERSIZE);
            impresion.setFitWidth((short) 1);
            impresion.setFitHeight((short) 0);
            libro.write(salida);
            return salida.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo escribir el Excel", e);
        }
    }

    /** Cada celda como texto (un IMEI no pasa a número) salvo, en las filas de datos, un entero corto, que va como número; con borde; vacía, en blanco pero con su borde. */
    private static void escribirFila(XSSFRow fila, List<String> valores, XSSFCellStyle estilo, int[] anchos, boolean datos) {
        for (int c = 0; c < valores.size(); c++) {
            String valor = valores.get(c);
            XSSFCell celda = fila.createCell(c);
            celda.setCellStyle(estilo);
            if (valor.isEmpty()) celda.setBlank();
            else if (datos && NUMERO.matcher(valor).matches()) celda.setCellValue(Long.parseLong(valor));
            else celda.setCellValue(valor);
            anchos[c] = Math.max(anchos[c], valor.length());
        }
    }

    private static XSSFCellStyle estilo(XSSFWorkbook libro, boolean negrita) {
        XSSFCellStyle s = libro.createCellStyle();
        s.setBorderTop(BorderStyle.THIN);
        s.setBorderBottom(BorderStyle.THIN);
        s.setBorderLeft(BorderStyle.THIN);
        s.setBorderRight(BorderStyle.THIN);
        if (negrita) {
            XSSFFont fuente = libro.createFont();
            fuente.setBold(true);
            s.setFont(fuente);
        }
        return s;
    }
}
