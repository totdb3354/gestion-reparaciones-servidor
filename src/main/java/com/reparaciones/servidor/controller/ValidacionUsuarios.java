package com.reparaciones.servidor.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Set;

/** Reglas del alta de usuarios y técnicos y del cambio de contraseña que hasta el sub-proyecto 6 solo aplicaba el
 *  cliente JavaFX (spec 6 §4.1, §4.2 y §4.4). Mismos textos que el cliente cuando existen. Un 422 se lanza siempre
 *  antes de escribir y de registrar log. La usan UsuarioController y AuthController. */
final class ValidacionUsuarios {

    private ValidacionUsuarios() {}

    /** Los dos roles del combo del alta (RegisterController :53); ADMIN no se crea desde la aplicación. */
    static final Set<String> ROLES = Set.of("TECNICO", "SUPERTECNICO");

    static final String MSG_CAMPOS         = "Todos los campos son obligatorios.";
    static final String MSG_PASSWORD_CORTA = "La contraseña debe tener al menos 6 caracteres.";
    static final String MSG_USUARIO_LARGO  = "El nombre de usuario no puede superar 50 caracteres.";
    static final String MSG_TECNICO_LARGO  = "El nombre del técnico no puede superar 100 caracteres.";
    static final String MSG_ROL            = "Rol no permitido.";

    static final String MSG_NO_ENCONTRADO  = "Técnico no encontrado.";
    static final String MSG_RELLENA        = "Rellena todos los campos.";

    /** 409 del borrado con datos asociados: el mismo texto que la cabecera del aviso del cliente (RegisterController :224). */
    static String msgTieneReferencias(String nombreTecnico) {
        return "\"" + nombreTecnico + "\" tiene reparaciones asociadas.";
    }

    /** Orden del cliente y de la spec: campos → 6 → 50 → 100 → rol; para en el primero. Los nombres llegan ya
     *  recortados (null si venían null); la contraseña no se recorta (calco). {@code rol} null vale TECNICO. */
    static void validarAlta(String nombreTecnico, String nombreUsuario, String password, String rol) {
        if (vacio(nombreTecnico) || vacio(nombreUsuario) || vacio(password)) throw regla(MSG_CAMPOS);
        if (password.length() < 6) throw regla(MSG_PASSWORD_CORTA);
        if (nombreUsuario.length() > 50) throw regla(MSG_USUARIO_LARGO);
        if (nombreTecnico.length() > 100) throw regla(MSG_TECNICO_LARGO);
        if (rol != null && !ROLES.contains(rol)) throw regla(MSG_ROL);
    }

    /** Cambiar contraseña (spec 6 §4.4), mismo orden que CambiarPasswordController :84-91 y sin trim: alguna vacía o
     *  ausente → "Rellena todos los campos."; nueva de menos de 6 → la misma regla del alta. La confirmación no llega
     *  al servidor (se queda en el cliente). */
    static void validarCambioPassword(String passwordActual, String passwordNueva) {
        if (vacio(passwordActual) || vacio(passwordNueva)) throw regla(MSG_RELLENA);
        if (passwordNueva.length() < 6) throw regla(MSG_PASSWORD_CORTA);
    }

    private static boolean vacio(String s) {
        return s == null || s.isEmpty();
    }

    static ResponseStatusException regla(String mensaje) {
        return new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, mensaje);
    }
}
