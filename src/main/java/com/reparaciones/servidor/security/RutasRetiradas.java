package com.reparaciones.servidor.security;

import org.springframework.util.AntPathMatcher;

import java.util.List;

/**
 * Rutas de la primera generación de la API: las pantallas actuales no las usan, están sustituidas por otras y
 * ninguno de los dos clientes las llama. Responden 403 y dejan constancia del intento, para poder retirarlas con
 * una prueba real de que nadie las usa (spec sp7b §4.1 y decisión D7). El reparto y la justificación de cada una,
 * en el inventario privado.
 *
 * Al retirar la lista entera, borrar también el interceptor y su registro en WebMvcConfig.
 */
public final class RutasRetiradas {

    public record Ruta(String metodo, String patron) {}

    public static final String MSG_RETIRADA = "Esta operación ya no está disponible.";
    public static final String ACCION_LOG   = "RUTA_RETIRADA";

    /** La rellena la Task 2. */
    public static final List<Ruta> LISTA = List.of(
            new Ruta("GET", "/api/componentes"),
            new Ruta("GET", "/api/telefonos/{imei}/exists")
    );

    private static final AntPathMatcher MATCHER = new AntPathMatcher();

    private RutasRetiradas() {}

    /** @param metodo método HTTP en mayúsculas; @param uri ruta de la petición, sin query string. */
    public static boolean coincide(String metodo, String uri) {
        for (Ruta r : LISTA) {
            if (r.metodo().equals(metodo) && MATCHER.match(r.patron(), uri)) return true;
        }
        return false;
    }
}
