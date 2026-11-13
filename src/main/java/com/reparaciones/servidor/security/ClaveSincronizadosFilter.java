package com.reparaciones.servidor.security;

/**
 * La clave de maquina de POST /api/pedidos/sincronizados (el aviso del plugin de la web). Misma regla que
 * {@link ClaveVendidosFilter} pero con su PROPIO ajuste (PEDIDOS_SINCRONIZADOS_CLAVE): las claves que pueden escribir no son las de solo
 * lectura de los vendidos (una misma clave puede estar en los dos ajustes). Ajuste sin ninguna clave valida (de 32 caracteres o mas):
 * 404; clave ausente o mala: 401; un token de usuario no vale; esta clave no abre ninguna otra ruta (da una autoridad distinta).
 */
public class ClaveSincronizadosFilter extends ClaveVendidosFilter {

    public static final String RUTA = "/api/pedidos/sincronizados";
    public static final String ROL = "PLUGIN_WEB";

    public ClaveSincronizadosFilter(String ajuste) {
        super(ajuste, RUTA, ROL, "sincronizados");
    }
}
