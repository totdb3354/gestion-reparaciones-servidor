package com.reparaciones.servidor.controller;

import com.reparaciones.servidor.dao.LogDAO;
import com.reparaciones.servidor.dao.UsuarioDAO;
import com.reparaciones.servidor.security.JwtUtil;
import com.reparaciones.servidor.security.UsuarioPrincipal;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthenticationManager authManager;
    private final JwtUtil               jwtUtil;
    private final LogDAO                logDao;
    private final UsuarioDAO            usuarioDao;

    public AuthController(AuthenticationManager authManager, JwtUtil jwtUtil,
                          LogDAO logDao, UsuarioDAO usuarioDao) {
        this.authManager = authManager;
        this.jwtUtil     = jwtUtil;
        this.logDao      = logDao;
        this.usuarioDao  = usuarioDao;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest req) {
        try {
            var auth = authManager.authenticate(
                    new UsernamePasswordAuthenticationToken(req.usuario(), req.password()));
            var principal = (UsuarioPrincipal) auth.getPrincipal();
            String token  = jwtUtil.generateToken(principal);

            logDao.insertar(principal.getIdUsu(), "LOGIN", "");

            Map<String, Object> resp = new HashMap<>();
            resp.put("idUsu",         principal.getIdUsu());
            resp.put("nombreUsuario", principal.getUsername());
            resp.put("rol",           principal.getRol());
            resp.put("idTec",         principal.getIdTec());
            resp.put("token",         token);
            return ResponseEntity.ok(resp);
        } catch (BadCredentialsException e) {
            return ResponseEntity.status(401).build();
        }
    }

    @PatchMapping("/cambiar-password")
    public ResponseEntity<?> cambiarPassword(
            @AuthenticationPrincipal UsuarioPrincipal principal,
            @RequestBody CambiarPasswordRequest req) {
        try {
            usuarioDao.cambiarPassword(principal.getIdUsu(), req.passwordActual(), req.passwordNueva());
            logDao.insertar(principal.getIdUsu(), "CAMBIAR_PASSWORD", "");
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(401).build();
        }
    }

    private record LoginRequest(String usuario, String password) {}
    private record CambiarPasswordRequest(String passwordActual, String passwordNueva) {}
}
