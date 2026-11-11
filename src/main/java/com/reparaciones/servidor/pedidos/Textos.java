package com.reparaciones.servidor.pedidos;

import java.util.Locale;
import java.util.regex.Pattern;

/** Cómo se comparan los textos de las fuentes de pedidos (spec pieza 2 §6). Es puro. */
public final class Textos {

    private static final Pattern ULTRAMARINO = Pattern.compile("(?i)ultramarino");
    private static final Pattern TELEFONO = Pattern.compile("^[iI]\\d.*");

    private Textos() {}

    /** Un SKU para comparar: sin espacios a los lados, en minúsculas y con el color como lo llama el maestro
     *  ({@link #equivalente}). null si queda vacío. */
    public static String sku(String sku) {
        if (sku == null || sku.isBlank()) return null;
        return equivalente(sku.strip()).toLowerCase(Locale.ROOT);
    }

    /** El SKU con el color como lo llama el maestro. El único sinónimo es «Ultramarino», que en las hojas se teclea así y el
     *  maestro llama «Azul» (el mismo, y el único, del verificador de SKU del bot de stock). Solo se añade otro si sale de
     *  verdad en las hojas: cada sinónimo de más hace la comparación menos estricta. Lo demás no cambia. */
    public static String equivalente(String sku) {
        return sku == null ? null : ULTRAMARINO.matcher(sku).replaceAll("Azul");
    }

    /** ¿Parece el SKU de un teléfono? Empieza por «i» y una cifra (los portes y las cajas, no). */
    public static boolean pareceTelefono(String sku) {
        return sku != null && TELEFONO.matcher(sku.strip()).matches();
    }
}
