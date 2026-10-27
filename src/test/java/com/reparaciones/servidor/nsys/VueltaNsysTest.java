package com.reparaciones.servidor.nsys;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.reparaciones.servidor.nsys.TallerDePrueba.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** Si un móvil sale en «Revisar a mano», Color y Grado (spec 0.9.22 pieza 4 §6): en la empresa, sí; vendido o de baja, solo si
 *  el taller lo ha trabajado un día posterior a la venta o a la baja. */
class VueltaNsysTest {

    private static final String IMEI = "350000000000001";
    private static final LocalDateTime OCT22 = LocalDateTime.of(2026, 10, 22, 16, 47);

    @Test void unoEnLaEmpresaSaleTalCual() {
        assertEquals(Optional.of(VueltaNsys.EN_EMPRESA), VueltaNsys.de(movil(IMEI, "Azul", "B"), List.<NsysDAO.TrabajoFecha>of()));
    }

    @Test void unVendidoQueElTallerTrabajoDespuesHaVueltoDesdeSuPrimerTrabajoPosterior() {
        MovilNsys m = vendido(IMEI, "Azul", "n/a", LocalDate.of(2026, 9, 18));
        assertEquals(Optional.of(new VueltaNsys(LocalDate.of(2026, 9, 18), null, OCT22)),
                VueltaNsys.de(m, List.of(r(OCT22.plusDays(2)), r(LocalDateTime.of(2026, 9, 1, 10, 0)), r(OCT22))));
    }

    @Test void elMismoDiaDeLaVentaNoCuentaNiLoDeAntes() {
        MovilNsys m = vendido(IMEI, "Azul", "n/a", LocalDate.of(2026, 10, 26));
        assertEquals(Optional.empty(), VueltaNsys.de(m, List.of(r(LocalDateTime.of(2026, 10, 26, 11, 15)),
                r(LocalDateTime.of(2026, 10, 23, 9, 0)))));
    }

    @Test void unaBajaCuentaDesdeElDiaEnQueNsysCambioSuFila() {
        MovilNsys m = deBaja(IMEI, "Azul", "B", LocalDateTime.of(2026, 10, 20, 9, 31));
        assertEquals(Optional.of(new VueltaNsys(null, LocalDate.of(2026, 10, 20), OCT22)), VueltaNsys.de(m, List.of(r(OCT22))));
        assertEquals(Optional.empty(), VueltaNsys.de(m, List.of(r(LocalDateTime.of(2026, 10, 20, 18, 0)))));
    }

    @Test void unVendidoSinFechaDeVentaNoSale() {
        assertEquals(Optional.empty(), VueltaNsys.de(vendido(IMEI, "Azul", "B", null), List.of(r(OCT22))));
    }

    private static NsysDAO.TrabajoFecha r(LocalDateTime asignado) {
        return new NsysDAO.TrabajoFecha(IMEI, "R20261022_9", asignado);
    }

    private static NsysDAO.TrabajoFecha a(LocalDateTime asignado, LocalDateTime terminado) {
        return new NsysDAO.TrabajoFecha(IMEI, "A20261020_1", asignado, terminado);
    }

    private static NsysDAO.TrabajoFecha r(LocalDateTime asignado, String idRep) {
        return new NsysDAO.TrabajoFecha(IMEI, idRep, asignado, null);
    }

    private static final LocalDate D = LocalDate.of(2026, 10, 20);
    private static final LocalDateTime D_1 = LocalDateTime.of(2026, 10, 19, 10, 0);

    private static Optional<VueltaNsys> vendidoD(NsysDAO.TrabajoFecha... t) {
        return VueltaNsys.de(vendido(IMEI, "Azul", "n/a", D), List.of(t));
    }

    @Test void laRQueCierraUnaAsignacionAbiertaElDiaDeLaVentaNoCuenta() {
        LocalDateTime d1 = LocalDateTime.of(2026, 10, 21, 12, 0);
        assertEquals(Optional.empty(), vendidoD(a(D_1, d1), r(d1, "R20261021_5")));
        assertEquals(Optional.empty(), vendidoD(a(D_1, d1), r(d1.minusMinutes(3), "R20261021_5")));
    }

    @Test void unaAsignacionNuevaPosteriorHaceQueHayaVuelto() {
        LocalDateTime d1 = LocalDateTime.of(2026, 10, 21, 12, 0);
        LocalDateTime d5 = LocalDateTime.of(2026, 10, 25, 9, 30);
        assertEquals(Optional.of(new VueltaNsys(D, null, d5)),
                vendidoD(a(D_1, d1), r(d1, "R20261021_5"), a(d5, null)));
    }

    @Test void unaRDirectaSinAsignacionAbiertaElDiaDeLaVentaCuenta() {
        LocalDateTime d3 = LocalDateTime.of(2026, 10, 23, 9, 0);
        assertEquals(Optional.of(new VueltaNsys(D, null, d3)), vendidoD(r(d3, "R20261023_2")));
        // una asignacion ya cerrada antes de la venta no la ampara
        assertEquals(Optional.of(new VueltaNsys(D, null, d3)),
                vendidoD(a(D_1, LocalDateTime.of(2026, 10, 19, 18, 0)), r(d3, "R20261023_2")));
    }

    @Test void laRConLaAsignacionAunSinTerminarNoCuenta() {
        assertEquals(Optional.empty(), vendidoD(a(D_1, null), r(LocalDateTime.of(2026, 10, 22, 11, 0), "R20261022_4")));
    }

    @Test void unaAsignacionDelMismoDiaDeLaVentaNoCuenta() {
        assertEquals(Optional.empty(), vendidoD(a(LocalDateTime.of(2026, 10, 20, 15, 0), null)));
        assertEquals(Optional.empty(), vendidoD(a(LocalDateTime.of(2026, 10, 20, 15, 0), LocalDateTime.of(2026, 10, 20, 17, 0))));
    }

    @Test void unaRDespuesDeCerrarseLaAsignacionAbiertaSiCuenta() {
        LocalDateTime d1 = LocalDateTime.of(2026, 10, 21, 12, 0);
        LocalDateTime tarde = LocalDateTime.of(2026, 10, 28, 8, 0);
        assertEquals(Optional.of(new VueltaNsys(D, null, tarde)), vendidoD(a(D_1, d1), r(tarde, "R20261028_1")));
    }

    @Test void unaBajaUsaLaMismaLogicaConElDiaDeCambiado() {
        MovilNsys m = deBaja(IMEI, "Azul", "B", LocalDateTime.of(2026, 10, 20, 9, 31));
        LocalDateTime d1 = LocalDateTime.of(2026, 10, 21, 12, 0);
        assertEquals(Optional.empty(), VueltaNsys.de(m, List.of(a(D_1, d1), r(d1, "R20261021_5"))));
        LocalDateTime d5 = LocalDateTime.of(2026, 10, 25, 9, 30);
        assertEquals(Optional.of(new VueltaNsys(null, D, d5)), VueltaNsys.de(m, List.of(a(D_1, d1), r(d1, "R20261021_5"), a(d5, null))));
    }

    @Test void losTrabajosSeJuntanPorClaveDeCruce() {
        NsysDAO.TrabajoFecha a1 = new NsysDAO.TrabajoFecha(IMEI, "A20261022_1", OCT22);
        NsysDAO.TrabajoFecha r2 = new NsysDAO.TrabajoFecha(" f2lxabc123 ", "R20261022_2", OCT22);
        NsysDAO.TrabajoFecha r3 = new NsysDAO.TrabajoFecha(IMEI, "R20261022_3", OCT22.plusHours(1));
        Map<String, List<NsysDAO.TrabajoFecha>> mapa = VueltaNsys.trabajosPorImei(List.of(a1, r2, r3));
        assertEquals(List.of(a1, r3), mapa.get(IMEI));
        assertEquals(List.of(r2), mapa.get("F2LXABC123"));
    }
}
