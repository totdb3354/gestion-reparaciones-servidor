package com.reparaciones.servidor.security;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final EstadoUsuarioService estadoUsuario;

    public JwtAuthFilter(JwtUtil jwtUtil, EstadoUsuarioService estadoUsuario) {
        this.jwtUtil       = jwtUtil;
        this.estadoUsuario = estadoUsuario;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            chain.doFilter(request, response);
            return;
        }

        String token = header.substring(7);
        Claims  claims;
        String  username;
        String  rol;
        Integer idUsu;
        Integer idTec;
        try {
            claims   = jwtUtil.parseToken(token);
            username = claims.getSubject();
            rol      = claims.get("rol", String.class);
            idUsu    = claims.get("idUsu", Integer.class);
            idTec    = claims.get("idTec", Integer.class);
        } catch (RuntimeException e) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Token inválido o expirado");
            return;
        }

        // Un token bien firmado pero sin los datos del usuario no identifica a nadie: 401,
        // y sin autoridad de rol (spec sp7b §4.5).
        if (idUsu == null || username == null || rol == null || rol.isBlank()) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Token inválido o expirado");
            return;
        }

        // El token puede ser válido y el usuario ya no: desactivado, borrado o con otro rol. 401 y no 403,
        // porque es lo único que devuelve al usuario a la pantalla de entrada en los dos clientes (spec sp7b D4).
        if (!estadoUsuario.estaOperativo(idUsu)) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Token inválido o expirado");
            return;
        }

        var principal = new UsuarioPrincipal(idUsu, username, "", rol, idTec);
        var auth = new UsernamePasswordAuthenticationToken(
                principal, null,
                List.of(new SimpleGrantedAuthority("ROLE_" + rol)));
        SecurityContextHolder.getContext().setAuthentication(auth);

        chain.doFilter(request, response);
    }
}
