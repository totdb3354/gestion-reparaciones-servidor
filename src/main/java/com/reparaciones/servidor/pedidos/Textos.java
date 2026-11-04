package com.reparaciones.servidor.pedidos;

import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/** Cómo se comparan los textos de las fuentes de pedidos (spec pieza 2 §6). Es puro. */
public final class Textos {

    private static final Pattern MARCAS = Pattern.compile("\\p{M}+");
    private static final Pattern ESPACIOS = Pattern.compile("\\s+");
    private static final Pattern QUINCE_CIFRAS = Pattern.compile("\\d{15}");
    private static final Pattern TELEFONO = Pattern.compile("^[iI]\\d.*");

    private Textos() {}

    /** Un nombre de cliente para comparar: sin tildes, en minúsculas y con un solo espacio. null si queda vacío. */
    public static String cliente(String nombre) {
        if (nombre == null) return null;
        String s = MARCAS.matcher(Normalizer.normalize(nombre, Normalizer.Form.NFD)).replaceAll("");
        s = ESPACIOS.matcher(s.strip()).replaceAll(" ").toLowerCase(Locale.ROOT);
        return s.isEmpty() ? null : s;
    }

    /** Un SKU para comparar: sin espacios a los lados y en minúsculas. null si queda vacío. */
    public static String sku(String sku) {
        if (sku == null || sku.isBlank()) return null;
        return sku.strip().toLowerCase(Locale.ROOT);
    }

    /** Un IMEI de 15 cifras, venga como texto, como número o como celda de una hoja (JsonNode); null si no lo es
     *  («cancelado», vacío, otra longitud). */
    public static String imei(Object valor) {
        if (valor == null) return null;
        String texto;
        if (valor instanceof JsonNode n) {
            if (n.isNull() || n.isMissingNode()) return null;
            texto = n.isNumber() ? n.decimalValue().toBigInteger().toString() : n.asText();
        } else if (valor instanceof Number num) {
            texto = new BigDecimal(num.toString()).toBigInteger().toString();
        } else {
            texto = valor.toString();
        }
        texto = texto.strip();
        return QUINCE_CIFRAS.matcher(texto).matches() ? texto : null;
    }

    /** ¿Parece el SKU de un teléfono? Empieza por «i» y una cifra (los portes y las cajas, no). */
    public static boolean pareceTelefono(String sku) {
        return sku != null && TELEFONO.matcher(sku.strip()).matches();
    }
}
