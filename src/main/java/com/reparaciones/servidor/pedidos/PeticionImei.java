package com.reparaciones.servidor.pedidos;

import io.swagger.v3.oas.annotations.media.Schema;

/** Cuerpo de PUT /api/pedidos/filas/{id}/imei: 15 cifras, o nulo (o en blanco) para quitar el IMEI. {@code imeiAnterior} (opcional): lo que la
 *  persona veia en la fila; nulo = no se comprueba, cadena vacia = esperaba la fila sin IMEI, 15 cifras = esperaba ese IMEI. */
public record PeticionImei(@Schema(nullable = true) String imei, @Schema(nullable = true) String imeiAnterior) {
    public PeticionImei(String imei) { this(imei, null); }
}
