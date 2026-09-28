package com.reparaciones.servidor.controller;

import com.reparaciones.servidor.dao.LogDAO;
import com.reparaciones.servidor.dao.UsuarioDAO;
import com.reparaciones.servidor.security.IntentosFallidos;
import com.reparaciones.servidor.security.JwtUtil;
import com.reparaciones.servidor.security.UsuarioPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Los fallos de contraseña dejan constancia y frenan la cuenta (spec sp7b §5.2 y §5.3). */
class AuthControllerIntentosTest {

    private final AuthenticationManager authManager = mock(AuthenticationManager.class);
    private final JwtUtil jwtUtil = mock(JwtUtil.class);
    private final LogDAO logDao = mock(LogDAO.class);
    private final UsuarioDAO usuarioDao = mock(UsuarioDAO.class);
    private final IntentosFallidos intentos = new IntentosFallidos();
    private final AuthController ctl = new AuthController(authManager, jwtUtil, logDao, usuarioDao, intentos);

    private static AuthController.LoginRequest login(String usuario) {
        return new AuthController.LoginRequest(usuario, "mala");
    }

    @Test void unLoginFallidoSeRegistraConElNombreIntentado() {
        when(authManager.authenticate(any())).thenThrow(new BadCredentialsException("no"));
        var resp = ctl.login(login("ana"), null);
        assertEquals(401, resp.getStatusCode().value());
        verify(logDao).insertarIntento(eq("ana"), eq("LOGIN_FALLIDO"), contains("INTENTOS: 1"));
    }

    @Test void pasadoElUmbralLaCuentaEspera() {
        when(authManager.authenticate(any())).thenThrow(new BadCredentialsException("no"));
        for (int i = 0; i < IntentosFallidos.UMBRAL; i++) ctl.login(login("ana"), null);
        var ex = assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> ctl.login(login("ana"), null));
        assertEquals(429, ex.getStatusCode().value());
    }

    @Test void otraCuentaNoSeVeAfectada() {
        when(authManager.authenticate(any())).thenThrow(new BadCredentialsException("no"));
        for (int i = 0; i < IntentosFallidos.UMBRAL; i++) ctl.login(login("ana"), null);
        var resp = ctl.login(login("bruno"), null);
        assertEquals(401, resp.getStatusCode().value());
    }

    @Test void unaEntradaCorrectaLimpiaElContador() {
        var principal = new UsuarioPrincipal(8, "ana", "", "TECNICO", 4);
        var auth = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                principal, null, principal.getAuthorities());
        when(authManager.authenticate(any())).thenThrow(new BadCredentialsException("no"));
        for (int i = 0; i < IntentosFallidos.UMBRAL - 1; i++) ctl.login(login("ana"), null);
        // doReturn en vez de when(...).thenReturn(...): el mock ya lanzaba BadCredentialsException en cada
        // llamada, y when() invoca el método real del mock para registrar el nuevo stub.
        doReturn(auth).when(authManager).authenticate(any());
        when(jwtUtil.generateToken(any())).thenReturn("un-token");
        ctl.login(login("ana"), null);
        assertEquals(0L, intentos.esperaMs("ana"));
    }
}
