package com.reparaciones.servidor.security;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;

/** Regla de las escrituras del formulario (spec web-formulario 2026-09-19 §5.1). */
class PropiedadAsignacionTest {

    private static final UsuarioPrincipal TECNICO = new UsuarioPrincipal(8, "tecnico_n", "x", "TECNICO", 4);
    private static final UsuarioPrincipal SUPER   = new UsuarioPrincipal(7, "tecnico_f", "x", "SUPERTECNICO", 3);
    private static final UsuarioPrincipal ADMIN   = new UsuarioPrincipal(1, "admin", "x", "ADMIN", null);

    @Test void duenoRecibeSuIdTec() {
        assertEquals(4, PropiedadAsignacion.tecnicoEfectivo(TECNICO, 4));
        assertEquals(3, PropiedadAsignacion.tecnicoEfectivo(SUPER, 3));
    }

    @Test void asignacionAjenaEs403ConMensaje() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> PropiedadAsignacion.tecnicoEfectivo(TECNICO, 9));
        assertEquals(403, ex.getStatusCode().value());
        assertEquals(PropiedadAsignacion.MSG_NO_ES_TUYA, ex.getReason());
        assertEquals("Solo puedes trabajar sobre tus propias asignaciones", PropiedadAsignacion.MSG_NO_ES_TUYA);
    }

    @Test void tokenSinTecnicoEs403() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> PropiedadAsignacion.tecnicoEfectivo(ADMIN, 4));
        assertEquals(403, ex.getStatusCode().value());
        assertEquals(PropiedadAsignacion.MSG_NO_ES_TUYA, ex.getReason());
        assertEquals(403, assertThrows(ResponseStatusException.class,
                () -> PropiedadAsignacion.tecnicoEfectivo(ADMIN, null)).getStatusCode().value());
    }

    @Test void supertecnicoSobreAsignacionAjenaTambienEs403() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> PropiedadAsignacion.tecnicoEfectivo(SUPER, 4));
        assertEquals(403, ex.getStatusCode().value());
        assertEquals(PropiedadAsignacion.MSG_NO_ES_TUYA, ex.getReason());
    }

    @Test void asignacionInexistenteNoLanza() {
        // Dueño nulo = la asignación no existe: la regla deja pasar y el DAO responde su 409.
        assertEquals(4, PropiedadAsignacion.tecnicoEfectivo(TECNICO, null));
        assertEquals(3, PropiedadAsignacion.tecnicoEfectivo(SUPER, null));
    }

    @Test void exigirSupertecnicoDejaPasarSoloAlSupertecnico() {
        assertDoesNotThrow(() -> PropiedadAsignacion.exigirSupertecnico(SUPER));
        for (UsuarioPrincipal otro : new UsuarioPrincipal[] { TECNICO, ADMIN }) {
            ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                    () -> PropiedadAsignacion.exigirSupertecnico(otro));
            assertEquals(403, ex.getStatusCode().value());
            assertEquals(PropiedadAsignacion.MSG_SOLO_SUPERTECNICO, ex.getReason());
        }
        assertEquals("Solo el supertécnico puede corregir una reparación ya hecha",
                PropiedadAsignacion.MSG_SOLO_SUPERTECNICO);
    }
}
