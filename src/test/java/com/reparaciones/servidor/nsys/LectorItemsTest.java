package com.reparaciones.servidor.nsys;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class LectorItemsTest {

    private static List<FilaItems> leer(byte[] xlsx) throws Exception {
        return LectorItems.leer(new ByteArrayInputStream(xlsx));
    }

    @Test void leeLasFilasConSusFechasAmericanasYSinCostes() throws Exception {
        byte[] xlsx = XlsxDePrueba.items(List.of(
                XlsxDePrueba.fila("350000000000001", "Apple iPhone 13 128GB Blue", "A", "Available", "BT-9001", "CAJA LISTOS", "9/2/2026", null),
                XlsxDePrueba.fila("350000000000002", "Apple iPhone 13 128GB Blue", "A-100", "Released", "BT-9001", "Pedidos realizados", "9/2/2026", "10/15/2026")));
        assertEquals(List.of(
                new FilaItems("350000000000001", "BT-9001", "Apple iPhone 13 128GB Blue", "A", "Available", "CAJA LISTOS",
                        "Proveedor A", LocalDate.of(2026, 9, 2), null),
                new FilaItems("350000000000002", "BT-9001", "Apple iPhone 13 128GB Blue", "A-100", "Released", "Pedidos realizados",
                        "Proveedor A", LocalDate.of(2026, 9, 2), LocalDate.of(2026, 10, 15))),
                leer(xlsx));
    }

    @Test void cabecerasConEspacioFinalYEnOtroOrdenValen() throws Exception {
        List<Object> cabecera = new ArrayList<>(LectorItems.CABECERAS);
        cabecera.set(0, "IMEI/SN ");
        cabecera.set(11, "Total cost ");
        java.util.Collections.swap(cabecera, 0, 18);
        List<Object> fila = new ArrayList<>(XlsxDePrueba.fila("350000000000003", "Apple iPhone 13 128GB Blue", "A",
                "InProcess", "BT-9001", null, "9/2/2026", null));
        java.util.Collections.swap(fila, 0, 18);
        Map<String, List<List<Object>>> hojas = new LinkedHashMap<>();
        hojas.put("Items", List.of(cabecera, fila));
        FilaItems f = leer(XlsxDePrueba.libro(hojas)).get(0);
        assertEquals("350000000000003", f.imeiSn());
        assertNull(f.location(), "sin caja");
    }

    @Test void siFaltaUnaColumnaSeRechazaDiciendoCual() {
        List<Object> cabecera = new ArrayList<>(LectorItems.CABECERAS);
        cabecera.set(18, "Ubicacion");
        Map<String, List<List<Object>>> hojas = new LinkedHashMap<>();
        hojas.put("Items", List.of(cabecera));
        FormatoItemsException e = assertThrows(FormatoItemsException.class, () -> leer(XlsxDePrueba.libro(hojas)));
        assertEquals("Faltan columnas en el fichero de NSYS: Location", e.getMessage());
    }

    @Test void unFicheroQueNoEsXlsxOVacioSeRechaza() throws Exception {
        assertThrows(FormatoItemsException.class, () -> leer("no es un xlsx".getBytes(StandardCharsets.UTF_8)));
        Map<String, List<List<Object>>> vacio = new LinkedHashMap<>();
        vacio.put("Items", List.of());
        assertEquals("El fichero de NSYS está vacío",
                assertThrows(FormatoItemsException.class, () -> leer(XlsxDePrueba.libro(vacio))).getMessage());
    }

    @Test void unXlsxTruncadoSeRechazaComoFormato() throws Exception {
        byte[] completo = XlsxDePrueba.items(List.of(
                XlsxDePrueba.fila("350000000000001", "Apple iPhone 13 128GB Blue", "A", "Available", "BT-9001", "CAJA LISTOS", "9/2/2026", null)));
        byte[] cortado = java.util.Arrays.copyOf(completo, completo.length / 2);
        assertThrows(FormatoItemsException.class, () -> leer(cortado));
    }

    @Test void lasFilasSinImeiSeIgnoranYUnaFechaRaraQuedaVacia() throws Exception {
        byte[] xlsx = XlsxDePrueba.items(List.of(
                XlsxDePrueba.fila(null, "Apple iPhone 13 128GB Blue", "A", "Available", "BT-9001", "", "9/2/2026", null),
                XlsxDePrueba.fila("350000000000004", "Apple iPhone 13 128GB Blue", "A", "Available", null, "", "ayer", null)));
        List<FilaItems> filas = leer(xlsx);
        assertEquals(1, filas.size());
        assertNull(filas.get(0).receivingDate());
        assertEquals("", filas.get(0).batch(), "sin lote, la clave sigue siendo válida");
    }

    @Test void unImeiGuardadoComoNumeroSaleConSusQuinceCifras() throws Exception {
        // Una celda numérica con formato «General» salía como «3.50000E+14» y juntaba muchos móviles en una clave.
        List<Object> primero = new ArrayList<>(XlsxDePrueba.fila(null, "Apple iPhone 13 128GB Blue", "A", "Available",
                "BT-9001", "CAJA LISTOS", "9/2/2026", null));
        primero.set(0, 350000000000123L);
        List<Object> segundo = new ArrayList<>(primero);
        segundo.set(0, 350000000000124L);
        List<FilaItems> filas = leer(XlsxDePrueba.items(List.of(primero, segundo)));
        assertEquals(List.of("350000000000123", "350000000000124"), filas.stream().map(FilaItems::imeiSn).toList());
    }

    @Test void unLoteNumericoSaleSinPuntoCeroYUnaFechaConFormatoDeFechaSeSigueLeyendo() throws Exception {
        try (XSSFWorkbook libro = new XSSFWorkbook(); ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            Sheet hoja = libro.createSheet("Items");
            Row cabecera = hoja.createRow(0);
            for (int c = 0; c < LectorItems.CABECERAS.size(); c++) cabecera.createCell(c).setCellValue(LectorItems.CABECERAS.get(c));
            CellStyle fecha = libro.createCellStyle();
            fecha.setDataFormat((short) 14);                            // formato de fecha integrado (m/d/yy)
            Row fila = hoja.createRow(1);
            fila.createCell(0).setCellValue("350000000000001");
            fila.createCell(7).setCellValue(9001);                      // Batch
            Cell recibido = fila.createCell(15);                        // Receiving date
            recibido.setCellValue(LocalDate.of(2026, 9, 2));
            recibido.setCellStyle(fecha);
            libro.write(salida);
            FilaItems f = leer(salida.toByteArray()).get(0);
            assertEquals("9001", f.batch());
            assertEquals(LocalDate.of(2026, 9, 2), f.receivingDate());
        }
    }

    @Test void losNumerosSalenEnTextoLlanoSinRedondear() {
        assertEquals("350000000000123", HojaXlsx.numeroLlano(350000000000123d));
        assertEquals("9001", HojaXlsx.numeroLlano(9001d));
        assertEquals("123.45", HojaXlsx.numeroLlano(123.45));
        assertEquals("0.000012345", HojaXlsx.numeroLlano(0.000012345));
        assertEquals("100000000000000000000", HojaXlsx.numeroLlano(1e20));
        assertEquals("-7", HojaXlsx.numeroLlano(-7d));
        assertEquals("0", HojaXlsx.numeroLlano(0d));
    }

    @Test void formatosDeFecha() {
        assertEquals(LocalDate.of(2026, 10, 5), LectorItems.fecha("10/5/2026"));
        assertEquals(LocalDate.of(2026, 10, 15), LectorItems.fecha("10/15/26"));
        assertEquals(LocalDate.of(2026, 10, 15), LectorItems.fecha("2026-10-15"));
        assertEquals(LocalDate.of(2026, 10, 15), LectorItems.fecha("10/15/2026 13:31"));
        assertNull(LectorItems.fecha("x"));
        assertNull(LectorItems.fecha(null));
    }
}
