package com.reparaciones.servidor.nsys;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class LectorItemsTest {

    private static List<FilaItems> leer(byte[] xlsx) throws Exception {
        return LectorItems.leer(new ByteArrayInputStream(xlsx));
    }

    @Test void leeLasFilasConSusFechasAmericanasYSuCoste() throws Exception {
        byte[] xlsx = XlsxDePrueba.items(List.of(
                XlsxDePrueba.fila("350000000000001", "Apple iPhone 13 128GB Blue", "A", "Available", "BT-9001", "CAJA LISTOS", "9/2/2026", null),
                XlsxDePrueba.fila("350000000000002", "Apple iPhone 13 128GB Blue", "A-100", "Released", "BT-9001", "Pedidos realizados", "9/2/2026", "10/15/2026")));
        assertEquals(List.of(
                new FilaItems("350000000000001", "BT-9001", "Apple iPhone 13 128GB Blue", "A", "Available", "CAJA LISTOS",
                        "Proveedor A", LocalDate.of(2026, 9, 2), null, new BigDecimal("123.45")),
                new FilaItems("350000000000002", "BT-9001", "Apple iPhone 13 128GB Blue", "A-100", "Released", "Pedidos realizados",
                        "Proveedor A", LocalDate.of(2026, 9, 2), LocalDate.of(2026, 10, 15), new BigDecimal("123.45"))),
                leer(xlsx));
    }

    @Test void elCosteVaADosDecimalesConPuntoOComaYSinNumeroEsNull() {
        assertEquals(new BigDecimal("535.41"), LectorItems.coste("535.41"));
        assertEquals(new BigDecimal("535.41"), LectorItems.coste(" 535,41 "));
        assertEquals(new BigDecimal("669.00"), LectorItems.coste("669"));
        assertEquals(new BigDecimal("386.80"), LectorItems.coste("386.8"));
        assertNull(LectorItems.coste(null));
        assertNull(LectorItems.coste(""));
        assertNull(LectorItems.coste("n/a"));
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

    @Test void cadaCampoSeCortaAlAnchoDeSuColumna() throws Exception {
        Object[] v = new Object[LectorItems.CABECERAS.size()];
        v[0] = "3".repeat(45);
        v[1] = "G".repeat(160);
        v[3] = "R".repeat(25);
        v[4] = "S".repeat(35);
        v[6] = "P".repeat(90);
        v[7] = "B".repeat(45);
        v[18] = "L".repeat(90);
        FilaItems f = leer(XlsxDePrueba.items(List.of(Arrays.asList(v)))).get(0);
        assertEquals(List.of(40, 40, 150, 20, 30, 80, 80), Stream.of(f.imeiSn(), f.batch(), f.goods(), f.grade(),
                f.status(), f.location(), f.supplier()).map(String::length).toList());
    }

    @Test void unGradoDeMasDe20NoDaEventosAlVolverAImportarElMismoFichero() throws Exception {
        String largo = "A-100 revisado y pulido";                    // 23 caracteres; GRADE es VARCHAR(20)
        byte[] xlsx = XlsxDePrueba.items(List.of(XlsxDePrueba.fila("350000000000001", "Apple iPhone 13 128GB Blue", largo,
                "Available", "BT-9001", "CAJA LISTOS", "9/2/2026", null)));
        Traductor t = new Traductor(List.of(), List.of(new UbicacionNsys("CAJA LISTOS", "Listos", true, false)));
        LocalDateTime primera = LocalDateTime.of(2026, 10, 16, 9, 1);
        Map<ClaveMovil, MovilNsys> enLaBase = new HashMap<>();
        for (MovilNsys m : Comparador.comparar(Map.of(), leer(xlsx), primera, Comparador.Modo.PRIMERA_CARGA, t).aGuardar())
            enLaBase.put(m.clave(), comoEnLaBase(m));

        ResultadoComparacion segunda = Comparador.comparar(enLaBase, leer(xlsx), primera.plusMinutes(30),
                Comparador.Modo.NORMAL, t);

        assertEquals(List.of(), segunda.eventos(), "sin un GRADO fantasma en cada importación");
        assertEquals(List.of(), segunda.aGuardar());
    }

    /** Lo que devuelve Nsys_movil al leerlo: GRADE es VARCHAR(20). */
    private static MovilNsys comoEnLaBase(MovilNsys m) {
        String grado = m.grade() != null && m.grade().length() > 20 ? m.grade().substring(0, 20) : m.grade();
        return new MovilNsys(m.imeiSn(), m.batch(), m.goods(), grado, m.status(), m.location(), m.supplier(),
                m.receivingDate(), m.soldDate(), m.sku(), m.modelo(), m.modeloMaestro(), m.capacidad(), m.color(),
                m.gradoDetalle(), m.esEsim(), m.ubicacion(), m.ubicacionUltima(), m.estado(), m.vistoPrimero(), m.cambiado());
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
