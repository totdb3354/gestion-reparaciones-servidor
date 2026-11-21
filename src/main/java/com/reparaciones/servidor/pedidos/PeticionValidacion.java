package com.reparaciones.servidor.pedidos;

import io.swagger.v3.oas.annotations.media.Schema;

/** Cuerpo de PUT /api/pedidos/filas/{id}/validacion. validar: true da por bueno el aviso de SKU del IMEI de la fila, false quita la validacion
 *  (obligatorio). imeiAnterior (opcional): el IMEI que la persona veia en la fila; nulo = no se comprueba, cadena vacia = esperaba la fila sin
 *  IMEI, 15 cifras = esperaba ese IMEI; si no coincide, 409. */
public record PeticionValidacion(@Schema(nullable = true) Boolean validar, @Schema(nullable = true) String imeiAnterior) {}
