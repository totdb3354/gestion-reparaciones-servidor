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

    public static final List<Ruta> LISTA = List.of(
            // Almacén de primera generación: el stock lo mueve el servidor al guardar reparaciones,
            // y las pantallas usan /api/componentes/agrupados y /gestionados.
            new Ruta("GET",    "/api/componentes"),
            new Ruta("GET",    "/api/componentes/stock-bajo"),
            new Ruta("GET",    "/api/componentes/chasis"),
            new Ruta("GET",    "/api/componentes/evolucion-stock"),
            new Ruta("POST",   "/api/componentes"),
            new Ruta("PATCH",  "/api/componentes/{idCom}/stock"),
            new Ruta("DELETE", "/api/componentes/{idCom}"),
            new Ruta("GET",    "/api/compras/en-camino"),
            // Piezas de una reparación: las pantallas usan /api/reparaciones/{idAsignacion}/filas
            // y el endpoint de incidencias.
            new Ruta("GET",    "/api/reparacion-componentes/{idRep}"),
            new Ruta("POST",   "/api/reparacion-componentes"),
            new Ruta("DELETE", "/api/reparacion-componentes/{idRep}/{idCom:[0-9]+}"),
            new Ruta("PATCH",  "/api/reparacion-componentes/{idRep}/incidencia"),
            // Reparaciones: lo hacen hoy los endpoints del formulario y del historial.
            new Ruta("GET",    "/api/reparaciones"),
            new Ruta("GET",    "/api/reparaciones/imei/{imei}/count"),
            new Ruta("GET",    "/api/reparaciones/historial/imei/{imei}"),
            new Ruta("GET",    "/api/reparaciones/asignaciones/imei/{imei}"),
            new Ruta("GET",    "/api/reparaciones/imei/{imei}/tecnicos-asignados"),
            new Ruta("GET",    "/api/reparaciones/estadisticas"),
            new Ruta("POST",   "/api/reparaciones"),
            new Ruta("PATCH",  "/api/reparaciones/{idRep}/completar"),
            // Técnicos: el alta y el borrado reales van por /api/usuarios/tecnicos.
            new Ruta("POST",   "/api/tecnicos"),
            new Ruta("DELETE", "/api/tecnicos/{idTec}"),
            // Teléfonos: las pantallas usan las consultas concretas del formulario.
            new Ruta("GET",    "/api/telefonos"),
            new Ruta("GET",    "/api/telefonos/{imei}/exists")
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
