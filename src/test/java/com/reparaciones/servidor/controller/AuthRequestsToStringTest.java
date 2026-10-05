package com.reparaciones.servidor.controller;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** La contrasena no debe salir en el toString() de las peticiones (Spring MVC puede registrarlas en DEBUG). */
class AuthRequestsToStringTest {

    private static final String SECRETA = "tortuga-violeta-47";

    @Test void loginEnmascaraLaPasswordPeroMuestraElUsuario() {
        String s = new AuthController.LoginRequest("ana", SECRETA).toString();
        assertFalse(s.contains(SECRETA));
        assertTrue(s.contains("ana"));
    }

    @Test void cambiarPasswordEnmascaraLasDos() {
        String s = new AuthController.CambiarPasswordRequest(SECRETA + "1", SECRETA + "2").toString();
        assertFalse(s.contains(SECRETA));
        assertEquals("CambiarPasswordRequest[passwordActual=***, passwordNueva=***]", s);
    }

    @Test void evaluarPasswordEnmascaraLaPassword() {
        assertFalse(new AuthController.EvaluarPasswordRequest(SECRETA).toString().contains(SECRETA));
    }
}
