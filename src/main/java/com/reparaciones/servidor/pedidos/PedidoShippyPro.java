package com.reparaciones.servidor.pedidos;

import java.time.LocalDateTime;
import java.util.List;

/** Un pedido pendiente de ShippyPro con lo mínimo para trabajar (spec pieza 2 §4.1): sin dirección, email, teléfono ni precios.
 *  {@code fecha} en hora de Madrid; {@code cliente} y {@code pais}, null si vienen vacíos. */
public record PedidoShippyPro(String numero, String marketplace, LocalDateTime fecha, String cliente, String pais,
                              List<Articulo> articulos) {

    public PedidoShippyPro {
        articulos = List.copyOf(articulos);
    }

    public record Articulo(String sku, int cantidad) {}
}
