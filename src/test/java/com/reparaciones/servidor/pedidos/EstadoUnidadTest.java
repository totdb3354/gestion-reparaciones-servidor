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

    private static MovilNsys movil(String modeloMaestro, String capacidad, String grade, boolean esEsim) {
        return new MovilNsys("350000000000001", "BT-9001", "Apple iPhone 13 128GB Blue", grade, "Available", "CAJA LISTOS",
                "Proveedor A", LocalDate.of(2026, 9, 1), null, "i13128AzulA", "13", modeloMaestro, capacidad, "Azul", grade, esEsim,
                "Listos", "Listos", EstadoMovil.STOCK, null, null);
    }

    private static EstadoUnidad.Resultado r(String estado, String motivo, String sim, boolean na, boolean ms, String dato) {
        return new EstadoUnidad.Resultado(estado, motivo, sim, na, ms, dato);
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
        assertEquals(new EstadoUnidad.Resultado("NO_CUADRA", "MODELO", null, false, false, "13 mini"), de(AMAZON_A, movil("13 mini", "A"), vale("Azul", "A", "x", null), NINGUNO, Canal.AMAZON));
        assertEquals(new EstadoUnidad.Resultado("NO_CUADRA", "GRADO_NO_VENDIBLE", null, false, false, "Fd"), de(AMAZON_A, movil("13", "Fd"), vale("Azul", "Fd", null, null), NINGUNO, Canal.AMAZON));
        assertEquals(new EstadoUnidad.Resultado("NO_CUADRA", "GRADO_NO_VENDIBLE", null, false, false, "ID"), de(AMAZON_A, movil("13", "ID"), vale("Azul", "ID", null, null), NINGUNO, Canal.AMAZON));
        assertEquals(new EstadoUnidad.Resultado("NECESITA_TALLER", "GRADO", null, false, false, "ID"),
                de(WEB_A, movil("13", "ID"), vale("Azul", "ID", null, null), NINGUNO, Canal.DISTRIBUIDORES),
                "un ID no está prohibido fuera de Amazon: no cuadra por grado");
        TrabajoAbierto glass = new TrabajoAbierto("GLASS", "Técnico 1", LocalDateTime.of(2026, 11, 1, 9, 0), null);
        assertEquals(new EstadoUnidad.Resultado("EN_TALLER", null), de(AMAZON_A, movil("13", "C"), vale("Azul", "C", "i13128AzulA", null), List.of(glass), Canal.AMAZON));
        assertEquals(new EstadoUnidad.Resultado("NECESITA_TALLER", "GRADO", null, false, false, "C"), de(AMAZON_A, movil("13", "C"), vale("Azul", "C", "i13128AzulA", null), NINGUNO, Canal.AMAZON));
        assertEquals(new EstadoUnidad.Resultado("CUADRA", null), de(AMAZON_A, movil("13", "C"), vale("Azul", "A", "i13128AzulA", "C"), NINGUNO, Canal.AMAZON),
                "un C que el taller ya dejó en A cuadra");
        assertEquals(new EstadoUnidad.Resultado("CUADRA", null), de(AMAZON_A, a, vale("Azul", "A", "I13128AZULA", null), NINGUNO, Canal.AMAZON));
        assertEquals(new EstadoUnidad.Resultado("CON_AVISO", null, null, false, true, null), de(AMAZON_A, movil("13", "MsA"), vale("Azul", "MsA", "i13128AzulMs", null), NINGUNO, Canal.AMAZON));
        assertEquals(new EstadoUnidad.Resultado("CON_AVISO", null, null, false, true, null), de(WEB_A, movil("13", "MsA"), vale("Azul", "MsA", "i13128AzulMs", null), NINGUNO, Canal.DISTRIBUIDORES),
                "en la web, un Ms del color y grado del pedido también avisa");
        assertEquals(new EstadoUnidad.Resultado("CON_AVISO", "B_POR_A"), de(AMAZON_A, movil("13", "B"), vale("Azul", "B", "i13128AzulB", null), NINGUNO, Canal.AMAZON));
        assertEquals(new EstadoUnidad.Resultado("NECESITA_TALLER", "GRADO", null, false, false, "B"), de(WEB_A, movil("13", "B"), vale("Azul", "B", "i13128AzulB", null), NINGUNO, Canal.DISTRIBUIDORES));
        assertEquals(new EstadoUnidad.Resultado("NECESITA_TALLER", "COLOR", null, false, false, "Rojo"), de(AMAZON_A, a, vale("Rojo", "A", "i13128RojoA", null), NINGUNO, Canal.AMAZON));
    }

    @Test void unCConBateriaNuevaSigueSinReparar() {
        assertEquals(new EstadoUnidad.Resultado("NECESITA_TALLER", "GRADO", null, false, false, "C"),
                de(AMAZON_A, movil("13", "C"), vale("Azul", "C100", "i13128AzulA", "C"), NINGUNO, Canal.AMAZON),
                "solo la bateria no lo hace A");
        assertEquals(new EstadoUnidad.Resultado("NECESITA_TALLER", "GRADO", null, false, false, "C"),
                de(AMAZON_A, movil("13", "C100"), vale("Azul", "C", "i13128AzulA", "C100"), NINGUNO, Canal.AMAZON));
        assertEquals(r("NECESITA_TALLER", "GRADO", null, false, true, "C"),
                de(AMAZON_A, movil("13", "MsC"), vale("Azul", "MsC", "i13128AzulA", null), NINGUNO, Canal.AMAZON));
        assertTrue(EstadoUnidad.cSinReparar(vale("Azul", "C100", null, "C")));
        assertFalse(EstadoUnidad.cSinReparar(vale("Azul", "A", null, "C")));
    }

    @Test void otrasCapacidadYGradosNoVendibles() {
        MovilNsys m256 = new MovilNsys("350000000000001", "BT-9001", "Apple iPhone 13 256GB Blue", "A", "Available", "CAJA LISTOS",
                "Proveedor A", LocalDate.of(2026, 9, 1), null, "i13256AzulA", "13", "13", "256GB", "Azul", "A", false,
                "Listos", "Listos", EstadoMovil.STOCK, null, null);
        assertEquals(new EstadoUnidad.Resultado("NO_CUADRA", "CAPACIDAD", null, false, false, "256GB"),
                de(AMAZON_A, m256, vale("Azul", "A", "i13256AzulA", null), NINGUNO, Canal.AMAZON));
        Aceptacion stockroom = ACEPTA.para(Canal.AMAZON_STOCKROOM, "i13128AzulA");
        assertEquals(new EstadoUnidad.Resultado("NO_CUADRA", "GRADO_NO_VENDIBLE", null, false, false, "ID"),
                de(stockroom, movil("13", "ID"), vale("Azul", "ID", null, null), NINGUNO, Canal.AMAZON_STOCKROOM));
    }

    /** Desde la 0.9.29 un n/a lleva SKU (el «SKU sin grado» + «NA»), que no es el SKU web de ninguna fila del maestro: el color y el
     *  grado se siguen mirando aunque el móvil tenga SKU. */
    @Test void conSkuQueNoEstaEnElPedidoSeMiranElColorYElGrado() {
        Aceptacion conFilaNa = new AceptaSku(List.of(
                AceptaSkuTest.producto("Azul", "A", "i13128AzulA", null, "i13128AzulX", null),
                AceptaSkuTest.producto("Azul", "n/a", null, null, "i13128AzulX", null))).para(Canal.PARTICULARES, "i13128AzulX");
        assertEquals(new EstadoUnidad.Resultado("CUADRA", null),
                de(conFilaNa, movil("13", "n/a"), vale("Azul", "n/a", "i13128AzulNA", null), NINGUNO, Canal.PARTICULARES),
                "un n/a de un pedido con fila n/a cuadra");
        assertEquals(r("CON_AVISO", null, "ESIM", false, false, null),
                de(conFilaNa, movil("13", "128GB", "n/a", true), vale("Azul", "n/a", "i13128AzulNA", null), NINGUNO, Canal.PARTICULARES),
                "un n/a aceptado por el pedido pero con otra SIM: solo el aviso de SIM");
        assertEquals(r("CON_AVISO", null, null, true, false, null),
                de(WEB_A, movil("13", "n/a"), vale("Azul", "n/a", "i13128AzulNA", null), NINGUNO, Canal.DISTRIBUIDORES),
                "un n/a solo mira el color, haya o no fila n/a en el pedido");
        assertEquals(new EstadoUnidad.Resultado("CUADRA", null),
                de(WEB_A, a(), vale("Azul", "A", "i13128AzulA-2", null), NINGUNO, Canal.DISTRIBUIDORES),
                "un SKU que no es el del maestro, con el color y el grado del pedido, cuadra");
    }

    @Test void modeloYCapacidadVanSeparadosConLoQueEsElMovil() {
        assertEquals(r("NO_CUADRA", "MODELO", null, false, false, "14"),
                de(AMAZON_A, movil("14", "128GB", "A", false), vale("Azul", "A", "x", null), NINGUNO, Canal.AMAZON));
        assertEquals(r("NO_CUADRA", "CAPACIDAD", null, false, false, "256GB"),
                de(AMAZON_A, movil("13", "256GB", "A", false), vale("Azul", "A", "x", null), NINGUNO, Canal.AMAZON));
    }

    @Test void laSimDistintaSeApuntaYSeSigueMirando() {
        assertEquals(r("CON_AVISO", null, "ESIM", false, false, null),
                de(AMAZON_A, movil("13", "128GB", "A", true), vale("Azul", "A", "i13128AzulA", null), NINGUNO, Canal.AMAZON),
                "pide SIM física y es eSIM: solo el aviso de SIM");
        assertEquals(r("NECESITA_TALLER", "COLOR", "ESIM", false, false, "Rojo"),
                de(AMAZON_A, movil("13", "128GB", "A", true), vale("Rojo", "A", "i13128RojoA", null), NINGUNO, Canal.AMAZON));
        assertEquals(r("NO_CUADRA", "CAPACIDAD", "ESIM", false, false, "256GB"),
                de(AMAZON_A, movil("13", "256GB", "A", true), vale("Azul", "A", "x", null), NINGUNO, Canal.AMAZON),
                "con otra capacidad también se apunta la SIM");
    }

    @Test void unNaSoloMiraElColor() {
        assertEquals(r("CON_AVISO", null, null, true, false, null),
                de(AMAZON_A, movil("13", "128GB", "n/a", false), vale("Azul", "n/a", "i13128AzulNA", null), NINGUNO, Canal.AMAZON));
        assertEquals(r("NECESITA_TALLER", "COLOR", null, true, false, "Rosa"),
                de(AMAZON_A, movil("13", "128GB", "n/a", false), vale("Rosa", "n/a", "i13128RosaNA", null), NINGUNO, Canal.AMAZON));
        assertEquals(r("CON_AVISO", null, "ESIM", true, false, null),
                de(AMAZON_A, movil("13", "128GB", "n/a", true), vale("Azul", "n/a", "i13128AzulNA", null), NINGUNO, Canal.AMAZON));
    }

    @Test void colorYGradoSeparados() {
        assertEquals(r("NECESITA_TALLER", "COLOR", null, false, false, "Rojo"),
                de(AMAZON_A, movil("13", "A"), vale("Rojo", "A", "i13128RojoA", null), NINGUNO, Canal.AMAZON));
        assertEquals(r("NECESITA_TALLER", "GRADO", null, false, false, "B"),
                de(WEB_A, movil("13", "B"), vale("Azul", "B", "i13128AzulB", null), NINGUNO, Canal.DISTRIBUIDORES));
        assertEquals(r("NECESITA_TALLER", "GRADO", null, false, false, "C"),
                de(AMAZON_A, movil("13", "C"), vale("Azul", "C", "i13128AzulA", null), NINGUNO, Canal.AMAZON),
                "un C sin reparar es «grado: C»");
        assertEquals(r("NECESITA_TALLER", "COLOR", null, false, false, "Rojo"),
                de(WEB_A, movil("13", "B"), vale("Rojo", "B", "i13128RojoB", null), NINGUNO, Canal.DISTRIBUIDORES),
                "si fallan los dos, el color");
    }

    @Test void colorYGradoValenPorSeparadoPeroLaCombinacionNoExiste() {
        AceptaSku dos = new AceptaSku(List.of(
                AceptaSkuTest.producto("Azul", "A", "i13128X", null, null, null),
                AceptaSkuTest.producto("Rojo", "B", "i13128X", null, null, null)));
        Aceptacion ac = dos.para(Canal.DISTRIBUIDORES, "i13128X");
        assertEquals(r("NECESITA_TALLER", "COLOR_O_GRADO", null, false, false, null),
                de(ac, movil("13", "A"), vale("Rojo", "A", null, null), NINGUNO, Canal.DISTRIBUIDORES));
    }

    @Test void unMsEsUnaSenalYPuedeIrConOtroMotivo() {
        assertEquals(r("CON_AVISO", null, null, false, true, null),
                de(AMAZON_A, movil("13", "MsA"), vale("Azul", "MsA", "i13128AzulMs", null), NINGUNO, Canal.AMAZON));
        assertEquals(r("NECESITA_TALLER", "COLOR", null, false, true, "Rojo"),
                de(AMAZON_A, movil("13", "MsA"), vale("Rojo", "MsA", null, null), NINGUNO, Canal.AMAZON));
    }

    @Test void enElTallerNoMiraColorNiGradoNiApuntaNaNiMs() {
        TrabajoAbierto rep = new TrabajoAbierto("REPARACION", "Juan", LocalDateTime.of(2026, 11, 1, 9, 0), null);
        assertEquals(r("EN_TALLER", null, null, false, false, null),
                de(AMAZON_A, movil("13", "128GB", "n/a", false), vale("Rosa", "n/a", null, null), List.of(rep), Canal.AMAZON));
    }

    private static MovilNsys a() {
        return movil("13", "A");
    }
}
