package com.reparaciones.servidor.security;

import jakarta.servlet.FilterChain;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import java.util.ArrayList;
import java.util.List;

/**
 * La clave de maquina de GET /api/pedidos/vendidos (spec «lista de IMEI vendidos para el bot» §4). Es el unico sitio que la mira:
 * actua solo sobre esa ruta exacta y no mira ningun otro; en ella la unica credencial valida es la clave (JwtAuthFilter se
 * salta esa ruta, asi que un token de usuario no cuenta).
 *
 * <ul>
 *   <li>Ajuste con una o varias claves separadas por comas; las de menos de 32 caracteres se ignoran (aviso al arrancar con cuantas).
 *       Sin ninguna valida: 404 para todos (ruta apagada).</li>
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

    private final List<byte[]> claves;

    /** Largo minimo de cada clave (caracteres, ya recortada): las de menos se ignoran; si no queda ninguna, la ruta queda apagada. */
    static final int LARGO_MINIMO = 32;

    private static final Logger LOG = LoggerFactory.getLogger(ClaveVendidosFilter.class);

    /** Una o varias claves separadas por comas (una por consumidor); todas dan el mismo permiso. */
    public ClaveVendidosFilter(String ajuste) {
        List<byte[]> validas = new ArrayList<>();
        int ignoradas = 0;
        if (ajuste != null) {
            for (String trozo : ajuste.split(",")) {
                String limpia = trozo.strip();
                if (limpia.isEmpty()) continue;
                if (limpia.length() < LARGO_MINIMO) ignoradas++;
                else validas.add(limpia.getBytes(StandardCharsets.UTF_8));
            }
        }
        // Ni las claves ni su largo van al registro: solo cuantas se han ignorado.
        if (ignoradas > 0)
            LOG.warn("Claves de la ruta de vendidos demasiado cortas, ignoradas: {}{}", ignoradas,
                    validas.isEmpty() ? " (no queda ninguna valida: la ruta queda apagada)" : "");
        this.claves = List.copyOf(validas);
    }

    /** Recorre TODAS las claves, sin cortar en la que acierta, para no filtrar por tiempo cual fue. */
    private boolean valida(String recibida) {
        byte[] bytes = recibida.strip().getBytes(StandardCharsets.UTF_8);
        boolean ok = false;
        for (byte[] clave : claves) ok |= MessageDigest.isEqual(clave, bytes);
        return ok;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !RUTA.equals(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (claves.isEmpty()) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        String recibida = request.getHeader(CABECERA);
        if (recibida == null || !valida(recibida)) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Clave no válida");
            return;
        }
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "bot-vendidos", null, List.of(new SimpleGrantedAuthority("ROLE_" + ROL))));
        chain.doFilter(request, response);
    }
}
