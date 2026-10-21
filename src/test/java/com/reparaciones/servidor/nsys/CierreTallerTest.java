package com.reparaciones.servidor.nsys;

import org.junit.jupiter.api.Test;

import static com.reparaciones.servidor.nsys.CierreTaller.Decision.*;
import static com.reparaciones.servidor.nsys.CierreTaller.decidir;
import static com.reparaciones.servidor.nsys.TallerDePrueba.vigente;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** El cierre tras una importación (spec 0.9.21 §2.2): un cambio del taller de B a A100. */
class CierreTallerTest {

    private static final CambioTaller A100 = vigente(1, "350000000000001", DatoTaller.GRADO, "A100", "B");

    @Test void nsysYaTieneLoDelTallerEsPasado() {
        assertEquals(PASADO, decidir(A100, "A100"));
        assertEquals(PASADO, decidir(A100, " A100 "), "sin espacios a los lados");
    }

    @Test void nsysCambiaDespuesAOtraCosaMandaNsys() {
        assertEquals(MANDA_NSYS, decidir(A100, "B-"));
    }

    @Test void nsysComoAntesSigue() {
        assertEquals(SIGUE, decidir(A100, "B"));
    }

    @Test void nsysSinElDatoSigue() {
        assertEquals(SIGUE, decidir(A100, null));
        assertEquals(SIGUE, decidir(A100, "  "));
    }
}
