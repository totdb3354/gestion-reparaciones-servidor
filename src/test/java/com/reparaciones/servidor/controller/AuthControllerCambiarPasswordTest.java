package com.reparaciones.servidor.controller;

import com.reparaciones.servidor.dao.LogDAO;
import com.reparaciones.servidor.dao.UsuarioDAO;
import com.reparaciones.servidor.security.JwtUtil;
import com.reparaciones.servidor.security.UsuarioPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

/** PATCH /api/auth/cambiar-password (spec 6 §4.4): 422 "Rellena todos los campos." y 422 de longitud antes de tocar
 *  la BD (sustituyen al 400 sin cuerpo), el 422 "Contraseña actual incorrecta." de siempre y 204 con log. */
class AuthControllerCambiarPasswordTest {

    private final UsuarioDAO usuarioDao = mock(UsuarioDAO.class);
    private final LogDAO logDao = mock(LogDAO.class);
    private final AuthController ctl = new AuthController(mock(AuthenticationManager.class), mock(JwtUtil.class),
            logDao, usuarioDao);
    private final UsuarioPrincipal usuario = new UsuarioPrincipal(8, "usuario-a", "", "TECNICO", 4);

    private static AuthController.CambiarPasswordRequest cambio(String actual, String nueva) {
        return new AuthController.CambiarPasswordRequest(actual, nueva);
    }

    /** Un 422 de validación no llega al DAO ni registra log. */
    private String falla422(AuthController.CambiarPasswordRequest req) {
        ResponseStatusException e = assertThrows(ResponseStatusException.class, () -> ctl.cambiarPassword(usuario, req));
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, e.getStatusCode());
        verifyNoInteractions(usuarioDao, logDao);
        return e.getReason();
    }

    @Test void cambioValidoEs204YRegistraLog() {
        ResponseEntity<?> resp = ctl.cambiarPassword(usuario, cambio("secreta1", "nueva123"));
        assertEquals(204, resp.getStatusCode().value());
        verify(usuarioDao).cambiarPassword(8, "secreta1", "nueva123");
        verify(logDao).insertar(8, "CAMBIAR_PASSWORD", "");
    }

    @Test void camposVaciosOAusentesSon422() {
        assertEquals("Rellena todos los campos.", falla422(cambio("", "nueva123")));
        assertEquals("Rellena todos los campos.", falla422(cambio("secreta1", "")));
        assertEquals("Rellena todos los campos.", falla422(cambio(null, "nueva123")));
        assertEquals("Rellena todos los campos.", falla422(cambio("secreta1", null)));
    }

    @Test void nuevaCortaEs422EnVezDe400() {
        assertEquals("La contraseña debe tener al menos 6 caracteres.", falla422(cambio("secreta1", "12345")));
    }

    /** Parando en la primera: con la actual vacía y la nueva corta sale el de campos. */
    @Test void elOrdenEsCamposYDespuesLongitud() {
        assertEquals("Rellena todos los campos.", falla422(cambio("", "123")));
    }

    /** Sin trim (calco): seis espacios son una contraseña de 6 caracteres. */
    @Test void losEspaciosCuentanComoCaracteres() {
        ResponseEntity<?> resp = ctl.cambiarPassword(usuario, cambio("secreta1", "      "));
        assertEquals(204, resp.getStatusCode().value());
        verify(usuarioDao).cambiarPassword(8, "secreta1", "      ");
    }

    @Test void actualIncorrectaSigueSiendo422ConSuTextoYSinLog() {
        doThrow(new IllegalArgumentException("Contraseña actual incorrecta."))
                .when(usuarioDao).cambiarPassword(8, "otra-cosa", "nueva123");
        ResponseEntity<?> resp = ctl.cambiarPassword(usuario, cambio("otra-cosa", "nueva123"));
        assertEquals(422, resp.getStatusCode().value());
        assertEquals(Map.of("message", "Contraseña actual incorrecta."), resp.getBody());
        verifyNoInteractions(logDao);
    }
}
