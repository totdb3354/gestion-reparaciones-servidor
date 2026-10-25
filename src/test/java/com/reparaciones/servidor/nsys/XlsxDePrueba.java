package com.reparaciones.servidor.nsys;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Libros .xlsx de datos inventados, hechos en memoria para los tests de los lectores. */
final class XlsxDePrueba {

    private XlsxDePrueba() {}

    /** Un libro con estas hojas, en orden; números como celdas numéricas y null como celda vacía. */
    static byte[] libro(Map<String, List<List<Object>>> hojas) throws IOException {
        try (XSSFWorkbook libro = new XSSFWorkbook(); ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            for (Map.Entry<String, List<List<Object>>> hoja : hojas.entrySet()) {
                Sheet s = libro.createSheet(hoja.getKey());
                int n = 0;
                for (List<Object> fila : hoja.getValue()) {
                    Row r = s.createRow(n++);
                    for (int c = 0; c < fila.size(); c++) {
                        Object v = fila.get(c);
                        if (v instanceof Number numero) r.createCell(c).setCellValue(numero.doubleValue());
                        else if (v != null) r.createCell(c).setCellValue(v.toString());
                    }
                }
            }
            libro.write(salida);
            return salida.toByteArray();
        }
    }

    /** Un fichero Items con la cabecera de NSYS y estas filas. */
    static byte[] items(List<List<Object>> filas) throws IOException {
        List<List<Object>> todo = new ArrayList<>();
        todo.add(new ArrayList<Object>(LectorItems.CABECERAS));
        todo.addAll(filas);
        Map<String, List<List<Object>>> hojas = new LinkedHashMap<>();
        hojas.put("Items", todo);
        return libro(hojas);
    }

    /** Una fila de Items en el orden de LectorItems.CABECERAS, con un coste de 123,45 (se lee desde la 0.9.22). */
    static List<Object> fila(String imei, String goods, String grade, String status, String batch, String location,
                             String recibido, String vendido) {
        Object[] v = new Object[LectorItems.CABECERAS.size()];
        v[0] = imei;
        v[1] = goods;
        v[3] = grade;
        v[4] = status;
        v[6] = "Proveedor A";
        v[7] = batch;
        v[11] = 123.45;
        v[15] = recibido;
        v[16] = vendido;
        v[18] = location;
        return Arrays.asList(v);
    }
}
