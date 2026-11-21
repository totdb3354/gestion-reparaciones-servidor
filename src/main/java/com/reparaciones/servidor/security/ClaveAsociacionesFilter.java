package com.reparaciones.servidor.security;

import jakarta.servlet.http.HttpServletRequest;

/**
 * La clave de maquina de POST /api/pedidos/asociaciones (las asociaciones IMEI -> pedido de los libros de ventas de Amazon). Misma regla que
 * {@link ClaveVendidosFilter} con su PROPIO ajuste (PEDIDOS_ASOCIACIONES_CLAVE): ajuste sin ninguna clave valida (de 32 caracteres o mas):
 * 404; clave ausente o mala: 401; un token de usuario no vale; esta clave no abre ninguna otra ruta (da una autoridad distinta).
 * Solo mira el POST: el GET de la misma ruta es de usuario (SUPERTECNICO y ADMIN, con su token) y no pasa por aqui.
 */
public class ClaveAsociacionesFilter extends ClaveVendidosFilter {

    public static final String RUTA = "/api/pedidos/asociaciones";
    public static final String ROL = "LIBROS_AMAZON";

    public ClaveAsociacionesFilter(String ajuste) {
        super(ajuste, RUTA, ROL, "asociaciones");
    }

    /** El POST de la ruta exacta: el unico que entra con la clave. */
    public static boolean esDeMaquina(HttpServletRequest request) {
        return "POST".equals(request.getMethod()) && RUTA.equals(request.getRequestURI());
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !esDeMaquina(request);
    }
}
