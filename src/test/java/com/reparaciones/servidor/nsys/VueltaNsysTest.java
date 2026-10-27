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
        assertEquals(Optional.of(VueltaNsys.EN_EMPRESA), VueltaNsys.de(movil(IMEI, "Azul", "B"), List.of()));
    }

    @Test void unVendidoQueElTallerTrabajoDespuesHaVueltoDesdeSuPrimerTrabajoPosterior() {
        MovilNsys m = vendido(IMEI, "Azul", "n/a", LocalDate.of(2026, 9, 18));
        assertEquals(Optional.of(new VueltaNsys(LocalDate.of(2026, 9, 18), null, OCT22)),
                VueltaNsys.de(m, List.of(OCT22.plusDays(2), LocalDateTime.of(2026, 9, 1, 10, 0), OCT22)));
    }

    @Test void elMismoDiaDeLaVentaNoCuentaNiLoDeAntes() {
        MovilNsys m = vendido(IMEI, "Azul", "n/a", LocalDate.of(2026, 10, 26));
        assertEquals(Optional.empty(), VueltaNsys.de(m, List.of(LocalDateTime.of(2026, 10, 26, 11, 15),
                LocalDateTime.of(2026, 10, 23, 9, 0))));
    }

    @Test void unaBajaCuentaDesdeElDiaEnQueNsysCambioSuFila() {
        MovilNsys m = deBaja(IMEI, "Azul", "B", LocalDateTime.of(2026, 10, 20, 9, 31));
        assertEquals(Optional.of(new VueltaNsys(null, LocalDate.of(2026, 10, 20), OCT22)), VueltaNsys.de(m, List.of(OCT22)));
        assertEquals(Optional.empty(), VueltaNsys.de(m, List.of(LocalDateTime.of(2026, 10, 20, 18, 0))));
    }

    @Test void unVendidoSinFechaDeVentaNoSale() {
        assertEquals(Optional.empty(), VueltaNsys.de(vendido(IMEI, "Azul", "B", null), List.of(OCT22)));
    }

    @Test void lasAsignacionesSeJuntanPorClaveDeCruce() {
        Map<String, List<LocalDateTime>> mapa = VueltaNsys.asignacionesPorImei(List.of(
                new NsysDAO.TrabajoFecha(IMEI, "A20261022_1", OCT22), new NsysDAO.TrabajoFecha(" f2lxabc123 ", "R20261022_2", OCT22),
                new NsysDAO.TrabajoFecha(IMEI, "R20261022_3", OCT22.plusHours(1))));
        assertEquals(List.of(OCT22, OCT22.plusHours(1)), mapa.get(IMEI));
        assertEquals(List.of(OCT22), mapa.get("F2LXABC123"));
    }
}
