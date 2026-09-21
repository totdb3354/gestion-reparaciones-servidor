package com.reparaciones.servidor.security;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * Regla única de las escrituras del formulario (spec web-formulario 2026-09-19 §5.1): el técnico de cada
 * trabajo es el del token y solo se trabaja sobre asignaciones propias. Igual para TECNICO y SUPERTECNICO.
 * El {@code idTec} que envíe el cliente en el cuerpo no interviene: los controllers usan el valor que
 * devuelve {@link #tecnicoEfectivo}.
 */
public final class PropiedadAsignacion {

    public static final String MSG_NO_ES_TUYA        = "Solo puedes trabajar sobre tus propias asignaciones";
    public static final String MSG_SOLO_SUPERTECNICO = "Solo el supertécnico puede corregir una reparación ya hecha";

    private PropiedadAsignacion() {}

    /**
     * @param principal   usuario del token (las rutas exigen sesión, nunca es nulo)
     * @param idTecDueno  ID_TEC de la asignación ({@code null} si la asignación no existe)
     * @return el técnico efectivo (siempre el del token)
     * @throws ResponseStatusException 403 (MSG_NO_ES_TUYA) si el token no tiene técnico o la asignación es de otro.
     *         Si {@code idTecDueno} es null NO lanza: deja que el DAO responda su 409 de "ya eliminada o completada".
     */
    public static int tecnicoEfectivo(UsuarioPrincipal principal, Integer idTecDueno) {
        Integer propio = principal.getIdTec();
        if (propio == null || (idTecDueno != null && !idTecDueno.equals(propio))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, MSG_NO_ES_TUYA);
        }
        return propio;
    }

    /** @throws ResponseStatusException 403 (MSG_SOLO_SUPERTECNICO) si el rol no es SUPERTECNICO. */
    public static void exigirSupertecnico(UsuarioPrincipal principal) {
        if (!"SUPERTECNICO".equals(principal.getRol())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, MSG_SOLO_SUPERTECNICO);
        }
    }
}
