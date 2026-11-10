package com.reparaciones.servidor.pedidos;

import com.reparaciones.servidor.nsys.EstadoMovil;
import com.reparaciones.servidor.nsys.LoQueVale;
import com.reparaciones.servidor.nsys.MovilNsys;
import com.reparaciones.servidor.nsys.NsysDAO;
import com.reparaciones.servidor.nsys.TrabajoAbierto;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class RecomendadorTest {

    private static final AceptaSku ACEPTA = new AceptaSku(List.of(
            AceptaSkuTest.producto("Azul", "A", "i13128AzulA", "i13128AzulA", null, null),
            AceptaSkuTest.producto("Azul", "MsA", "i13128AzulMs", "i13128AzulA", null, null),
            AceptaSkuTest.producto("Azul", "B", "i13128AzulB", "i13128AzulB", null, null)));
    private static final Aceptacion AMAZON_A = ACEPTA.para(Canal.AMAZON, "i13128AzulA");
    private static final Aceptacion WEB_A = ACEPTA.para(Canal.DISTRIBUIDORES, "i13128AzulA");
    private static final Map<String, List<TrabajoAbierto>> SIN_TRABAJOS = Map.of();
    private static final Set<String> NADIE = Set.of();

    private static MovilNsys movil(String imei, String grade, LocalDate recepcion, EstadoMovil estado) {
        return new MovilNsys(imei, "BT-9001", "Apple iPhone 13 128GB Blue", grade, "Available", "CAJA LISTOS",
                "Proveedor A", recepcion, null, "i13128AzulA", "13", "13", "128GB", "Azul", grade, false,
                "Listos", "Listos", estado, null, null);
    }

    private static MovilNsys movil(String imei, String grade, LocalDate recepcion) {
        return movil(imei, grade, recepcion, EstadoMovil.STOCK);
    }

    /** Lo que vale es lo que dice NSYS, con el SKU según el grado. */
    private static LoQueVale.Valor vale(MovilNsys m) {
        String sku = switch (m.grade()) {
            case "A" -> "i13128AzulA";
            case "MsA" -> "i13128AzulMs";
            case "B" -> "i13128AzulB";
            default -> null;
        };
        return new LoQueVale.Valor("Azul", m.grade(), sku, null, null, null, false);
    }

    private static List<Recomendador.Candidato> de(Aceptacion ac, Canal canal, List<MovilNsys> stock,
                                                    Map<String, List<TrabajoAbierto>> trabajos, Set<String> yaPuestos) {
        return Recomendador.de(ac, canal, stock, RecomendadorTest::vale, trabajos, yaPuestos);
    }

    private static List<String> imeis(List<Recomendador.Candidato> l) {
        return l.stream().map(Recomendador.Candidato::imei).toList();
    }

    private static final LocalDate D1 = LocalDate.of(2026, 9, 1);
    private static final LocalDate D2 = LocalDate.of(2026, 9, 2);
    private static final LocalDate D3 = LocalDate.of(2026, 9, 3);

    @Test void entraElStockDelMismoModeloCapacidadYEsim() {
        MovilNsys bueno = movil("350000000000001", "A", D1);
        MovilNsys otroModelo = new MovilNsys("350000000000002", "BT-9001", "x", "A", "Available", "CAJA LISTOS", "P", D1, null,
                "i13128AzulA", "13", "13 mini", "128GB", "Azul", "A", false, "Listos", "Listos", EstadoMovil.STOCK, null, null);
        MovilNsys otraCapacidad = new MovilNsys("350000000000003", "BT-9001", "x", "A", "Available", "CAJA LISTOS", "P", D1, null,
                "i13256AzulA", "13", "13", "256GB", "Azul", "A", false, "Listos", "Listos", EstadoMovil.STOCK, null, null);
        MovilNsys esim = new MovilNsys("350000000000004", "BT-9001", "x", "A", "Available", "CAJA LISTOS", "P", D1, null,
                "i13128AzulA", "13", "13", "128GB", "Azul", "A", true, "Listos", "Listos", EstadoMovil.STOCK, null, null);
        List<Recomendador.Candidato> r = de(AMAZON_A, Canal.AMAZON, List.of(bueno, otroModelo, otraCapacidad, esim), SIN_TRABAJOS, NADIE);
        assertEquals(List.of(new Recomendador.Candidato("350000000000001", "i13128AzulA", "Listos", 1)), r);
    }

    @Test void unoYaPuestoEnOtraFilaNoEntra() {
        MovilNsys a = movil("350000000000001", "A", D1);
        MovilNsys b = movil("350000000000002", "A", D2);
        List<Recomendador.Candidato> r = de(AMAZON_A, Canal.AMAZON, List.of(a, b), SIN_TRABAJOS,
                Set.of(NsysDAO.claveCruce("350000000000001")));
        assertEquals(List.of("350000000000002"), imeis(r));
    }

    @Test void unoQueNoEsStockNoEntra() {
        MovilNsys noCuenta = movil("350000000000001", "A", D1, EstadoMovil.NO_CUENTA);
        MovilNsys dePedido = movil("350000000000002", "A", D1, EstadoMovil.DE_PEDIDO);
        MovilNsys bueno = movil("350000000000003", "A", D2);
        assertEquals(List.of("350000000000003"),
                imeis(de(AMAZON_A, Canal.AMAZON, List.of(noCuenta, dePedido, bueno), SIN_TRABAJOS, NADIE)));
    }

    @Test void losGradosNoVendiblesYElIdEnAmazonNoEntran() {
        MovilNsys f = movil("350000000000001", "F", D1);
        MovilNsys fd = movil("350000000000002", "FD", D1);
        MovilNsys rma = movil("350000000000003", "RMA", D1);
        MovilNsys id = movil("350000000000004", "ID", D1);
        assertEquals(List.of(), de(AMAZON_A, Canal.AMAZON, List.of(f, fd, rma, id), SIN_TRABAJOS, NADIE));
        // fuera de Amazon un ID entra (hay que transformarlo), pero F, FD y RMA no
        assertEquals(List.of("350000000000004"), imeis(de(WEB_A, Canal.DISTRIBUIDORES, List.of(f, fd, rma, id), SIN_TRABAJOS, NADIE)));
    }

    @Test void losNivelesSegunElEstadoDeLaUnidad() {
        MovilNsys igual = movil("350000000000001", "A", D3);
        MovilNsys conAviso = movil("350000000000002", "MsA", D3);
        MovilNsys enTaller = movil("350000000000003", "A", D3);
        MovilNsys transformar = movil("350000000000004", "B", D3);
        MovilNsys cSinReparar = movil("350000000000005", "C", D3);
        TrabajoAbierto glass = new TrabajoAbierto("GLASS", "Técnico 1", LocalDateTime.of(2026, 11, 1, 9, 0), null);
        Map<String, List<TrabajoAbierto>> trabajos = Map.of(NsysDAO.claveCruce("350000000000003"), List.of(glass));
        List<Recomendador.Candidato> r = de(WEB_A, Canal.DISTRIBUIDORES,
                List.of(transformar, cSinReparar, enTaller, conAviso, igual), trabajos, NADIE);
        assertEquals(List.of("350000000000001", "350000000000002", "350000000000003", "350000000000004", "350000000000005"), imeis(r));
        assertEquals(List.of(1, 1, 2, 3, 3), r.stream().map(Recomendador.Candidato::nivel).toList());
    }

    @Test void unoEnElTallerSinSkuAceptadoNoEntra() {
        // un B en el taller: su SKU (i13128AzulB) no lo acepta un pedido de A en la web
        MovilNsys enTaller = movil("350000000000001", "B", D1);
        TrabajoAbierto glass = new TrabajoAbierto("GLASS", "Técnico 1", LocalDateTime.of(2026, 11, 1, 9, 0), null);
        Map<String, List<TrabajoAbierto>> trabajos = Map.of(NsysDAO.claveCruce("350000000000001"), List.of(glass));
        assertEquals(List.of(), de(WEB_A, Canal.DISTRIBUIDORES, List.of(enTaller), trabajos, NADIE));
    }

    @Test void ordenPorNivelLuegoRecepcionAntiguaConNulosAlFinalYLuegoImei() {
        MovilNsys nuevo = movil("350000000000001", "A", D3);
        MovilNsys viejo = movil("350000000000002", "A", D1);
        MovilNsys sinFecha = movil("350000000000003", "A", null);
        MovilNsys empateB = movil("350000000000005", "A", D2);
        MovilNsys empateA = movil("350000000000004", "A", D2);
        MovilNsys nivel3 = movil("350000000000000", "B", D1);
        List<Recomendador.Candidato> r = de(WEB_A, Canal.DISTRIBUIDORES,
                List.of(nivel3, sinFecha, nuevo, empateB, viejo, empateA), SIN_TRABAJOS, NADIE);
        assertEquals(List.of("350000000000002", "350000000000004", "350000000000005", "350000000000001", "350000000000003"),
                imeis(r));
    }

    @Test void comoMuchoCincoYElSexto_queda_fuera() {
        List<MovilNsys> stock = new java.util.ArrayList<>();
        for (int i = 1; i <= 7; i++) stock.add(movil("35000000000000" + i, "A", LocalDate.of(2026, 9, i)));
        List<Recomendador.Candidato> r = de(AMAZON_A, Canal.AMAZON, stock, SIN_TRABAJOS, NADIE);
        assertEquals(Recomendador.TOPE, r.size());
        assertEquals("350000000000001", r.get(0).imei());
        assertEquals("350000000000005", r.get(4).imei());
    }

    @Test void sinAceptacionNoHayCandidatos() {
        assertEquals(List.of(), de(null, Canal.AMAZON, List.of(movil("350000000000001", "A", D1)), SIN_TRABAJOS, NADIE));
    }
}
