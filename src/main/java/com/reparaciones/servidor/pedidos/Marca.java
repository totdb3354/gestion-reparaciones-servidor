package com.reparaciones.servidor.pedidos;

import java.util.Arrays;

/** Las marcas fijas de una fila de ShippyPro de Pedidos (columna MARCA de Pedido_fila). Como mucho una por fila. */
public enum Marca {
    ESPERA_STOCK, EN_REVISION, PENDIENTE_COLOR, CANCELADO;

    /** {@code true} si el texto es exactamente el nombre de una de las cuatro marcas. */
    public static boolean valida(String texto) {
        return texto != null && Arrays.stream(values()).anyMatch(m -> m.name().equals(texto));
    }
}
