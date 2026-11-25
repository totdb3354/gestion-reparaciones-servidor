package com.reparaciones.servidor.security;

import jakarta.servlet.http.HttpServletRequest;

/**
 * La clave de maquina de /api/bot/** (bot de stock de NSYS: GET /api/bot/devoluciones y POST /api/bot/movimientos; spec
 * 2026-11-24-bot-devoluciones §6). Misma regla que {@link ClaveVendidosFilter} con su PROPIO ajuste (PEDIDOS_BOT_CLAVE): ajuste sin
 * ninguna clave valida: 404; clave ausente o mala: 401; un token de usuario no vale; esta clave no abre ninguna otra ruta.
 */
public class ClaveBotFilter extends ClaveVendidosFilter {

    public static final String RUTA_BASE = "/api/bot";
    public static final String ROL = "BOT_STOCK";

    public ClaveBotFilter(String ajuste) {
        super(ajuste, RUTA_BASE, ROL, "bot");
    }

    /** Cualquier ruta bajo /api/bot/. */
    public static boolean esDeMaquina(HttpServletRequest request) {
        return request.getRequestURI().startsWith(RUTA_BASE + "/");
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !esDeMaquina(request);
    }
}
