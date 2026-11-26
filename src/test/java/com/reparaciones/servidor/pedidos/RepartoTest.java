package com.reparaciones.servidor.pedidos;

import com.reparaciones.servidor.nsys.EstadoMovil;
import com.reparaciones.servidor.nsys.LoQueVale;
import com.reparaciones.servidor.nsys.MovilNsys;
import com.reparaciones.servidor.nsys.NsysDAO;
import com.reparaciones.servidor.nsys.ProductoMaestro;
import com.reparaciones.servidor.nsys.TrabajoAbierto;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Datos inventados: todo es un iPhone 13 128GB. El SKU de un móvil y de un producto del maestro es
 *  «i13128» + «E» si es eSIM + color + grado («n/a» → «NA»). */
class RepartoTest {

    private static final String LISTOS = "CAJA LISTOS";
    private static final String REPARACION = "CAJA REP. OFICINA";
    private static final String PULIR = "CAJA PULIR";
    private static final String GLASS = "CAJA GLASS";
    private static final String DEVOLUCION = "Funcionalidad pendiente";
    private static final String SIN_CAJA = "";

    private static final LocalDate HOY = LocalDate.of(2026, 11, 24);
    private static final LocalDate D1 = LocalDate.of(2026, 9, 1);
    private static final LocalDate D2 = LocalDate.of(2026, 9, 2);
    private static final LocalDate D3 = LocalDate.of(2026, 9, 3);
    private static final Map<String, List<TrabajoAbierto>> SIN_TRABAJOS = Map.of();
    private static final Set<String> NADIE = Set.of();

    private static String sku(boolean esim, String color, String grado) {
        return "i13128" + (esim ? "E" : "") + color + (grado.equalsIgnoreCase("n/a") ? "NA" : grado);
    }

    private static ProductoMaestro producto(boolean esim, String color, String grado) {
        return new ProductoMaestro("clave-" + sku(esim, color, grado), "13", "13", "128GB", color, grado, sku(esim, color, grado),
                null, "i13128" + color, esim);
    }

    /** Lo que acepta un pedido: los productos de ese formato y color con esos grados (el primero, el que pide si no hay A). */
    private static Aceptacion ac(boolean esim, String color, String... grados) {
        return new Aceptacion(Arrays.stream(grados).map(g -> producto(esim, color, g)).toList(), List.of());
    }

    private static final Aceptacion AMZ_AZUL_A = ac(false, "Azul", "A", "A+", "A-");
    private static final Aceptacion AMZ_AZUL_A_ESIM = ac(true, "Azul", "A", "A+", "A-");

    private static MovilNsys movil(String imei, boolean esim, String color, String grade, String location, LocalDate recepcion,
                                   EstadoMovil estado) {
        return new MovilNsys(imei, "BT-1", "Apple iPhone 13 128GB", grade, "Available", location, "Proveedor", recepcion, null,
                sku(esim, color, grade), "13", "13", "128GB", color, grade, esim, null, null, estado, null, null);
    }

    private static MovilNsys movil(String imei, boolean esim, String color, String grade, String location, LocalDate recepcion) {
        return movil(imei, esim, color, grade, location, recepcion, EstadoMovil.STOCK);
    }

    /** Físico, en Listos. */
    private static MovilNsys movil(String imei, String color, String grade, LocalDate recepcion) {
        return movil(imei, false, color, grade, LISTOS, recepcion);
    }

    private static LoQueVale.Valor vale(MovilNsys m) {
        return new LoQueVale.Valor(m.color(), m.grade(), m.sku(), null, null, null, false);
    }

    private static Reparto.Fila fila(int id, Canal canal, String pais, LocalDate fecha, Aceptacion ac) {
        return new Reparto.Fila(id, canal, pais, fecha, ac);
    }

    private static Reparto.Fila amazon(int id, Aceptacion ac) {
        return fila(id, Canal.AMAZON, "DE", HOY, ac);
    }

    private static List<Reparto.Resultado> de(List<Reparto.Fila> filas, List<MovilNsys> stock) {
        return Reparto.de(filas, stock, RepartoTest::vale, SIN_TRABAJOS, NADIE);
    }

    private static List<String> imeis(List<Reparto.Resultado> r) {
        return r.stream().map(Reparto.Resultado::imei).toList();
    }

    private static List<Integer> puestos(List<Reparto.Resultado> r) {
        return r.stream().map(Reparto.Resultado::puesto).toList();
    }

    /** n filas de Amazon (DE) del mismo pedido. */
    private static List<Reparto.Fila> amazonN(int n, Aceptacion ac) {
        List<Reparto.Fila> filas = new ArrayList<>();
        for (int i = 1; i <= n; i++) filas.add(amazon(i, ac));
        return filas;
    }

    private static Integer puestoAmazon(Aceptacion ac, String pais, MovilNsys m) {
        return Reparto.puestoAmazon(fila(1, Canal.AMAZON, pais, HOY, ac), m, vale(m), false);
    }

    private static Integer puestoWeb(Aceptacion ac, MovilNsys m) {
        return Reparto.puestoWeb(fila(1, Canal.DISTRIBUIDORES, "ES", HOY, ac), m, vale(m), false);
    }

    // ── Amazon, puesto 1 y orden de grado ──

    @Test void amazonPuesto1ConLosGradosDelAbanicoYNaEnOrdenDeGrado() {
        // Los más nuevos, los mejores grados: el grado manda sobre la antigüedad.
        List<MovilNsys> stock = List.of(
                movil("001", "Azul", "n/a", D1),
                movil("002", "Azul", "A100", D1),
                movil("003", "Azul", "A", D1),
                movil("004", "Azul", "A-100", D2),
                movil("005", "Azul", "A-", D3));
        List<Reparto.Resultado> r = de(amazonN(5, AMZ_AZUL_A), stock);
        assertEquals(List.of("005", "004", "003", "002", "001"), imeis(r));
        assertEquals(List.of(1, 1, 1, 1, 1), puestos(r));
        assertEquals(List.of(1, 2, 3, 4, 5), r.stream().map(Reparto.Resultado::idFila).toList());
        assertEquals("Puesto 1 · Caja · Listos · A-", r.get(0).porque());
    }

    @Test void aMasCuentaComoA() {
        List<MovilNsys> stock = List.of(movil("001", "Azul", "A+100", D1), movil("002", "Azul", "A+", D2));
        assertEquals(List.of("002", "001"), imeis(de(amazonN(2, AMZ_AZUL_A), stock)));
    }

    // ── Dentro de un puesto: caja, grado, antigüedad ──

    @Test void laCajaVaAntesQueElGrado() {
        MovilNsys aMenosGlass = movil("001", false, "Azul", "A-", GLASS, D1);
        MovilNsys aListos = movil("002", false, "Azul", "A", LISTOS, D2);
        MovilNsys aMenosPulir = movil("003", false, "Azul", "A-", PULIR, D1);
        MovilNsys aMenosReparacion = movil("004", false, "Azul", "A-", REPARACION, D3);
        MovilNsys aMenosPantalla = movil("005", false, "Azul", "A-", "C.R. Pantalla Rota", D2);
        MovilNsys aDevolucion = movil("006", false, "Azul", "A-", DEVOLUCION, D1);
        List<Reparto.Resultado> r = de(amazonN(6, AMZ_AZUL_A),
                List.of(aDevolucion, aMenosGlass, aListos, aMenosPulir, aMenosReparacion, aMenosPantalla));
        // Reparación = Pantalla rota: desempata la antigüedad.
        assertEquals(List.of("002", "005", "004", "003", "001", "006"), imeis(r));
    }

    @Test void laAntiguedadDesempataYDespuesElImei() {
        List<MovilNsys> stock = List.of(movil("003", "Azul", "A", D2), movil("002", "Azul", "A", D1), movil("001", "Azul", "A", D2));
        assertEquals(List.of("002", "001", "003"), imeis(de(amazonN(3, AMZ_AZUL_A), stock)));
    }

    // ── Amazon, puestos 2 a 8 ──

    @Test void puesto2BdeOtroColorAntesQuePuesto3EsimDelMismoColorYBantesQueC() {
        MovilNsys esimAzul = movil("001", true, "Azul", "A", LISTOS, D1);
        MovilNsys cRosa = movil("002", false, "Rosa", "C", LISTOS, D1);
        MovilNsys bRosa = movil("003", false, "Rosa", "B", LISTOS, D2);
        assertEquals(3, puestoAmazon(AMZ_AZUL_A, "DE", esimAzul));
        assertEquals(2, puestoAmazon(AMZ_AZUL_A, "DE", cRosa));
        assertEquals(2, puestoAmazon(AMZ_AZUL_A, "DE", bRosa));
        List<Reparto.Resultado> r = de(amazonN(3, AMZ_AZUL_A), List.of(esimAzul, cRosa, bRosa));
        assertEquals(List.of("003", "002", "001"), imeis(r));
        assertEquals(List.of(2, 2, 3), puestos(r));
    }

    @Test void puestos4a8EnOrdenAunqueLaCajaSeaPeor() {
        MovilNsys naRojo = movil("005", false, "Rojo", "n/a", GLASS, D1);
        MovilNsys naRojoEsim = movil("006", true, "Rojo", "n/a", GLASS, D1);
        MovilNsys aRojo = movil("007", false, "Rojo", "A", LISTOS, D1);
        MovilNsys aMenosRojoEsim = movil("008", true, "Rojo", "A-", LISTOS, D1);
        MovilNsys bEsim = movil("004", true, "Rojo", "B", GLASS, D1);
        List<Reparto.Resultado> r = de(amazonN(5, AMZ_AZUL_A), List.of(aMenosRojoEsim, aRojo, naRojoEsim, naRojo, bEsim));
        assertEquals(List.of("004", "005", "006", "007", "008"), imeis(r));
        assertEquals(List.of(4, 5, 6, 7, 8), puestos(r));
    }

    @Test void pedidoEsimEsElInversoExacto() {
        Aceptacion e = AMZ_AZUL_A_ESIM;
        assertEquals(1, puestoAmazon(e, "DE", movil("1", true, "Azul", "A", LISTOS, D1)));
        assertEquals(2, puestoAmazon(e, "DE", movil("2", true, "Rosa", "B", LISTOS, D1)));
        assertEquals(3, puestoAmazon(e, "DE", movil("3", false, "Azul", "A-", LISTOS, D1)));
        assertEquals(3, puestoAmazon(e, "DE", movil("3", false, "Azul", "n/a", LISTOS, D1)));
        assertEquals(4, puestoAmazon(e, "DE", movil("4", false, "Rosa", "C", LISTOS, D1)));
        assertEquals(5, puestoAmazon(e, "DE", movil("5", true, "Rojo", "n/a", LISTOS, D1)));
        assertEquals(6, puestoAmazon(e, "DE", movil("6", false, "Rojo", "n/a", LISTOS, D1)));
        assertEquals(7, puestoAmazon(e, "DE", movil("7", true, "Rojo", "A", LISTOS, D1)));
        assertEquals(8, puestoAmazon(e, "DE", movil("8", false, "Rojo", "A-", LISTOS, D1)));
        assertNull(puestoAmazon(e, "ES", movil("9", true, "Azul", "MsA", LISTOS, D1)), "Ms: nunca");
    }

    // ── El modelo del maestro con «eSIM» (0.9.48.4): el maestro y el espejo llaman «13 eSIM» al eSIM y «13» al físico ──

    /** Como movil(...), pero con el modelo como lo pone el maestro de verdad: «13 eSIM» si es eSIM. */
    private static MovilNsys movilMaestro(String imei, boolean esim, String color, String grade, String location) {
        return new MovilNsys(imei, "BT-1", "Apple iPhone 13 128GB", grade, "Available", location, "Proveedor", D1, null,
                sku(esim, color, grade), "13", esim ? "13 eSIM" : "13", "128GB", color, grade, esim, null, null,
                EstadoMovil.STOCK, null, null);
    }

    /** Como ac(...), con el modelo del maestro de verdad. */
    private static Aceptacion acMaestro(boolean esim, String color, String... grados) {
        return new Aceptacion(Arrays.stream(grados).map(g -> new ProductoMaestro("clave-" + sku(esim, color, g), "13",
                esim ? "13 eSIM" : "13", "128GB", color, g, sku(esim, color, g), null, "i13128" + color, esim)).toList(), List.of());
    }

    private static Reparto.Resultado unaFila(Canal canal, Aceptacion ac, MovilNsys... stock) {
        return de(List.of(fila(1, canal, "DE", HOY, ac)), List.of(stock)).get(0);
    }

    @Test void pedidoFisicoReservaElEsimDelMismoColorYGradoAntesQueUnNaFisicoDeOtroColor() {
        // Caso de la fila 29468 (26/11): dos eSIM del color y grado del pedido libres, y reservó un N/A físico de otro color.
        MovilNsys esimAzul = movilMaestro("001", true, "Azul", "A100", GLASS);
        MovilNsys naRojo = movilMaestro("002", false, "Rojo", "n/a", SIN_CAJA);
        Reparto.Resultado r = unaFila(Canal.AMAZON, acMaestro(false, "Azul", "A", "A+", "A-", "A100"), naRojo, esimAzul);
        assertEquals("001", r.imei());
        assertEquals(3, r.puesto());
    }

    @Test void pedidoFisicoEsimByCEsPuesto4() {
        assertEquals(4, unaFila(Canal.AMAZON, acMaestro(false, "Azul", "A"), movilMaestro("001", true, "Azul", "B", LISTOS)).puesto());
        assertEquals(4, unaFila(Canal.AMAZON, acMaestro(false, "Azul", "A"), movilMaestro("002", true, "Azul", "C", LISTOS)).puesto());
    }

    @Test void pedidoFisicoEsimNaDeOtroColorEsPuesto6() {
        assertEquals(6, unaFila(Canal.AMAZON, acMaestro(false, "Azul", "A"), movilMaestro("001", true, "Rojo", "n/a", LISTOS)).puesto());
    }

    @Test void pedidoFisicoEsimAoAMenosDeOtroColorEsPuesto8() {
        assertEquals(8, unaFila(Canal.AMAZON, acMaestro(false, "Azul", "A"), movilMaestro("001", true, "Rojo", "A", LISTOS)).puesto());
        assertEquals(8, unaFila(Canal.AMAZON, acMaestro(false, "Azul", "A"), movilMaestro("002", true, "Rojo", "A-", LISTOS)).puesto());
    }

    @Test void pedidoEsimReservaElFisicoDelMismoColorYGrado() {
        Reparto.Resultado r = unaFila(Canal.AMAZON, acMaestro(true, "Azul", "A"), movilMaestro("001", false, "Azul", "A", LISTOS));
        assertEquals("001", r.imei());
        assertEquals(3, r.puesto());
    }

    @Test void laWebSigueSinAceptarElOtroFormato() {
        // Mismo color y grado: solo el formato es distinto. La web nunca coge otro formato.
        assertNull(unaFila(Canal.DISTRIBUIDORES, acMaestro(false, "Azul", "A"), movilMaestro("001", true, "Azul", "A", LISTOS)).imei());
        assertNull(unaFila(Canal.PARTICULARES, acMaestro(true, "Azul", "A"), movilMaestro("002", false, "Azul", "A", LISTOS)).imei());
        // Y el del mismo formato sí, aunque el maestro le ponga «eSIM» al modelo.
        assertEquals(1, unaFila(Canal.DISTRIBUIDORES, acMaestro(true, "Azul", "A"), movilMaestro("003", true, "Azul", "A", LISTOS)).puesto());
    }

    // ── Ms: nunca se reserva (Imad 25/11; lo decide y lo pica una persona) ──

    @Test void msNuncaSeReservaNiAEspanaNiComoUltimoRecurso() {
        MovilNsys ms = movil("001", "Azul", "MsA", D1);
        MovilNsys aRojo = movil("002", false, "Rojo", "A", GLASS, D3);
        for (String g : List.of("MsA", "MsA-", "MsA+", "MsB", "MsC", "MSA", "Ms", "MsA-100", "MsC100")) {
            assertNull(puestoAmazon(AMZ_AZUL_A, "ES", movil("003", "Azul", g, D1)), g);
            assertNull(puestoAmazon(AMZ_AZUL_A, "DE", movil("003", "Azul", g, D1)), g);
        }

        List<Reparto.Fila> espana = List.of(fila(1, Canal.AMAZON, "ES", HOY, AMZ_AZUL_A), fila(2, Canal.AMAZON, "ES", HOY, AMZ_AZUL_A));
        List<Reparto.Resultado> r = de(espana, List.of(ms, aRojo));
        assertEquals(Arrays.asList("002", null), imeis(r), "la segunda fila se queda sin stock aunque haya un Ms");
    }

    @Test void unMsASecasTampocoAunqueElPedidoLoAcepte() {
        MovilNsys ms = movil("001", "Azul", "Ms", D1);
        assertNull(puestoAmazon(ac(false, "Azul", "Ms"), "ES", ms));
        assertNull(puestoWeb(ac(false, "Azul", "Ms"), ms));
    }

    @Test void msNuncaEnLaWeb() {
        MovilNsys ms = movil("001", "Azul", "MsA", D1);
        assertNull(puestoWeb(ac(false, "Azul", "MsA"), ms), "ni con el SKU exacto");
        assertNull(puestoWeb(ac(false, "Azul", "A"), ms));
    }

    // ── Nunca: ID, F, FD, RMA. Devolución y sin caja = N/A ──

    @Test void idNunca() {
        MovilNsys id = movil("001", "Azul", "ID", D1);
        assertNull(puestoAmazon(AMZ_AZUL_A, "ES", id));
        assertNull(puestoWeb(ac(false, "Azul", "ID"), id));
        assertNull(puestoAmazon(AMZ_AZUL_A, "ES", movil("002", "Azul", "MsID", D1)));
        assertEquals(Arrays.asList((String) null), imeis(de(amazonN(1, AMZ_AZUL_A), List.of(id))));
    }

    @Test void fFdYRmaNuncaPorqueNoSonStock() {
        List<MovilNsys> stock = List.of(movil("001", "Azul", "F", D1), movil("002", "Azul", "FD", D1),
                movil("003", "Azul", "rma", D1), movil("004", false, "Azul", "A", LISTOS, D1, EstadoMovil.DE_PEDIDO),
                movil("005", false, "Azul", "A", LISTOS, D1, EstadoMovil.NO_CUENTA));
        assertEquals(Arrays.asList(null, null), imeis(de(amazonN(2, AMZ_AZUL_A), stock)));
    }

    @Test void devolucionYSinCajaCuentanComoNa() {
        MovilNsys aDevolucion = movil("001", false, "Azul", "A", DEVOLUCION, D1);
        MovilNsys aSinCaja = movil("002", false, "Azul", "A", SIN_CAJA, D1);
        MovilNsys aNull = movil("003", false, "Azul", "A", null, D2);
        MovilNsys aRojoDevolucion = movil("004", false, "Rojo", "A", DEVOLUCION, D1);
        MovilNsys bRojoSinCaja = movil("005", false, "Rojo", "B", SIN_CAJA, D1);
        assertEquals(1, puestoAmazon(AMZ_AZUL_A, "DE", aDevolucion));
        assertEquals(1, puestoAmazon(AMZ_AZUL_A, "DE", aNull));
        assertEquals(5, puestoAmazon(AMZ_AZUL_A, "DE", aRojoDevolucion), "N/A de otro color, no A de otro color");
        assertEquals(5, puestoAmazon(AMZ_AZUL_A, "DE", bRojoSinCaja), "N/A, no B");
        // En la web no valen como exacto.
        assertNull(puestoWeb(ac(false, "Azul", "A"), aDevolucion));
        assertNull(puestoWeb(ac(false, "Azul", "A"), aSinCaja));

        List<Reparto.Resultado> r = de(amazonN(1, AMZ_AZUL_A), List.of(aDevolucion));
        assertEquals("Puesto 1 · Devolución · N/A (es A)", r.get(0).porque());
        assertEquals("Puesto 1 · En revisión · N/A (es A)", de(amazonN(1, AMZ_AZUL_A), List.of(aSinCaja)).get(0).porque());
    }

    @Test void idEnDevolucionOSinCajaNoSeReserva() {
        MovilNsys idDevolucion = movil("001", false, "Azul", "ID", DEVOLUCION, D1);
        MovilNsys idSinCaja = movil("002", false, "Azul", "id", SIN_CAJA, D1);
        MovilNsys idRojoDevolucion = movil("003", false, "Rojo", "ID", DEVOLUCION, D1);
        for (MovilNsys m : List.of(idDevolucion, idSinCaja, idRojoDevolucion)) {
            assertNull(puestoAmazon(AMZ_AZUL_A, "ES", m), m.imeiSn());
            assertNull(puestoWeb(ac(false, "Azul", "ID"), m), m.imeiSn());
        }
        assertEquals(Arrays.asList(null, null, null),
                imeis(de(amazonN(3, AMZ_AZUL_A), List.of(idDevolucion, idSinCaja, idRojoDevolucion))));
    }

    @Test void msEnDevolucionOSinCajaTampocoComoNa() {
        MovilNsys msDevolucion = movil("001", false, "Azul", "MsA", DEVOLUCION, D1);
        MovilNsys msSinCaja = movil("002", false, "Azul", "MsA-", SIN_CAJA, D1);
        MovilNsys msRojoDevolucion = movil("003", false, "Rojo", "MsA", DEVOLUCION, D1);
        MovilNsys msEsimDevolucion = movil("004", true, "Azul", "MsA", DEVOLUCION, D1);
        for (MovilNsys m : List.of(msDevolucion, msSinCaja, msRojoDevolucion, msEsimDevolucion)) {
            assertNull(puestoAmazon(AMZ_AZUL_A, "ES", m), m.imeiSn());
            assertNull(puestoAmazon(AMZ_AZUL_A, "DE", m), m.imeiSn());
        }
        assertNull(puestoWeb(ac(false, "Azul", "MsA"), msDevolucion));
        assertEquals(Arrays.asList((String) null), imeis(de(List.of(fila(1, Canal.AMAZON, "ES", HOY, AMZ_AZUL_A)),
                List.of(msDevolucion, msSinCaja, msRojoDevolucion, msEsimDevolucion))));
    }

    @Test void colorOModeloEnBlancoNoCuentanComoIguales() {
        Aceptacion sinColor = ac(false, null, "A");
        MovilNsys aSinColor = movil("001", null, "A", D1);
        assertNull(puestoWeb(sinColor, aSinColor), "null == null no es el mismo color");
        assertEquals(7, puestoAmazon(sinColor, "DE", aSinColor), "A de otro color, no puesto 1");
        assertNull(puestoWeb(ac(false, "Azul", "A"), movil("002", " ", "A", D1)));

        ProductoMaestro sinModelo = new ProductoMaestro("c", "13", null, null, "Azul", "A", "i13128AzulA", null, "i13128Azul", false);
        Aceptacion acSinModelo = new Aceptacion(List.of(sinModelo), List.of());
        MovilNsys movilSinModelo = new MovilNsys("003", "BT-1", "x", "A", "Available", LISTOS, "P", D1, null, "i13128AzulA", "13",
                null, null, "Azul", "A", false, null, null, EstadoMovil.STOCK, null, null);
        assertNull(puestoAmazon(acSinModelo, "ES", movilSinModelo));
        assertNull(puestoWeb(acSinModelo, movilSinModelo));
    }

    @Test void aceptacionSinProductosNoReservaNiFalla() {
        Aceptacion vacia = new Aceptacion(List.of(), List.of());
        MovilNsys a = movil("001", "Azul", "A", D1);
        assertNull(puestoAmazon(vacia, "ES", a));
        assertNull(puestoWeb(vacia, a));
        assertEquals(List.of(new Reparto.Resultado(1, null, null, null)), de(List.of(amazon(1, vacia)), List.of(a)));
    }

    // ── Web ──

    @Test void webPuesto1EsElSkuExacto() {
        Aceptacion webA = ac(false, "Azul", "A");
        assertEquals(1, puestoWeb(webA, movil("001", "Azul", "A", D1)));
        assertNull(puestoWeb(webA, movil("002", "Azul", "A-", D1)));
        assertNull(puestoWeb(webA, movil("003", "Azul", "A100", D1)), "el «100» cuenta");
        assertNull(puestoWeb(webA, movil("004", "Azul", "n/a", D1)));
        Aceptacion webA100 = ac(false, "Azul", "A100");
        assertEquals(1, puestoWeb(webA100, movil("005", "Azul", "A100", D1)));
        assertEquals(1, puestoWeb(ac(true, "Azul", "A"), movil("006", true, "Azul", "A", LISTOS, D1)));
    }

    @Test void webBoCSoloConPedidoAoA100DelMismoColorYFormato() {
        MovilNsys b = movil("001", "Azul", "B", D1);
        MovilNsys c = movil("002", false, "Azul", "C", REPARACION, D1);
        assertEquals(2, puestoWeb(ac(false, "Azul", "A"), b));
        assertEquals(2, puestoWeb(ac(false, "Azul", "A"), c));
        assertEquals(2, puestoWeb(ac(false, "Azul", "A100"), b));
        assertNull(puestoWeb(ac(false, "Azul", "A-"), b), "para A- no");
        assertNull(puestoWeb(ac(false, "Azul", "A-100"), b));
        assertNull(puestoWeb(ac(false, "Rosa", "A"), b), "otro color, no");
        assertNull(puestoWeb(ac(true, "Azul", "A"), b), "otro formato, no");
        assertNull(puestoWeb(ac(false, "Azul", "A"), movil("003", "Azul", "MsB", D1)));

        Reparto.Fila dist = fila(1, Canal.DISTRIBUIDORES, "ES", HOY, ac(false, "Azul", "A"));
        Reparto.Fila dist2 = fila(2, Canal.DISTRIBUIDORES, "ES", HOY, ac(false, "Azul", "A"));
        List<Reparto.Resultado> r = de(List.of(dist, dist2), List.of(c, b, movil("004", false, "Azul", "A", GLASS, D3)));
        assertEquals(List.of("004", "001"), imeis(r));
        assertEquals(List.of(1, 2), puestos(r));
    }

    @Test void webNuncaOtroColorNiOtroFormato() {
        Aceptacion webA = ac(false, "Azul", "A");
        assertNull(puestoWeb(webA, movil("001", "Rosa", "A", D1)));
        assertNull(puestoWeb(webA, movil("002", true, "Azul", "A", LISTOS, D1)));
        assertNull(puestoWeb(webA, movil("003", "Rosa", "n/a", D1)));
    }

    // ── Prioridad entre filas ──

    @Test void amazonVaAntesQueLaWebAunqueLaWebSeaMasAntigua() {
        MovilNsys a = movil("001", "Azul", "A", D1);
        Reparto.Fila web = fila(1, Canal.DISTRIBUIDORES, "ES", HOY.minusDays(5), ac(false, "Azul", "A"));
        Reparto.Fila amz = fila(2, Canal.AMAZON_STOCKROOM, "DE", HOY, AMZ_AZUL_A);
        List<Reparto.Resultado> r = de(List.of(web, amz), List.of(a));
        assertEquals(List.of(2, 1), r.stream().map(Reparto.Resultado::idFila).toList());
        assertEquals(Arrays.asList("001", null), imeis(r));
    }

    @Test void ordenDeCanalDespuesFechaDespuesId() {
        Aceptacion webA = ac(false, "Azul", "A");
        List<Reparto.Fila> filas = List.of(
                fila(1, Canal.BACKIPHONE, "ES", D1, webA),
                fila(2, Canal.PARTICULARES, "ES", D1, webA),
                fila(3, Canal.DISTRIBUIDORES, "ES", D3, webA),
                fila(4, Canal.DISTRIBUIDORES, "ES", D2, webA),
                fila(9, Canal.AMAZON, "DE", D3, AMZ_AZUL_A),
                fila(8, Canal.AMAZON_STOCKROOM, "DE", D3, AMZ_AZUL_A),
                fila(7, Canal.AMAZON, "DE", null, AMZ_AZUL_A));
        List<Reparto.Resultado> r = de(filas, List.of());
        assertEquals(List.of(8, 9, 7, 4, 3, 2, 1), r.stream().map(Reparto.Resultado::idFila).toList());
        assertTrue(r.stream().allMatch(x -> x.imei() == null && x.puesto() == null));
    }

    @Test void variasFilasDelMismoPedidoCogenCadaUnaElSiguienteYLaQueNoTieneSaleSinStock() {
        List<MovilNsys> stock = List.of(movil("001", "Azul", "A", D1), movil("002", "Azul", "A", D2));
        List<Reparto.Resultado> r = de(amazonN(3, AMZ_AZUL_A), stock);
        assertEquals(Arrays.asList("001", "002", null), imeis(r));
        assertEquals(new Reparto.Resultado(3, null, null, null), r.get(2));
    }

    @Test void filaSinAceptacionSaleSinStock() {
        List<Reparto.Resultado> r = de(List.of(amazon(1, null)), List.of(movil("001", "Azul", "A", D1)));
        assertEquals(List.of(new Reparto.Resultado(1, null, null, null)), r);
    }

    // ── Elegibles: modelo, ocupados, trabajos ──

    @Test void soloElMismoModeloYCapacidad() {
        MovilNsys otroModelo = new MovilNsys("001", "BT-1", "x", "A", "Available", LISTOS, "P", D1, null, "i13128AzulA", "13",
                "13 mini", "128GB", "Azul", "A", false, null, null, EstadoMovil.STOCK, null, null);
        MovilNsys otraCapacidad = new MovilNsys("002", "BT-1", "x", "A", "Available", LISTOS, "P", D1, null, "i13128AzulA", "13",
                "13", "256GB", "Azul", "A", false, null, null, EstadoMovil.STOCK, null, null);
        assertNull(puestoAmazon(AMZ_AZUL_A, "ES", otroModelo));
        assertNull(puestoAmazon(AMZ_AZUL_A, "ES", otraCapacidad));
        assertNull(puestoWeb(ac(false, "Azul", "A"), otroModelo));
        assertEquals(Arrays.asList((String) null), imeis(de(amazonN(1, AMZ_AZUL_A), List.of(otroModelo, otraCapacidad))));
    }

    @Test void unOcupadoNoEntra() {
        List<MovilNsys> stock = List.of(movil("350000000000001", "Azul", "A", D1), movil("350000000000002", "Azul", "A", D2));
        List<Reparto.Resultado> r = Reparto.de(amazonN(2, AMZ_AZUL_A), stock, RepartoTest::vale, SIN_TRABAJOS,
                Set.of(NsysDAO.claveCruce(" 350000000000001 ")));
        assertEquals(Arrays.asList("350000000000002", null), imeis(r));
    }

    @Test void conTrabajoAbiertoSoloPuesto1YConElSkuAceptado() {
        TrabajoAbierto t = new TrabajoAbierto("REPARACION", "Ana", LocalDateTime.of(2026, 11, 20, 10, 0), null);
        MovilNsys aConTrabajo = movil("001", "Azul", "A", D1);
        MovilNsys naConTrabajo = movil("002", "Azul", "n/a", D1);
        MovilNsys bConTrabajo = movil("003", "Rosa", "B", D1);
        MovilNsys aMenos100ConTrabajo = movil("004", "Azul", "A-100", D1);
        Reparto.Fila f = amazon(1, AMZ_AZUL_A);
        assertEquals(1, Reparto.puestoAmazon(f, aConTrabajo, vale(aConTrabajo), true));
        assertNull(Reparto.puestoAmazon(f, naConTrabajo, vale(naConTrabajo), true), "N/A: su SKU no es de los aceptados");
        assertNull(Reparto.puestoAmazon(f, bConTrabajo, vale(bConTrabajo), true), "puesto 2 con trabajo, no");
        assertNull(Reparto.puestoAmazon(f, aMenos100ConTrabajo, vale(aMenos100ConTrabajo), true), "puesto 1 sin el SKU aceptado, no");
        Reparto.Fila web = fila(2, Canal.PARTICULARES, "ES", HOY, ac(false, "Azul", "A"));
        assertEquals(1, Reparto.puestoWeb(web, aConTrabajo, vale(aConTrabajo), true));
        MovilNsys bAzul = movil("005", "Azul", "B", D1);
        assertNull(Reparto.puestoWeb(web, bAzul, vale(bAzul), true));

        Map<String, List<TrabajoAbierto>> trabajos = Map.of(NsysDAO.claveCruce("002"), List.of(t), NsysDAO.claveCruce("003"), List.of(t));
        List<Reparto.Resultado> r = Reparto.de(amazonN(2, AMZ_AZUL_A), List.of(naConTrabajo, bConTrabajo), RepartoTest::vale,
                trabajos, NADIE);
        assertEquals(Arrays.asList(null, null), imeis(r));
    }

    // ── El simulador del prototipo (assets/2026-11-24-pedidos-reserva-imei-prototipo.html) ──

    @Test void elCasoDelSimuladorDaLoMismoQueElPrototipo() {
        LocalDate base = LocalDate.of(2026, 11, 24);
        List<MovilNsys> stock = List.of(
                movil("…001", false, "Azul", "A-", LISTOS, base.minusDays(40)),
                movil("…002", false, "Azul", "A", LISTOS, base.minusDays(60)),
                movil("…003", false, "Azul", "A-100", GLASS, base.minusDays(10)),
                movil("…004", false, "Azul", "MsA", LISTOS, base.minusDays(90)),
                movil("…005", false, "Azul", "A", SIN_CAJA, base.minusDays(5)),
                movil("…006", false, "Rosa", "B", LISTOS, base.minusDays(30)),
                movil("…007", true, "Azul", "A", LISTOS, base.minusDays(20)),
                movil("…008", false, "Rosa", "C", REPARACION, base.minusDays(15)),
                movil("…009", false, "Rojo", "A", LISTOS, base.minusDays(50)),
                movil("…010", false, "Azul", "A", DEVOLUCION, base.minusDays(70)),
                movil("…011", true, "Rosa", "A-", PULIR, base.minusDays(12)),
                movil("…012", false, "Azul", "ID", LISTOS, base.minusDays(80)));
        // Las filas, desordenadas: id = pedido*10 + unidad.
        List<Reparto.Fila> filas = List.of(
                fila(71, Canal.BACKIPHONE, "ES", LocalDate.of(2026, 11, 23), ac(false, "Rosa", "A")),
                fila(61, Canal.PARTICULARES, "ES", LocalDate.of(2026, 11, 22), ac(false, "Azul", "A-")),
                fila(51, Canal.DISTRIBUIDORES, "ES", LocalDate.of(2026, 11, 20), ac(false, "Azul", "A")),
                fila(52, Canal.DISTRIBUIDORES, "ES", LocalDate.of(2026, 11, 20), ac(false, "Azul", "A")),
                fila(41, Canal.AMAZON, "IT", LocalDate.of(2026, 11, 23), AMZ_AZUL_A_ESIM),
                fila(31, Canal.AMAZON_STOCKROOM, "FR", LocalDate.of(2026, 11, 22), ac(false, "Rosa", "A", "A+", "A-")),
                fila(22, Canal.AMAZON, "ES", LocalDate.of(2026, 11, 21), AMZ_AZUL_A),
                fila(21, Canal.AMAZON, "ES", LocalDate.of(2026, 11, 21), AMZ_AZUL_A),
                fila(11, Canal.AMAZON, "DE", LocalDate.of(2026, 11, 21), AMZ_AZUL_A));
        // A1 y A2 entraron el mismo día (08:00 y 10:00): la fecha del pedido es un día, así que desempata el id.
        List<Reparto.Resultado> r = de(filas, stock);
        assertEquals(List.of(11, 21, 22, 31, 41, 51, 52, 61, 71), r.stream().map(Reparto.Resultado::idFila).toList());
        assertEquals(Arrays.asList("…001", "…002", "…003", "…006", "…007", null, null, null, "…008"), imeis(r));
        assertEquals(Arrays.asList(1, 1, 1, 2, 1, null, null, null, 2), puestos(r));
    }
}
