package com.reparaciones.servidor.security;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * Regla única del parámetro {@code ?tecnico=} de las listas del taller (spec web-taller 2026-09-16 §5.1):
 * un TECNICO solo consulta lo suyo (sin parámetro o con su propio id); pedir otro técnico es 403.
 * SUPERTECNICO y ADMIN mantienen el filtro libre (sin parámetro = todo). Cualquier rol que no sea
 * SUPERTECNICO ni ADMIN se trata como TECNICO, por prudencia.
 */
public final class FiltroTecnico {

    public static final String MSG_SOLO_PROPIOS = "Solo puedes consultar tus propios trabajos";

    private FiltroTecnico() {}

    /**
     * @param principal usuario del token (las rutas exigen sesión, nunca es nulo)
     * @param tecnico   valor de {@code ?tecnico=}, o {@code null} si no vino
     * @return el filtro que debe aplicar el DAO ({@code null} = sin filtro)
     * @throws ResponseStatusException 403 si un técnico pide un técnico distinto del suyo (o no tiene técnico)
     */
    public static Integer efectivo(UsuarioPrincipal principal, Integer tecnico) {
        String rol = principal.getRol();
        if ("SUPERTECNICO".equals(rol) || "ADMIN".equals(rol)) return tecnico;
        Integer propio = principal.getIdTec();
        if (propio == null || (tecnico != null && !tecnico.equals(propio))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, MSG_SOLO_PROPIOS);
        }
        return propio;
    }
}
