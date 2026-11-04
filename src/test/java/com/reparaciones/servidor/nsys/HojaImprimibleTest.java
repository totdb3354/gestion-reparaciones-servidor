package com.reparaciones.servidor.nsys;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** La hoja para imprimir de «Qué revisar» (spec 0.9.25 §10.3): genérica, con textos inventados. */
class HojaImprimibleTest {

    private static final List<String> COLUMNAS = List.of("Modelo", "Capacidad", "Color", "IMEI", "Revisado");
    private static final List<List<String>> FILAS = List.of(
            List.of("iPhone 15 Pro", "256 GB", "Titanio natural", "350000000000101", ""),
            List.of("iPhone 15 Pro", "256 GB", "Titanio natural", "350000000000102", ""));

    private static XSSFSheet hoja(byte[] xlsx) throws IOException {
        return new XSSFWorkbook(new ByteArrayInputStream(xlsx)).getSheetAt(0);
    }

    @Test void laCabeceraEnNegritaYLasFilasComoTextoConBordes() throws IOException {
        XSSFSheet hoja = hoja(HojaImprimible.escribir("Revisar sin grado", COLUMNAS, FILAS));
        assertEquals("Revisar sin grado", hoja.getSheetName());
        assertEquals(2, hoja.getLastRowNum(), "la cabecera y dos filas");
        XSSFCell titulo = hoja.getRow(0).getCell(3);
        assertEquals("IMEI", titulo.getStringCellValue());
        assertTrue(titulo.getCellStyle().getFont().getBold());
        XSSFCell imei = hoja.getRow(1).getCell(3);
        assertEquals(CellType.STRING, imei.getCellType(), "un IMEI no pasa a número");
        assertEquals("350000000000101", imei.getStringCellValue());
        assertFalse(imei.getCellStyle().getFont().getBold());
        XSSFCell vacia = hoja.getRow(2).getCell(4);
        assertEquals(CellType.BLANK, vacia.getCellType());
        for (XSSFCell c : List.of(titulo, imei, vacia)) {
            assertEquals(BorderStyle.THIN, c.getCellStyle().getBorderTop());
            assertEquals(BorderStyle.THIN, c.getCellStyle().getBorderBottom());
            assertEquals(BorderStyle.THIN, c.getCellStyle().getBorderLeft());
            assertEquals(BorderStyle.THIN, c.getCellStyle().getBorderRight());
        }
    }

    @Test void preparadaParaImprimirApaisadaAUnaPaginaDeAnchoYConLaCabeceraEnCadaPagina() throws IOException {
        XSSFSheet hoja = hoja(HojaImprimible.escribir("Revisar sin grado", COLUMNAS, FILAS));
        assertTrue(hoja.getPrintSetup().getLandscape());
        assertTrue(hoja.getFitToPage());
        assertEquals(1, hoja.getPrintSetup().getFitWidth());
        assertEquals(0, hoja.getPrintSetup().getFitHeight(), "tantas páginas de alto como haga falta");
        CellRangeAddress repetidas = hoja.getRepeatingRows();
        assertNotNull(repetidas, "la cabecera se repite en cada página");
        assertEquals(0, repetidas.getFirstRow());
        assertEquals(0, repetidas.getLastRow());
    }

    @Test void cadaColumnaTanAnchaComoSuTextoMasLargoConTope() throws IOException {
        String largo = "x".repeat(150);
        XSSFSheet hoja = hoja(HojaImprimible.escribir("Hoja", List.of("A", "Color", "Nota"),
                List.of(List.of("1", "Titanio natural", largo))));
        assertEquals((1 + 2) * 256, hoja.getColumnWidth(0));
        assertEquals((15 + 2) * 256, hoja.getColumnWidth(1), "«Titanio natural», 15 caracteres");
        assertEquals((HojaImprimible.ANCHO_MAXIMO + 2) * 256, hoja.getColumnWidth(2));
    }

    @Test void sinFilasSaleSoloLaCabecera() throws IOException {
        XSSFSheet hoja = hoja(HojaImprimible.escribir("Subir B a A", COLUMNAS, List.of()));
        assertEquals(0, hoja.getLastRowNum());
        assertEquals("Modelo", hoja.getRow(0).getCell(0).getStringCellValue());
    }

    @Test void unaPeticionQueValeNoTieneError() {
        assertNull(HojaImprimible.error("Revisar sin grado", COLUMNAS, FILAS));
        assertNull(HojaImprimible.error("x".repeat(31), List.of("A"), List.of()));
    }

    @Test void losLimitesDanSuMotivo() {
        List<String> doce = Collections.nCopies(12, "C");
        List<List<String>> cincoMil = Collections.nCopies(5000, List.of("a"));
        assertNull(HojaImprimible.error("Hoja", doce, List.of(doce)));
        assertNull(HojaImprimible.error("Hoja", List.of("A"), cincoMil));
        assertEquals("Falta el nombre de la hoja", HojaImprimible.error("  ", COLUMNAS, FILAS));
        assertEquals("Falta el nombre de la hoja", HojaImprimible.error(null, COLUMNAS, FILAS));
        assertEquals("El nombre de la hoja pasa de 31 caracteres", HojaImprimible.error("x".repeat(32), COLUMNAS, FILAS));
        for (String malo : List.of("a/b", "a\\b", "a:b", "a*b", "a?b", "a[b", "a]b"))
            assertEquals("El nombre de la hoja no puede llevar [ ] : * ? / \\", HojaImprimible.error(malo, COLUMNAS, FILAS), malo);
        assertEquals("La hoja lleva de 1 a 12 columnas", HojaImprimible.error("Hoja", List.of(), List.of()));
        assertEquals("La hoja lleva de 1 a 12 columnas", HojaImprimible.error("Hoja", null, List.of()));
        assertEquals("La hoja lleva de 1 a 12 columnas", HojaImprimible.error("Hoja", Collections.nCopies(13, "C"), List.of()));
        assertEquals("Una columna no tiene nombre", HojaImprimible.error("Hoja", Arrays.asList("A", null), List.of()));
        for (String vacia : List.of("", "  "))
            assertEquals("Una columna no tiene nombre", HojaImprimible.error("Hoja", List.of("A", vacia), List.of()), "[" + vacia + "]");
        String apostrofo = "El nombre de la hoja no puede empezar ni acabar con un apóstrofo";
        assertEquals(apostrofo, HojaImprimible.error("'x", COLUMNAS, FILAS));
        assertEquals(apostrofo, HojaImprimible.error("x'", COLUMNAS, FILAS));
        assertNull(HojaImprimible.error("x'y", COLUMNAS, FILAS));
        assertEquals("El nombre de una columna pasa de 200 caracteres", HojaImprimible.error("Hoja", List.of("x".repeat(201)), List.of()));
        assertEquals("Faltan las filas", HojaImprimible.error("Hoja", List.of("A"), null));
        List<List<String>> demasiadas = new ArrayList<>(cincoMil);
        demasiadas.add(List.of("a"));
        assertEquals("Como mucho 5000 filas", HojaImprimible.error("Hoja", List.of("A"), demasiadas));
        assertEquals("La fila 2 no tiene 2 celdas",
                HojaImprimible.error("Hoja", List.of("A", "B"), List.of(List.of("a", "b"), List.of("a"))));
        assertEquals("La fila 1 no tiene 2 celdas", HojaImprimible.error("Hoja", List.of("A", "B"), Arrays.asList((List<String>) null)));
        assertEquals("La fila 1 lleva una celda nula", HojaImprimible.error("Hoja", List.of("A"), List.of(Arrays.asList((String) null))));
        assertEquals("Una celda de la fila 1 pasa de 200 caracteres", HojaImprimible.error("Hoja", List.of("A"), List.of(List.of("x".repeat(201)))));
        assertNull(HojaImprimible.error("Hoja", List.of("A"), List.of(List.of("x".repeat(200)))));
    }
}
