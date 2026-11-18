package com.reparaciones.servidor.pedidos;

import java.time.LocalDateTime;
import java.util.List;

/** Un pedido pendiente de ShippyPro con lo mínimo para trabajar (spec pieza 2 §4.1): sin dirección, email, teléfono ni precios
 *  (el estado y el servicio de envío no son datos personales). {@code fecha} en hora de Madrid; {@code cliente}, {@code pais},
 *  {@code estado} y {@code servicio}, null si vienen vacíos. */
public record PedidoShippyPro(String numero, String marketplace, LocalDateTime fecha, String cliente, String pais,
                              List<Articulo> articulos, String estado, String servicio) {

    public PedidoShippyPro {
        articulos = List.copyOf(articulos);
    }

    /** Sin estado ni servicio (null). */
    public PedidoShippyPro(String numero, String marketplace, LocalDateTime fecha, String cliente, String pais,
                           List<Articulo> articulos) {
        this(numero, marketplace, fecha, cliente, pais, articulos, null, null);
    }

    public record Articulo(String sku, int cantidad) {}
}
