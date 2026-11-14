package com.reparaciones.servidor.pedidos;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/** Cuerpo de POST /api/pedidos/asociaciones: el canal de Amazon (AMAZON o AMAZON_STOCKROOM) y las asociaciones IMEI -> pedido de su
 *  libro de ventas, en el orden del libro (hasta 10.000). Cada par se limpia en el servidor: el numero de pedido se normaliza como lo
 *  guarda la lectura de ShippyPro y el IMEI debe ser de 15 cifras tras recortar espacios; lo que no (vacios, «CANCELADO», otros numeros)
 *  se descarta y se cuenta, sin dar error. */
public record PeticionAsociaciones(String canal, List<Par> pares) {

    /** Una linea del libro: «el movil {@code imei} va en el pedido {@code pedido}». */
    public record Par(@Schema(nullable = true) String pedido, @Schema(nullable = true) String imei) {}
}
