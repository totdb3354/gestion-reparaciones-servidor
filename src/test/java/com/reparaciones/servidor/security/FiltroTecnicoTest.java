package com.reparaciones.servidor.security;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;

/** Regla del ?tecnico= (spec web-taller 2026-09-16 §5.1). */
class FiltroTecnicoTest {

    private static final UsuarioPrincipal TECNICO = new UsuarioPrincipal(8, "tecnico_n", "x", "TECNICO", 4);
    private static final UsuarioPrincipal SUPER   = new UsuarioPrincipal(7, "tecnico_f", "x", "SUPERTECNICO", 3);
    private static final UsuarioPrincipal ADMIN   = new UsuarioPrincipal(1, "admin", "x", "ADMIN", null);

    @Test void tecnicoSinParametroRecibeLoSuyo() {
        assertEquals(4, FiltroTecnico.efectivo(TECNICO, null));
    }

    @Test void tecnicoPidiendoseASiMismoRecibeLoSuyo() {
        assertEquals(4, FiltroTecnico.efectivo(TECNICO, 4));
    }

    @Test void tecnicoPidiendoAOtroEs403ConMensaje() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> FiltroTecnico.efectivo(TECNICO, 9));
        assertEquals(403, ex.getStatusCode().value());
        assertEquals(FiltroTecnico.MSG_SOLO_PROPIOS, ex.getReason());
    }

    @Test void tecnicoSinIdTecNoRecibeNada() {
        UsuarioPrincipal raro = new UsuarioPrincipal(9, "x", "x", "TECNICO", null);
        assertEquals(403, assertThrows(ResponseStatusException.class,
                () -> FiltroTecnico.efectivo(raro, null)).getStatusCode().value());
    }

    @Test void supertecnicoYAdminMantienenElFiltroLibre() {
        assertNull(FiltroTecnico.efectivo(SUPER, null));
        assertEquals(9, FiltroTecnico.efectivo(SUPER, 9));
        assertNull(FiltroTecnico.efectivo(ADMIN, null));
        assertEquals(9, FiltroTecnico.efectivo(ADMIN, 9));
    }
}
