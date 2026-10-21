package com.reparaciones.servidor.nsys;

import org.junit.jupiter.api.Test;

import java.util.List;

import static com.reparaciones.servidor.nsys.DatoTaller.COLOR;
import static com.reparaciones.servidor.nsys.DatoTaller.GRADO;
import static com.reparaciones.servidor.nsys.TallerDePrueba.*;
import static org.junit.jupiter.api.Assertions.*;

/** Lo que vale de un móvil (spec 0.9.21 §2.1): lo del taller vigente por encima de NSYS, dato a dato. */
class LoQueValeTest {

    private static final String IMEI = "350000000000001";
    private static final MovilNsys AZUL_B = movil(IMEI, "Azul", "B");
    private static final Maestro MAESTRO = new Maestro(List.of(producto("Blue", "Azul", "B"), producto("Blue", "Azul", "A100"),
            producto("(PRODUCT)RED", "Rojo", "B"), producto("(PRODUCT)RED", "Rojo", "A100")));

    @Test void sinNadaDelTallerTodoComoHoyYSinLeerElMaestro() {
        assertEquals(new LoQueVale.Valor("Azul", "B", "i13128AzulB", null, null, null, false),
                LoQueVale.de(AZUL_B, List.of(), () -> fail("sin nada del taller no se lee el maestro")));
    }

    @Test void conElColorDelTaller() {
        LoQueVale.Valor v = LoQueVale.de(AZUL_B, List.of(vigente(1, IMEI, COLOR, "Rojo", "Azul")), () -> MAESTRO);
        assertEquals(new LoQueVale.Valor("Rojo", "B", "i13128RojoB", "Azul", null, "i13128AzulB", true), v);
    }

    @Test void conElGradoDelTallerQueLlevaLaBateria() {
        LoQueVale.Valor v = LoQueVale.de(AZUL_B, List.of(vigente(1, IMEI, GRADO, "A100", "B")), () -> MAESTRO);
        assertEquals("A100", v.grade());
        assertEquals("A", v.grado());
        assertTrue(v.bateriaNueva());
        assertEquals("B", v.gradoNsys());
        assertNull(v.colorNsys());
        assertEquals("i13128AzulA100", v.sku());
    }

    @Test void conLosDos() {
        LoQueVale.Valor v = LoQueVale.de(AZUL_B, List.of(vigente(1, IMEI, COLOR, "Rojo", "Azul"),
                vigente(2, IMEI, GRADO, "A100", "B")), () -> MAESTRO);
        assertEquals(new LoQueVale.Valor("Rojo", "A100", "i13128RojoA100", "Azul", "B", "i13128AzulB", true), v);
    }

    @Test void sinSkuEnElMaestroSeQuedaSinSku() {
        LoQueVale.Valor v = LoQueVale.de(AZUL_B, List.of(vigente(1, IMEI, GRADO, "C", "B")), () -> MAESTRO);
        assertNull(v.sku());
        assertEquals("i13128AzulB", v.skuNsys());
    }

    @Test void loQueNsysNoSabeSaleConUnaRaya() {
        MovilNsys sinSku = new MovilNsys(IMEI, "BT-9001", "Apple iPhone 13 128GB Blue", "B", "Available", "CAJA LISTOS",
                "Proveedor A", null, null, null, "13", "13", "128GB", "Azul", "B", false, "Listos", "Listos",
                EstadoMovil.STOCK, HOY, HOY);
        LoQueVale.Valor v = LoQueVale.de(sinSku, List.of(vigente(1, IMEI, GRADO, "A100", "B")), () -> MAESTRO);
        assertEquals("i13128AzulA100", v.sku());
        assertEquals("—", v.skuNsys());
    }

    @Test void unCambioIgualALoDeNsysNoLlevaMarcaPeroSigueEnLaLista() {
        LoQueVale.Valor v = LoQueVale.de(AZUL_B, List.of(vigente(1, IMEI, GRADO, "B", "C")), () -> MAESTRO);
        assertNull(v.gradoNsys());
        assertNull(v.skuNsys());
        assertTrue(v.porPasar(), "hasta que el cierre lo vea PASADO");
    }
}
