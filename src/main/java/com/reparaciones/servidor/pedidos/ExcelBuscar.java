package com.reparaciones.servidor.pedidos;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.CreationHelper;
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
import java.time.LocalDate;
import java.util.Comparator;
import java.util.EnumMap;
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

    static final String SIN_CLIENTE = "(sin cliente)";
    static final String TOTAL_WEB = "Total";
    static final int ANCHO_CLIENTE_SKU_MAXIMO = 40;
    static final int ANCHO_UDS = 6;
    static final int ANCHO_ENTRO = 12;
    /** Los bloques de las webs, en este orden y con este titulo. */
    static final Map<Canal, String> BLOQUES_WEB = new LinkedHashMap<>();
    static {
        BLOQUES_WEB.put(Canal.DISTRIBUIDORES, "Fonestockroom (distribuidores)");
        BLOQUES_WEB.put(Canal.PARTICULARES, "Fonestore (particulares)");
        BLOQUES_WEB.put(Canal.BACKIPHONE, "BackiPhone");
    }

    /** Una fila del bloque de una web: cliente, SKU, dia de entrada y unidades. */
    record FilaWeb(String cliente, String sku, LocalDate entro, int uds) {}

    private ExcelBuscar() {}

    /** Por web (siempre las tres, en el orden de {@link #BLOQUES_WEB}): una fila por cliente, SKU (sin distinguir mayusculas, con la forma mas
     *  repetida; empate: la primera alfabetica) y dia de entrada, con sus unidades; orden por dia (el mas antiguo primero), cliente y SKU. */
    static Map<Canal, List<FilaWeb>> webs(List<FilasDAO.PorBuscar> filas) {
        record Clave(String cliente, String sku, LocalDate entro) {}
        Map<Canal, List<FilaWeb>> resultado = new EnumMap<>(Canal.class);
        for (Canal canal : BLOQUES_WEB.keySet()) {
            Map<Clave, Map<String, Integer>> formas = new LinkedHashMap<>();
            for (FilasDAO.PorBuscar f : filas) {
                if (f.canal() != canal) continue;
                String cliente = f.cliente() == null || f.cliente().isBlank() ? SIN_CLIENTE : f.cliente().strip();
                String sku = f.sku() == null || f.sku().isBlank() ? SIN_SKU : f.sku();
                formas.computeIfAbsent(new Clave(cliente, sku.toLowerCase(Locale.ROOT), f.entro()), k -> new TreeMap<>()).merge(sku, 1, Integer::sum);
            }
            List<FilaWeb> lista = new ArrayList<>();
            for (var e : formas.entrySet()) {
                String texto = null;
                int mejor = 0;
                int uds = 0;
                for (var forma : e.getValue().entrySet()) { // TreeMap: en un empate gana la primera alfabetica
                    uds += forma.getValue();
                    if (forma.getValue() > mejor) { mejor = forma.getValue(); texto = forma.getKey(); }
                }
                lista.add(new FilaWeb(e.getKey().cliente(), texto, e.getKey().entro(), uds));
            }
            lista.sort(Comparator.comparing(FilaWeb::entro, Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparing(f -> f.cliente().toLowerCase(Locale.ROOT)).thenComparing(f -> f.sku().toLowerCase(Locale.ROOT)));
            resultado.put(canal, lista);
        }
        return resultado;
    }

    static int unidades(Map<Canal, List<FilaWeb>> webs) {
        return webs.values().stream().flatMap(List::stream).mapToInt(FilaWeb::uds).sum();
    }

    /** El Excel de las webs: una hoja con un bloque por web (titulo, {@code Cliente | SKU | Uds | Entro}, filas y «Total»), una fila en blanco entre bloques. */
    static byte[] escribirWebs(String nombreHoja, Map<Canal, List<FilaWeb>> webs) {
        try (XSSFWorkbook libro = new XSSFWorkbook(); ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            XSSFSheet hoja = libro.createSheet(nombreHoja);
            Estilos estilos = new Estilos(estilo(libro, true, false), estilo(libro, true, true), estilo(libro, false, true));
            XSSFCellStyle fecha = estilo(libro, false, true);
            CreationHelper ayuda = libro.getCreationHelper();
            fecha.setDataFormat(ayuda.createDataFormat().getFormat("dd/mm/yyyy"));
            int[] anchos = {"Cliente".length(), "SKU".length(), ANCHO_UDS, ANCHO_ENTRO};
            int fila = 0;
            boolean primero = true;
            for (var bloque : BLOQUES_WEB.entrySet()) {
                if (!primero) fila++;
                primero = false;
                celdaTexto(hoja.createRow(fila++), 0, bloque.getValue(), estilos.titulo());
                List<FilaWeb> filas = webs.getOrDefault(bloque.getKey(), List.of());
                if (filas.isEmpty()) {
                    hoja.createRow(fila++).createCell(0).setCellValue(VACIA);
                    continue;
                }
                XSSFRow cab = hoja.createRow(fila++);
                celdaTexto(cab, 0, "Cliente", estilos.cabecera());
                celdaTexto(cab, 1, "SKU", estilos.cabecera());
                celdaTexto(cab, 2, "Uds", estilos.cabecera());
                celdaTexto(cab, 3, "Entró", estilos.cabecera());
                int total = 0;
                for (FilaWeb f : filas) {
                    XSSFRow r = hoja.createRow(fila++);
                    celdaTexto(r, 0, f.cliente(), estilos.normal());
                    celdaTexto(r, 1, f.sku(), estilos.normal());
                    XSSFCell uds = r.createCell(2);
                    uds.setCellStyle(estilos.normal());
                    uds.setCellValue(f.uds());
                    XSSFCell entro = r.createCell(3);
                    entro.setCellStyle(fecha);
                    if (f.entro() != null) entro.setCellValue(f.entro());
                    anchos[0] = Math.max(anchos[0], f.cliente().length());
                    anchos[1] = Math.max(anchos[1], f.sku().length());
                    total += f.uds();
                }
                XSSFRow t = hoja.createRow(fila++);
                celdaTexto(t, 0, TOTAL_WEB, estilos.cabecera());
                t.createCell(1).setCellStyle(estilos.cabecera());
                XSSFCell suma = t.createCell(2);
                suma.setCellStyle(estilos.cabecera());
                suma.setCellValue(total);
                t.createCell(3).setCellStyle(estilos.cabecera());
            }
            for (int c = 0; c < anchos.length; c++) {
                int ancho = c < 2 ? Math.min(anchos[c], ANCHO_CLIENTE_SKU_MAXIMO) : anchos[c];
                hoja.setColumnWidth(c, (ancho + 2) * 256);
            }
            ajustarImpresion(hoja);
            libro.write(salida);
            return salida.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo escribir el Excel", e);
        }
    }

    private static void ajustarImpresion(XSSFSheet hoja) {
        hoja.setFitToPage(true);
        XSSFPrintSetup impresion = hoja.getPrintSetup();
        impresion.setLandscape(false);
        impresion.setPaperSize(PrintSetup.A4_PAPERSIZE);
        impresion.setFitWidth((short) 1);
        impresion.setFitHeight((short) 0);
    }

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
            ajustarImpresion(hoja);
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
