package com.reparaciones.servidor.nsys;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class LectorMaestroTest {

    private static final List<Object> CABECERA = List.of("Product Name Calidad", "Product Name", "Familia", "Modelo",
            "Capacidad", "Color", "Calidad Detalle", "SKU (stock web)", "SKU (Amazon)", "SKU sin grado");

    private static List<Object> fila(String nombre, String familia, Object modelo, String color, String detalle,
                                     String web, String amazon) {
        return Arrays.asList(nombre, "x", familia, modelo, "128GB", color, detalle, web, amazon, "i13128Azul");
    }

    private static byte[] maestro(List<List<Object>> filas) throws Exception {
        List<List<Object>> todo = new ArrayList<>();
        todo.add(CABECERA);
        todo.addAll(filas);
        Map<String, List<List<Object>>> hojas = new LinkedHashMap<>();
        hojas.put("TD1", List.of(List.of("tabla dinámica")));
        hojas.put(LectorMaestro.HOJA, todo);
        return XlsxDePrueba.libro(hojas);
    }

    private static ResultadoMaestro leer(byte[] xlsx) throws Exception {
        return LectorMaestro.leer(new ByteArrayInputStream(xlsx));
    }

    @Test void leeSuHojaConModelosYSku() throws Exception {
        ResultadoMaestro r = leer(maestro(List.of(
                fila("Apple iPhone 13 128GB Blue A", "iPhone", 13, "Azul", "A", "i13128AzulA", "i13128AzulA"),
                fila("Apple iPhone 17e 256GB eSIM Black A", "iPhone", "17 e eSIM", "Negro", "A", "i17e256NegroA", null),
                fila("Apple iPad 11 11in 128GB Blue n/a", "iPad", "11", null, "n/a", null, null))));

        assertEquals(new ProductoMaestro(Normalizacion.clave("Apple iPhone 13 128GB Blue A"), "13", "13", "128GB", "Azul",
                "A", "i13128AzulA", "i13128AzulA", "i13128Azul", false), r.productos().get(0));
        ProductoMaestro diecisiete = r.productos().get(1);
        assertNull(diecisiete.modelo(), "17e no está en el catálogo del ERP");
        assertEquals("17 e eSIM", diecisiete.modeloMaestro());
        assertTrue(diecisiete.esEsim());
        assertNull(r.productos().get(2).modelo(), "un iPad nunca es un iPhone 11");
        assertEquals(Set.of("17 e eSIM"), r.modelosFueraDeCatalogo());
        assertTrue(r.conflictos().isEmpty());
    }

    @Test void unaClaveRepetidaConOtroSkuSeQuedaSinSku() throws Exception {
        ResultadoMaestro r = leer(maestro(List.of(
                fila("Apple iPhone 13 128GB Blue A", "iPhone", "13", "Azul", "A", "i13128AzulA", "i13128AzulA"),
                fila("Apple iPhone 13 128GB  Blue A", "iPhone", "13", "Azul", "A", "i13128AzulB", "i13128AzulB"))));
        assertEquals(1, r.productos().size());
        assertNull(r.productos().get(0).skuWeb());
        assertEquals(List.of("Apple iPhone 13 128GB  Blue A"), r.conflictos());
    }

    @Test void unColorLargoSeCortaAlAnchoDeSuColumna() throws Exception {
        String color = "Azul Sierra con reflejos de medianoche y mar";      // 44 caracteres; COLOR es VARCHAR(40)
        ProductoMaestro p = leer(maestro(List.of(fila("Apple iPhone 13 128GB Blue A", "iPhone", "13", color, "A",
                "i13128AzulA", "i13128AzulA")))).productos().get(0);
        assertEquals(color.substring(0, 40), p.color());
    }

    @Test void cadaCampoSeCortaAlAnchoDeSuColumna() throws Exception {
        List<Object> larga = Arrays.asList("Apple iPhone 13 " + "x".repeat(200), "x", "iPhone", "M".repeat(45),
                "C".repeat(25), "K".repeat(45), "D".repeat(25), "W".repeat(65), "Z".repeat(65), "S".repeat(65));
        ProductoMaestro p = leer(maestro(List.of(larga))).productos().get(0);
        assertEquals(List.of(160, 40, 20, 40, 20, 60, 60, 60), java.util.stream.Stream.of(p.clave(), p.modeloMaestro(),
                p.capacidad(), p.color(), p.calidadDetalle(), p.skuWeb(), p.skuAmazon(), p.skuSinGrado())
                .map(String::length).toList());
    }

    @Test void sinSuHojaOSinUnaColumnaSeRechaza() throws Exception {
        Map<String, List<List<Object>>> otra = new LinkedHashMap<>();
        otra.put("Check SKUs", List.of(CABECERA));
        assertEquals("Falta la hoja «Maestro producto NSYS»",
                assertThrows(FormatoItemsException.class, () -> leer(XlsxDePrueba.libro(otra))).getMessage());

        List<Object> sinModelo = new ArrayList<>(CABECERA);
        sinModelo.set(3, "Modelo texto");
        Map<String, List<List<Object>>> hojas = new LinkedHashMap<>();
        hojas.put(LectorMaestro.HOJA, List.of(sinModelo));
        assertEquals("Faltan columnas en el maestro: Modelo",
                assertThrows(FormatoItemsException.class, () -> leer(XlsxDePrueba.libro(hojas))).getMessage());
    }
}
