package com.reparaciones.servidor.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

/**
 * La clave de maquina de GET /api/pedidos/vendidos (spec «lista de IMEI vendidos para el bot» §4). Es el unico sitio que la mira:
 * actua solo sobre esa ruta exacta y no mira ningun otro; en ella la unica credencial valida es la clave (JwtAuthFilter se
 * salta esa ruta, asi que un token de usuario no cuenta).
 *
 * <ul>
 *   <li>Ajuste vacio: 404 para todos (ruta apagada).</li>
 *   <li>Sin cabecera X-API-Key o con otra clave: 401. Comparacion en tiempo constante.</li>
 *   <li>Clave buena: autentica con la unica autoridad {@link #ROL} (que ninguna otra ruta pide).</li>
 * </ul>
 *
 * No es un bean (no se registra solo en el contenedor de servlets): lo construye SecurityConfig y lo pone en la cadena.
 * Nunca escribe en el registro la clave ni la cabecera recibida.
 */
public class ClaveVendidosFilter extends OncePerRequestFilter {

    public static final String RUTA = "/api/pedidos/vendidos";
    public static final String CABECERA = "X-API-Key";
    public static final String ROL = "BOT_VENDIDOS";

    private final byte[] clave;

    public ClaveVendidosFilter(String clave) {
        this.clave = clave == null ? new byte[0] : clave.strip().getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !RUTA.equals(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (clave.length == 0) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        String recibida = request.getHeader(CABECERA);
        if (recibida == null || !MessageDigest.isEqual(clave, recibida.strip().getBytes(StandardCharsets.UTF_8))) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Clave no válida");
            return;
        }
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "bot-vendidos", null, List.of(new SimpleGrantedAuthority("ROLE_" + ROL))));
        chain.doFilter(request, response);
    }
}
