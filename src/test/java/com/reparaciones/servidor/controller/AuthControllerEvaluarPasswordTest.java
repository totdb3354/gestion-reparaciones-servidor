package com.reparaciones.servidor.controller;

import com.reparaciones.servidor.dao.LogDAO;
import com.reparaciones.servidor.dao.UsuarioDAO;
import com.reparaciones.servidor.model.EvaluacionPassword;
import com.reparaciones.servidor.security.*;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationManager;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

/** POST /api/auth/evaluar-password: la misma regla que al guardar, con el usuario y el rol de la sesión, sin escribir. */
class AuthControllerEvaluarPasswordTest {

    private final UsuarioDAO usuarioDao = mock(UsuarioDAO.class);
    private final LogDAO logDao = mock(LogDAO.class);
    private final AuthController ctl = new AuthController(mock(AuthenticationManager.class), mock(JwtUtil.class),
            logDao, usuarioDao, new IntentosFallidos(), mock(EstadoUsuarioService.class),
            new PoliticaPassword((p, w) -> new MedidorFuerza.Medida(3, List.of("Añade otra palabra.")), ""));

    @Test void devuelveLaNotaYSiSeAceptaSegunElRol() {
        var tecnico = new UsuarioPrincipal(8, "usuario-a", "", "TECNICO", 4);
        var admin = new UsuarioPrincipal(1, "admin", "", "ADMIN", null);
        assertEquals(new EvaluacionPassword(3, true, null),
                ctl.evaluarPassword(tecnico, new AuthController.EvaluarPasswordRequest("nueva-larga-123")));
        assertEquals(new EvaluacionPassword(3, false, "La contraseña es poco segura. Añade otra palabra."),
                ctl.evaluarPassword(admin, new AuthController.EvaluarPasswordRequest("nueva-larga-123")));
    }

    @Test void noEscribeNiRegistraNada() {
        ctl.evaluarPassword(new UsuarioPrincipal(8, "usuario-a", "", "TECNICO", 4),
                new AuthController.EvaluarPasswordRequest("nueva-larga-123"));
        verifyNoInteractions(logDao);
        verify(usuarioDao, never()).cambiarPassword(anyInt(), any(), any());
    }

    @Test void vaciaDevuelveRellenar() {
        assertEquals(new EvaluacionPassword(0, false, "Rellena todos los campos."),
                ctl.evaluarPassword(new UsuarioPrincipal(8, "usuario-a", "", "TECNICO", 4),
                        new AuthController.EvaluarPasswordRequest(null)));
    }
}
