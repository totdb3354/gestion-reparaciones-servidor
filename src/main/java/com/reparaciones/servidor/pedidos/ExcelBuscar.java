package com.reparaciones.servidor.pedidos;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.PrintSetup;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

/** El Excel de «Descargar para buscar»: una hoja con dos tablas (SKU por pais con el total) de los moviles que aun hay que buscar,
 *  «Para hoy» y «Pueden esperar». Estilos y ajuste de impresion como {@code HojaImprimible}. */
final class ExcelBuscar {

    static final String SIN_PAIS = "—";
    static final String SIN_SKU = "(sin SKU)";
    static final String TITULO_HOY = "Para hoy";
    static final String TITULO_ESPERAR = "Pueden esperar";
    static final String VACIA = "No hay móviles que buscar";
    static final String TOTAL = "Total general";
    static final int ANCHO_SKU_MAXIMO = 40;
    static final int ANCHO_PAIS = 6;
    static final int ANCHO_TOTAL = 13;

    /** Los paises presentes (alfabeticos, «—» al final) y una fila por SKU. */
    record Tabla(List<String> paises, List<FilaTabla> filas) {
        int total() { return filas.stream().mapToInt(FilaTabla::total).sum(); }
    }

    record FilaTabla(String sku, Map<String, Integer> porPais, int total) {}

    private ExcelBuscar() {}

    static Tabla tabla(List<FilasDAO.PorBuscar> filas) {
        TreeSet<String> paisesConNombre = new TreeSet<>();
        boolean sinPais = false;
        // clave en minusculas -> (forma escrita -> veces) y (clave -> pais -> veces); TreeMap: las claves salen en orden alfabetico.
        Map<String, Map<String, Integer>> formas = new TreeMap<>();
        Map<String, Map<String, Integer>> porPais = new HashMap<>();
        for (FilasDAO.PorBuscar f : filas) {
            String sku = f.sku() == null || f.sku().isBlank() ? SIN_SKU : f.sku();
            String clave = sku.toLowerCase(Locale.ROOT);
            String pais = f.pais() == null || f.pais().isBlank() ? SIN_PAIS : f.pais();
            if (pais.equals(SIN_PAIS)) sinPais = true; else paisesConNombre.add(pais);
            formas.computeIfAbsent(clave, k -> new TreeMap<>()).merge(sku, 1, Integer::sum);
            porPais.computeIfAbsent(clave, k -> new HashMap<>()).merge(pais, 1, Integer::sum);
        }
        List<String> paises = new ArrayList<>(paisesConNombre);
        if (sinPais) paises.add(SIN_PAIS);
        List<FilaTabla> filasTabla = new ArrayList<>();
        for (var e : formas.entrySet()) {
            String texto = null;
            int mejor = 0;
            for (var forma : e.getValue().entrySet()) { // TreeMap: en un empate gana la primera alfabetica
                if (forma.getValue() > mejor) { mejor = forma.getValue(); texto = forma.getKey(); }
            }
            Map<String, Integer> cuentas = new LinkedHashMap<>();
            for (String p : paises) {
                Integer n = porPais.get(e.getKey()).get(p);
                if (n != null) cuentas.put(p, n);
            }
            filasTabla.add(new FilaTabla(texto, cuentas, cuentas.values().stream().mapToInt(Integer::intValue).sum()));
        }
        return new Tabla(paises, filasTabla);
    }

    static byte[] escribir(String nombreHoja, Tabla paraHoy, Tabla puedenEsperar) {
        try (XSSFWorkbook libro = new XSSFWorkbook(); ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            XSSFSheet hoja = libro.createSheet(nombreHoja);
            Estilos estilos = new Estilos(estilo(libro, true, false), estilo(libro, true, true), estilo(libro, false, true));
            int[] anchos = new int[Math.max(paraHoy.paises().size(), puedenEsperar.paises().size()) + 2];
            int fila = escribirTabla(hoja, 0, TITULO_HOY, paraHoy, estilos, anchos);
            escribirTabla(hoja, fila + 1, TITULO_ESPERAR, puedenEsperar, estilos, anchos);
            for (int c = 0; c < anchos.length; c++) {
                int ancho = c == 0 ? Math.min(Math.max(anchos[0], ANCHO_PAIS), ANCHO_SKU_MAXIMO) : Math.max(anchos[c], ANCHO_PAIS);
                hoja.setColumnWidth(c, (ancho + 2) * 256);
            }
            hoja.setFitToPage(true);
            XSSFPrintSetup impresion = hoja.getPrintSetup();
            impresion.setLandscape(false);
            impresion.setPaperSize(PrintSetup.A4_PAPERSIZE);
            impresion.setFitWidth((short) 1);
            impresion.setFitHeight((short) 0);
            libro.write(salida);
            return salida.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo escribir el Excel", e);
        }
    }

    private record Estilos(XSSFCellStyle titulo, XSSFCellStyle cabecera, XSSFCellStyle normal) {}

    /** Escribe una tabla desde {@code fila}; devuelve la primera fila libre despues de ella. */
    private static int escribirTabla(XSSFSheet hoja, int fila, String titulo, Tabla t, Estilos estilos, int[] anchos) {
        celdaTexto(hoja.createRow(fila++), 0, titulo, estilos.titulo());
        if (t.filas().isEmpty()) {
            hoja.createRow(fila++).createCell(0).setCellValue(VACIA);
            return fila;
        }
        int columnaTotal = t.paises().size() + 1;
        XSSFRow cab = hoja.createRow(fila++);
        celdaTexto(cab, 0, "SKU", estilos.cabecera());
        for (int i = 0; i < t.paises().size(); i++) celdaTexto(cab, i + 1, t.paises().get(i), estilos.cabecera());
        celdaTexto(cab, columnaTotal, TOTAL, estilos.cabecera());
        anchos[columnaTotal] = Math.max(anchos[columnaTotal], ANCHO_TOTAL);
        for (FilaTabla f : t.filas()) {
            XSSFRow r = hoja.createRow(fila++);
            celdaTexto(r, 0, f.sku(), estilos.normal());
            anchos[0] = Math.max(anchos[0], f.sku().length());
            for (int i = 0; i < t.paises().size(); i++) celdaNumero(r, i + 1, f.porPais().get(t.paises().get(i)), estilos.normal());
            celdaNumero(r, columnaTotal, f.total(), estilos.normal());
        }
        XSSFRow total = hoja.createRow(fila++);
        celdaTexto(total, 0, TOTAL, estilos.cabecera());
        for (int i = 0; i < t.paises().size(); i++) {
            String pais = t.paises().get(i);
            celdaNumero(total, i + 1, t.filas().stream().mapToInt(f -> f.porPais().getOrDefault(pais, 0)).sum(), estilos.cabecera());
        }
        celdaNumero(total, columnaTotal, t.total(), estilos.cabecera());
        return fila;
    }

    private static void celdaTexto(XSSFRow fila, int columna, String texto, XSSFCellStyle estilo) {
        XSSFCell c = fila.createCell(columna);
        c.setCellValue(texto);
        c.setCellStyle(estilo);
    }

    /** Un numero; vacio (con su borde) si es nulo o 0. */
    private static void celdaNumero(XSSFRow fila, int columna, Integer valor, XSSFCellStyle estilo) {
        XSSFCell c = fila.createCell(columna);
        c.setCellStyle(estilo);
        if (valor == null || valor == 0) c.setBlank(); else c.setCellValue(valor);
    }

    private static XSSFCellStyle estilo(XSSFWorkbook libro, boolean negrita, boolean borde) {
        XSSFCellStyle s = libro.createCellStyle();
        if (borde) {
            s.setBorderTop(BorderStyle.THIN);
            s.setBorderBottom(BorderStyle.THIN);
            s.setBorderLeft(BorderStyle.THIN);
            s.setBorderRight(BorderStyle.THIN);
        }
        if (negrita) {
            XSSFFont fuente = libro.createFont();
            fuente.setBold(true);
            s.setFont(fuente);
        }
        return s;
    }
}
