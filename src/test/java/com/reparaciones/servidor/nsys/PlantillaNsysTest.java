package com.reparaciones.servidor.nsys;

import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Las plantillas para subir a NSYS (spec 0.9.22 §4) sobre la vacía de NSYS. Datos inventados. */
class PlantillaNsysTest {

    private static final PlantillaNsys.FilaColor ROJO = new PlantillaNsys.FilaColor("350000000000011", "iPhone 13", "128 GB",
            "(PRODUCT)RED", false, "B", new BigDecimal("412.35"), "Proveedor A");

    private static XSSFWorkbook abrir(byte[] xlsx) throws IOException {
        return new XSSFWorkbook(new ByteArrayInputStream(xlsx));
    }

    private static XSSFWorkbook vacia() throws IOException {
        return new XSSFWorkbook(PlantillaNsys.class.getResourceAsStream(PlantillaNsys.RECURSO));
    }

    /** El texto de cada celda de las filas 1 a 6 (41 columnas, hasta AO), como lo enseña Excel. */
    private static List<String> cabecera(XSSFWorkbook libro) {
        DataFormatter formato = new DataFormatter();
        Sheet hoja = libro.getSheetAt(0);
        List<String> textos = new ArrayList<>();
        for (int r = 0; r < 6; r++) {
            Row fila = hoja.getRow(r);
            for (int c = 0; c < 41; c++)
                textos.add(fila == null || fila.getCell(c) == null ? "" : formato.formatCellValue(fila.getCell(c)));
        }
        return textos;
    }

    private static XSSFCell celda(XSSFWorkbook libro, int fila, int columna) {
        return libro.getSheetAt(0).getRow(PlantillaNsys.PRIMERA_FILA + fila).getCell(columna);
    }

    private static byte[] fondo(XSSFCell c) {
        XSSFColor color = ((XSSFCellStyle) c.getCellStyle()).getFillForegroundXSSFColor();
        return color == null ? null : color.getRGB();
    }

    @Test void conservaLaHojaYLaCabeceraDeLaVaciaYLosDatosEmpiezanEnLaFila7() throws IOException {
        try (XSSFWorkbook hecha = abrir(PlantillaNsys.color(List.of(ROJO))); XSSFWorkbook vacia = vacia()) {
            assertEquals("Лист1", hecha.getSheetName(0));
            assertEquals(cabecera(vacia), cabecera(hecha));
            assertEquals(vacia.getSheetAt(0).getNumMergedRegions(), hecha.getSheetAt(0).getNumMergedRegions());
            assertEquals("350000000000011", celda(hecha, 0, 0).getStringCellValue());
        }
    }

    @Test void laDeColorLlevaLoDeNsysYElColorNuevo() throws IOException {
        try (XSSFWorkbook libro = abrir(PlantillaNsys.color(List.of(ROJO)))) {
            assertEquals("Apple", celda(libro, 0, 1).getStringCellValue());
            assertEquals("iPhone 13", celda(libro, 0, 2).getStringCellValue());
            assertEquals("128 GB", celda(libro, 0, 3).getStringCellValue());
            assertEquals("(PRODUCT)RED", celda(libro, 0, 4).getStringCellValue());
            assertEquals("B", celda(libro, 0, 5).getStringCellValue());
            assertEquals(412.35, celda(libro, 0, 6).getNumericCellValue(), 0.0001);
            assertEquals("Proveedor A", celda(libro, 0, 7).getStringCellValue());
            assertEquals("COLOR", celda(libro, 0, 8).getStringCellValue());
            assertEquals("iPhone", celda(libro, 0, 9).getStringCellValue());
            assertEquals(0, celda(libro, 0, 13).getNumericCellValue(), 0.0001);
            assertFalse(java.util.Arrays.equals(PlantillaNsys.FONDO_AMARILLO, fondo(celda(libro, 0, 4))),
                    "un color con la grafía de NSYS no se marca");
        }
    }

    @Test void unColorDelMaestroVaEnAmarilloYLoQueFaltaEnRojo() throws IOException {
        PlantillaNsys.FilaColor delMaestro = new PlantillaNsys.FilaColor("350000000000012", "iPhone 13", "128 GB", "Pink", true, "B",
                null, null);
        try (XSSFWorkbook libro = abrir(PlantillaNsys.color(List.of(ROJO, delMaestro)))) {
            assertArrayEquals(PlantillaNsys.FONDO_AMARILLO, fondo(celda(libro, 1, 4)));
            assertArrayEquals(PlantillaNsys.FONDO_ROJO, fondo(celda(libro, 1, 6)), "sin coste: rellenar");
            assertArrayEquals(PlantillaNsys.FONDO_ROJO, fondo(celda(libro, 1, 7)), "sin proveedor: rellenar");
            assertEquals("", celda(libro, 1, 6).toString());
        }
    }

    @Test void laDeAltasLlevaElImeiYElModeloDelTallerEnRojoYLoDemasParaRellenar() throws IOException {
        try (XSSFWorkbook libro = abrir(PlantillaNsys.altas(List.of(new PlantillaNsys.FilaAlta("350000000000088", "iPhone 14"))))) {
            assertEquals("350000000000088", celda(libro, 0, 0).getStringCellValue());
            assertEquals("Apple", celda(libro, 0, 1).getStringCellValue());
            assertEquals("iPhone 14", celda(libro, 0, 2).getStringCellValue());
            assertArrayEquals(PlantillaNsys.FONDO_ROJO, fondo(celda(libro, 0, 2)));
            for (int c = 3; c <= 7; c++) assertArrayEquals(PlantillaNsys.FONDO_ROJO, fondo(celda(libro, 0, c)), "columna " + c);
            assertEquals("AJUSTE", celda(libro, 0, 8).getStringCellValue());
            assertEquals("iPhone", celda(libro, 0, 9).getStringCellValue());
            assertEquals(0, celda(libro, 0, 13).getNumericCellValue(), 0.0001);
        }
    }

    @Test void sinFilasEsLaPlantillaVacia() throws IOException {
        try (XSSFWorkbook libro = abrir(PlantillaNsys.altas(List.of())); XSSFWorkbook vacia = vacia()) {
            assertEquals(cabecera(vacia), cabecera(libro));
            Row fila7 = libro.getSheetAt(0).getRow(PlantillaNsys.PRIMERA_FILA);
            assertTrue(fila7 == null || fila7.getCell(0) == null || fila7.getCell(0).toString().isEmpty());
        }
    }

    @Test void elNombreDelModeloDelTallerComoLaWeb() {
        assertEquals("iPhone 14 Pro Max", PlantillaNsys.nombreModelo("14promax"));
        assertEquals("iPhone 16e", PlantillaNsys.nombreModelo("16e"));
        assertEquals("iPhone XS Max", PlantillaNsys.nombreModelo("xsmax"));
        assertEquals("iPhone 13 Mini", PlantillaNsys.nombreModelo("13mini"));
        assertNull(PlantillaNsys.nombreModelo(null));
        assertNull(PlantillaNsys.nombreModelo(" "));
    }
}
