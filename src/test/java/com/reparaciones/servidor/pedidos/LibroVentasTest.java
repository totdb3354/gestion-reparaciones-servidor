package com.reparaciones.servidor.pedidos;

import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** El lector de la pestaña «Paso 1. Meter IMEI» de los libros de ventas de Amazon, con libros sinteticos generados aqui (datos
 *  inventados). */
class LibroVentasTest {

    private static final LocalDate HOY = LocalDate.of(2026, 11, 16);
    private static final LocalDate DESDE = HOY.minusDays(30);

    @TempDir Path dir;

    /** Una fila del libro: cada celda es un String (texto), un Double (numero), un LocalDateTime (fecha de Excel) o null. */
    private static Object[] f(Object... celdas) {
        return celdas;
    }

    /** Un libro con una pestaña «Hoja1» de apuntes delante y la pestaña {@code pestana}: 5 filas de relleno, la cabecera en la 6 y las
     *  filas de datos detras. */
    private Path libro(String pestana, String[] cabecera, Object[]... filas) throws Exception {
        Path fichero = dir.resolve("libro.xlsx");
        try (XSSFWorkbook libro = new XSSFWorkbook(); OutputStream salida = Files.newOutputStream(fichero)) {
            Sheet apuntes = libro.createSheet("Hoja1");
            apuntes.createRow(0).createCell(0).setCellValue("order id");
            apuntes.getRow(0).createCell(1).setCellValue("350000000000099");
            CellStyle fecha = libro.createCellStyle();
            fecha.setDataFormat(libro.createDataFormat().getFormat("dd/mm/yyyy hh:mm"));
            Sheet hoja = libro.createSheet(pestana);
            for (int i = 0; i < 5; i++) hoja.createRow(i).createCell(0).setCellValue("Relleno " + (i + 1) + ": instrucciones de logistica");
            Row cab = hoja.createRow(5);
            for (int i = 0; i < cabecera.length; i++) if (cabecera[i] != null) cab.createCell(i).setCellValue(cabecera[i]);
            for (int n = 0; n < filas.length; n++) {
                Row r = hoja.createRow(6 + n);
                for (int i = 0; i < filas[n].length; i++) {
                    Object v = filas[n][i];
                    if (v instanceof String t) r.createCell(i).setCellValue(t);
                    else if (v instanceof Double d) r.createCell(i).setCellValue(d);
                    else if (v instanceof LocalDateTime t) {
                        r.createCell(i).setCellValue(t);
                        r.getCell(i).setCellStyle(fecha);
                    }
                }
            }
            libro.write(salida);
        }
        return fichero;
    }

    /** Cabecera desordenada, con mayusculas y espacios a los lados, y una columna que no se lee. */
    private static final String[] CABECERA = {"sku", "  IMEI ", "Purchase-Date", null, " Order ID"};

    @Test void leeLosParesDeLosUltimos30DiasConCadaFormaDeImeiYDeFecha() throws Exception {
        Path fichero = libro(LibroVentas.PESTANA, CABECERA,
                f("SKU-A", "350000000000011", "2026-11-15T10:22:33+00:00", "nota", "402-0000000-0000011"),
                f("SKU-B", 350000000000012.0, LocalDateTime.of(2026, 11, 14, 18, 5), null, "402-0000000-0000012"),
                f("SKU-C", " 35 0000000000013 ", "16/11/2026", null, " 402-0000000-0000013 "),
                f("SKU-D", "CANCELADO", "2026-11-10", null, "402-0000000-0000014"),
                f("SKU-E", null, "2026-11-10", null, "402-0000000-0000015"),
                f("SKU-F", "350000000000016", "2026-10-07T09:00:00+00:00", null, "402-0000000-0000016"),
                f("SKU-G", 350000000000017.0, LocalDateTime.of(2026, 10, 7, 9, 0), null, "402-0000000-0000017"),
                f("SKU-H", "350000000000018", "07/10/2026", null, "402-0000000-0000018"),
                f("SKU-I", "350000000000019", "2026-10-17", null, "402-0000000-0000019"),
                f("SKU-J", "350000000000020", "sin fecha", null, "402-0000000-0000020"),
                f(),
                f("SKU-K", "350000000000021", "2026-11-13 08:00:00", null, "402-0000000-0000021"));
        assertEquals(List.of(
                new PeticionAsociaciones.Par("402-0000000-0000011", "350000000000011"),
                new PeticionAsociaciones.Par("402-0000000-0000012", "350000000000012"),
                new PeticionAsociaciones.Par("402-0000000-0000013", "350000000000013"),
                new PeticionAsociaciones.Par("402-0000000-0000014", "CANCELADO"),
                new PeticionAsociaciones.Par("402-0000000-0000019", "350000000000019"),
                new PeticionAsociaciones.Par("402-0000000-0000021", "350000000000021")), LibroVentas.leer(fichero, DESDE));
    }

    @Test void sinLaPestanaEsUnErrorClaro() throws Exception {
        Path fichero = libro("Paso 2. Etiquetas", CABECERA, f("SKU-A", "350000000000011", "2026-11-15", null, "402-0000000-0000011"));
        LibroVentasException e = assertThrows(LibroVentasException.class, () -> LibroVentas.leer(fichero, DESDE));
        assertEquals("falta la pestaña Paso 1. Meter IMEI", e.getMessage());
    }

    @Test void sinUnaCabeceraEsUnErrorClaro() throws Exception {
        Path fichero = libro(LibroVentas.PESTANA, new String[]{"sku", "IMEIS", "purchase-date", "order id"},
                f("SKU-A", "350000000000011", "2026-11-15", "402-0000000-0000011"));
        LibroVentasException e = assertThrows(LibroVentasException.class, () -> LibroVentas.leer(fichero, DESDE));
        assertEquals("falta la columna IMEI en Paso 1. Meter IMEI", e.getMessage());
    }

    @Test void conLaCabeceraEnOtraFilaFaltanLasTres() throws Exception {
        Path fichero = libro(LibroVentas.PESTANA, new String[]{});
        LibroVentasException e = assertThrows(LibroVentasException.class, () -> LibroVentas.leer(fichero, DESDE));
        assertEquals("faltan las columnas order id, purchase-date, IMEI en Paso 1. Meter IMEI", e.getMessage());
    }

    @Test void unFicheroQueNoEsUnLibroEsUnErrorClaro() throws Exception {
        Path fichero = dir.resolve("roto.xlsm");
        Files.writeString(fichero, "esto no es un libro");
        LibroVentasException e = assertThrows(LibroVentasException.class, () -> LibroVentas.leer(fichero, DESDE));
        assertTrue(e.getMessage().startsWith("el libro no se puede leer"), e.getMessage());
    }

    @Test void elImeiNumericoDaSusQuinceCifrasDesdeElValorCrudo() {
        assertEquals("356789012345678", LibroVentas.imei(new LibroVentas.Celda("3.56789012345678E14", true)));
        assertEquals("356789012345678", LibroVentas.imei(new LibroVentas.Celda("3.56789012345678E+14", true)));
        assertEquals("356789012345678", LibroVentas.imei(new LibroVentas.Celda("356789012345678.0", true)));
        assertEquals("356789012345678", LibroVentas.imei(new LibroVentas.Celda("356789012345678", true)));
        assertEquals("35678901234567.5", LibroVentas.imei(new LibroVentas.Celda("35678901234567.5", true)), "con decimales de verdad no es un IMEI");
        assertEquals("356789012345678", LibroVentas.imei(new LibroVentas.Celda(" 356 789012 345678 ", false)));
        assertNull(LibroVentas.imei(new LibroVentas.Celda("   ", false)));
        assertNull(LibroVentas.imei(null));
    }

    @Test void laFechaSeEntiendeEnLosTresFormatosYConZonaEsElDiaDeMadrid() {
        assertEquals(LocalDate.of(2026, 11, 13), LibroVentas.fecha(new LibroVentas.Celda("2026-11-13T10:22:33+00:00", false)));
        assertEquals(LocalDate.of(2026, 11, 14), LibroVentas.fecha(new LibroVentas.Celda("2026-11-13T23:30:00+00:00", false)));
        assertEquals(LocalDate.of(2026, 11, 14), LibroVentas.fecha(new LibroVentas.Celda("2026-11-13T23:30:00Z", false)));
        assertEquals(LocalDate.of(2026, 11, 13), LibroVentas.fecha(new LibroVentas.Celda("2026-11-13T22:30:00.000Z", false)));
        assertEquals(LocalDate.of(2026, 11, 13), LibroVentas.fecha(new LibroVentas.Celda("2026-11-13", false)));
        assertEquals(LocalDate.of(2026, 11, 13), LibroVentas.fecha(new LibroVentas.Celda("13/11/2026", false)));
        assertEquals(LocalDate.of(2026, 11, 3), LibroVentas.fecha(new LibroVentas.Celda("3/11/2026 10:00", false)));
        assertEquals(LocalDate.of(2026, 11, 13), LibroVentas.fecha(new LibroVentas.Celda("46339.43", true)));
        assertNull(LibroVentas.fecha(new LibroVentas.Celda("ayer", false)));
        assertNull(LibroVentas.fecha(new LibroVentas.Celda("-5", true)));
    }
}
