package com.reparaciones.servidor.security;

import java.security.SecureRandom;

/**
 * Contraseña de un solo uso que el administrador comunica al usuario. Se leen y se teclean en voz alta, así que
 * no lleva caracteres que se confundan (0/O, 1/l/I) ni símbolos.
 */
public final class PasswordTemporal {

    private static final String ALFABETO = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789";
    private static final int LONGITUD = 10;
    private static final SecureRandom AZAR = new SecureRandom();

    private PasswordTemporal() {}

    public static String generar() {
        StringBuilder sb = new StringBuilder(LONGITUD);
        for (int i = 0; i < LONGITUD; i++) {
            sb.append(ALFABETO.charAt(AZAR.nextInt(ALFABETO.length())));
        }
        return sb.toString();
    }
}
