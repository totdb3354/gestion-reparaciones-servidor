package com.reparaciones.servidor.controller;

import com.reparaciones.servidor.dao.LogDAO;
import com.reparaciones.servidor.dao.UsuarioDAO;
import com.reparaciones.servidor.model.LoginResponse;
import com.reparaciones.servidor.security.IntentosFallidos;
import com.reparaciones.servidor.security.JwtUtil;
import com.reparaciones.servidor.security.UsuarioPrincipal;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthenticationManager authManager;
    private final JwtUtil               jwtUtil;
    private final LogDAO                logDao;
    private final UsuarioDAO            usuarioDao;
    private final IntentosFallidos      intentos;

    public AuthController(AuthenticationManager authManager, JwtUtil jwtUtil,
                          LogDAO logDao, UsuarioDAO usuarioDao, IntentosFallidos intentos) {
        this.authManager = authManager;
        this.jwtUtil     = jwtUtil;
        this.logDao      = logDao;
        this.usuarioDao  = usuarioDao;
        this.intentos    = intentos;
    }

    @PostMapping("/login")
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
            content = @io.swagger.v3.oas.annotations.media.Content(
                schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = LoginResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", content = @io.swagger.v3.oas.annotations.media.Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "429", content = @io.swagger.v3.oas.annotations.media.Content)
    })
    public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest req,
                                               jakarta.servlet.http.HttpServletRequest http) {
        intentos.comprobar(req.usuario());
        try {
            var auth = authManager.authenticate(
                    new UsernamePasswordAuthenticationToken(req.usuario(), req.password()));
            var principal = (UsuarioPrincipal) auth.getPrincipal();
            String token  = jwtUtil.generateToken(principal);

            intentos.limpiar(req.usuario());
            logDao.insertar(principal.getIdUsu(), "LOGIN", "");

            return ResponseEntity.ok(new LoginResponse(
                    principal.getIdUsu(), principal.getUsername(), principal.getRol(),
                    principal.getIdTec(), token, usuarioDao.tienePasswordTemporal(principal.getIdUsu())));
        } catch (BadCredentialsException e) {
            // Deja constancia del intento con el nombre tal cual se escribió, aunque no exista ese usuario
            // (spec sp7b §5.2).
            int fallos = intentos.registrarFallo(req.usuario());
            logDao.insertarIntento(req.usuario(), "LOGIN_FALLIDO",
                    "INTENTOS: " + fallos + origenDe(http));
            return ResponseEntity.status(401).build();
        }
    }

    @PatchMapping("/cambiar-password")
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", content = @io.swagger.v3.oas.annotations.media.Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "422", content = @io.swagger.v3.oas.annotations.media.Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "429", content = @io.swagger.v3.oas.annotations.media.Content)
    })
    public ResponseEntity<?> cambiarPassword(
            @AuthenticationPrincipal UsuarioPrincipal principal,
            @RequestBody CambiarPasswordRequest req) {
        intentos.comprobar(principal.getUsername());
        // 422 con los textos del cliente antes de BCrypt (spec 6 §4.4): sustituye al 400 sin cuerpo y evita el
        // "rawPassword cannot be null" de matches(null, …). Lanza ResponseStatusException: el catch de abajo no la ve.
        ValidacionUsuarios.validarCambioPassword(req.passwordActual(), req.passwordNueva());
        try {
            usuarioDao.cambiarPassword(principal.getIdUsu(), req.passwordActual(), req.passwordNueva());
            intentos.limpiar(principal.getUsername());
            logDao.insertar(principal.getIdUsu(), "CAMBIAR_PASSWORD", "");
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            int fallos = intentos.registrarFallo(principal.getUsername());
            logDao.insertar(principal.getIdUsu(), "CAMBIAR_PASSWORD_FALLIDO", "INTENTOS: " + fallos);
            return ResponseEntity.unprocessableEntity().body(Map.of("message", e.getMessage()));
        }
    }

    /** La dirección que ve la aplicación, para el registro; vacío si no hay petición (tests). */
    private static String origenDe(jakarta.servlet.http.HttpServletRequest http) {
        if (http == null) return "";
        String reenviada = http.getHeader("X-Real-IP");
        if (reenviada == null || reenviada.isBlank()) reenviada = http.getRemoteAddr();
        return reenviada == null || reenviada.isBlank() ? "" : ", ORIGEN: " + reenviada;
    }

    record LoginRequest(String usuario, String password) {}
    record CambiarPasswordRequest(String passwordActual, String passwordNueva) {}
}
