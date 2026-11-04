package com.reparaciones.servidor.pedidos;

import com.reparaciones.servidor.nsys.EstadoMovil;
import com.reparaciones.servidor.nsys.LoQueVale;
import com.reparaciones.servidor.nsys.MovilNsys;
import com.reparaciones.servidor.nsys.TrabajoAbierto;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EstadoUnidadTest {

    private static final AceptaSku ACEPTA = new AceptaSku(List.of(
            AceptaSkuTest.producto("Azul", "A", "i13128AzulA", "i13128AzulA", null, null),
            AceptaSkuTest.producto("Azul", "MsA", "i13128AzulMs", "i13128AzulA", null, null),
            AceptaSkuTest.producto("Azul", "B", "i13128AzulB", "i13128AzulB", null, null)));
    private static final Aceptacion AMAZON_A = ACEPTA.para(Canal.AMAZON, "i13128AzulA");
    private static final Aceptacion WEB_A = ACEPTA.para(Canal.DISTRIBUIDORES, "i13128AzulA");
    private static final List<TrabajoAbierto> NINGUNO = List.of();

    private static MovilNsys movil(String modeloMaestro, String grade) {
        return new MovilNsys("350000000000001", "BT-9001", "Apple iPhone 13 128GB Blue", grade, "Available", "CAJA LISTOS",
                "Proveedor A", LocalDate.of(2026, 9, 1), null, "i13128AzulA", "13", modeloMaestro, "128GB", "Azul", grade, false,
                "Listos", "Listos", EstadoMovil.STOCK, null, null);
    }

    private static LoQueVale.Valor vale(String color, String grade, String sku, String gradoNsys) {
        return new LoQueVale.Valor(color, grade, sku, null, gradoNsys, null, gradoNsys != null);
    }

    private static EstadoUnidad.Resultado de(Aceptacion ac, MovilNsys m, LoQueVale.Valor v, List<TrabajoAbierto> t, Canal c) {
        return EstadoUnidad.de(ac, m, v, t, c);
    }

    @Test void losCasosDeLaSpecEnSuOrden() {
        MovilNsys a = movil("13", "A");
        assertEquals(new EstadoUnidad.Resultado("NO_EN_NSYS", null), de(AMAZON_A, null, null, NINGUNO, Canal.AMAZON));
        assertEquals(new EstadoUnidad.Resultado("NO_CUADRA", "SKU_DESCONOCIDO"), de(null, a, vale("Azul", "A", "i13128AzulA", null), NINGUNO, Canal.AMAZON));
        assertEquals(new EstadoUnidad.Resultado("NO_CUADRA", "MODELO"), de(AMAZON_A, movil("13 mini", "A"), vale("Azul", "A", "x", null), NINGUNO, Canal.AMAZON));
        assertEquals(new EstadoUnidad.Resultado("NO_CUADRA", "GRADO_NO_VENDIBLE"), de(AMAZON_A, movil("13", "Fd"), vale("Azul", "Fd", null, null), NINGUNO, Canal.AMAZON));
        assertEquals(new EstadoUnidad.Resultado("NO_CUADRA", "GRADO_NO_VENDIBLE"), de(AMAZON_A, movil("13", "ID"), vale("Azul", "ID", null, null), NINGUNO, Canal.AMAZON));
        assertEquals(new EstadoUnidad.Resultado("NECESITA_TALLER", "COLOR_O_GRADO"),
                de(WEB_A, movil("13", "ID"), vale("Azul", "ID", null, null), NINGUNO, Canal.DISTRIBUIDORES),
                "un ID no está prohibido fuera de Amazon: no cuadra por grado");
        TrabajoAbierto glass = new TrabajoAbierto("GLASS", "Técnico 1", LocalDateTime.of(2026, 11, 1, 9, 0), null);
        assertEquals(new EstadoUnidad.Resultado("EN_TALLER", null), de(AMAZON_A, movil("13", "C"), vale("Azul", "C", "i13128AzulA", null), List.of(glass), Canal.AMAZON));
        assertEquals(new EstadoUnidad.Resultado("NECESITA_TALLER", "C_SIN_REPARAR"), de(AMAZON_A, movil("13", "C"), vale("Azul", "C", "i13128AzulA", null), NINGUNO, Canal.AMAZON));
        assertEquals(new EstadoUnidad.Resultado("CUADRA", null), de(AMAZON_A, movil("13", "C"), vale("Azul", "A", "i13128AzulA", "C"), NINGUNO, Canal.AMAZON),
                "un C que el taller ya dejó en A cuadra");
        assertEquals(new EstadoUnidad.Resultado("CUADRA", null), de(AMAZON_A, a, vale("Azul", "A", "I13128AZULA", null), NINGUNO, Canal.AMAZON));
        assertEquals(new EstadoUnidad.Resultado("CON_AVISO", "MS"), de(AMAZON_A, movil("13", "MsA"), vale("Azul", "MsA", "i13128AzulMs", null), NINGUNO, Canal.AMAZON));
        assertEquals(new EstadoUnidad.Resultado("CON_AVISO", "MS"), de(WEB_A, movil("13", "MsA"), vale("Azul", "MsA", "i13128AzulMs", null), NINGUNO, Canal.DISTRIBUIDORES),
                "en la web, un Ms del color y grado del pedido también avisa");
        assertEquals(new EstadoUnidad.Resultado("CON_AVISO", "B_POR_A"), de(AMAZON_A, movil("13", "B"), vale("Azul", "B", "i13128AzulB", null), NINGUNO, Canal.AMAZON));
        assertEquals(new EstadoUnidad.Resultado("NECESITA_TALLER", "COLOR_O_GRADO"), de(WEB_A, movil("13", "B"), vale("Azul", "B", "i13128AzulB", null), NINGUNO, Canal.DISTRIBUIDORES));
        assertEquals(new EstadoUnidad.Resultado("NECESITA_TALLER", "COLOR_O_GRADO"), de(AMAZON_A, a, vale("Rojo", "A", "i13128RojoA", null), NINGUNO, Canal.AMAZON));
    }

    @Test void unCConBateriaNuevaSigueSinReparar() {
        assertEquals(new EstadoUnidad.Resultado("NECESITA_TALLER", "C_SIN_REPARAR"),
                de(AMAZON_A, movil("13", "C"), vale("Azul", "C100", "i13128AzulA", "C"), NINGUNO, Canal.AMAZON),
                "solo la bateria no lo hace A");
        assertEquals(new EstadoUnidad.Resultado("NECESITA_TALLER", "C_SIN_REPARAR"),
                de(AMAZON_A, movil("13", "C100"), vale("Azul", "C", "i13128AzulA", "C100"), NINGUNO, Canal.AMAZON));
        assertEquals(new EstadoUnidad.Resultado("NECESITA_TALLER", "C_SIN_REPARAR"),
                de(AMAZON_A, movil("13", "MsC"), vale("Azul", "MsC", "i13128AzulA", null), NINGUNO, Canal.AMAZON));
        assertTrue(EstadoUnidad.cSinReparar(vale("Azul", "C100", null, "C")));
        assertFalse(EstadoUnidad.cSinReparar(vale("Azul", "A", null, "C")));
    }

    @Test void otrasCapacidadYGradosNoVendibles() {
        MovilNsys m256 = new MovilNsys("350000000000001", "BT-9001", "Apple iPhone 13 256GB Blue", "A", "Available", "CAJA LISTOS",
                "Proveedor A", LocalDate.of(2026, 9, 1), null, "i13256AzulA", "13", "13", "256GB", "Azul", "A", false,
                "Listos", "Listos", EstadoMovil.STOCK, null, null);
        assertEquals(new EstadoUnidad.Resultado("NO_CUADRA", "MODELO"),
                de(AMAZON_A, m256, vale("Azul", "A", "i13256AzulA", null), NINGUNO, Canal.AMAZON));
        Aceptacion stockroom = ACEPTA.para(Canal.AMAZON_STOCKROOM, "i13128AzulA");
        assertEquals(new EstadoUnidad.Resultado("NO_CUADRA", "GRADO_NO_VENDIBLE"),
                de(stockroom, movil("13", "ID"), vale("Azul", "ID", null, null), NINGUNO, Canal.AMAZON_STOCKROOM));
    }
}
