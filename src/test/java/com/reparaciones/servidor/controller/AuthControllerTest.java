package com.reparaciones.servidor.controller;

import com.reparaciones.servidor.dao.LogDAO;
import com.reparaciones.servidor.dao.UsuarioDAO;
import com.reparaciones.servidor.model.LoginResponse;
import com.reparaciones.servidor.security.JwtUtil;
import com.reparaciones.servidor.security.UsuarioPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AuthControllerTest {

    private final AuthenticationManager authManager = mock(AuthenticationManager.class);
    private final JwtUtil jwtUtil = mock(JwtUtil.class);
    private final LogDAO logDao = mock(LogDAO.class);
    private final UsuarioDAO usuarioDao = mock(UsuarioDAO.class);
    private final AuthController ctl = new AuthController(authManager, jwtUtil, logDao, usuarioDao);

    @Test void loginDevuelveRespuestaTipadaConLosCincoCampos() {
        var principal = new UsuarioPrincipal(7, "fati", "x", "SUPERTECNICO", 3);
        var auth = mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn(principal);
        when(authManager.authenticate(any())).thenReturn(auth);
        when(jwtUtil.generateToken(principal)).thenReturn("jwt-123");

        var resp = ctl.login(new AuthController.LoginRequest("fati", "secreta"));

        assertEquals(200, resp.getStatusCode().value());
        var body = (LoginResponse) resp.getBody();
        assertNotNull(body);
        assertEquals(7, body.idUsu());
        assertEquals("fati", body.nombreUsuario());
        assertEquals("SUPERTECNICO", body.rol());
        assertEquals(3, body.idTec());
        assertEquals("jwt-123", body.token());
        verify(logDao).insertar(7, "LOGIN", "");
    }

    @Test void loginConCredencialesMalasDevuelve401SinCuerpo() {
        when(authManager.authenticate(any())).thenThrow(new BadCredentialsException("no"));
        var resp = ctl.login(new AuthController.LoginRequest("x", "y"));
        assertEquals(401, resp.getStatusCode().value());
        assertNull(resp.getBody());
    }
}
