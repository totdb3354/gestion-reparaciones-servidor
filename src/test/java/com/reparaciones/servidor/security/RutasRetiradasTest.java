package com.reparaciones.servidor.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** La regla de coincidencia de las rutas de primera generación (spec sp7b §4.1). */
class RutasRetiradasTest {

    @Test void coincideLaRutaExactaYElMetodoExacto() {
        assertTrue(RutasRetiradas.coincide("GET", "/api/componentes"));
        assertFalse(RutasRetiradas.coincide("POST", "/api/componentes/agrupados"));
    }

    @Test void noCoincideOtroMetodoDeLaMismaRuta() {
        assertFalse(RutasRetiradas.coincide("PUT", "/api/componentes"));
    }

    @Test void coincideConVariableDeRuta() {
        assertTrue(RutasRetiradas.coincide("GET", "/api/telefonos/355400000000111/exists"));
    }

    @Test void noCoincideUnaRutaViva() {
        assertFalse(RutasRetiradas.coincide("GET", "/api/componentes/agrupados"));
        assertFalse(RutasRetiradas.coincide("GET", "/api/reparaciones/historial"));
        assertFalse(RutasRetiradas.coincide("DELETE", "/api/telefonos/355400000000111"));
    }
}
