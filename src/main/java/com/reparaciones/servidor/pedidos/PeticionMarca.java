package com.reparaciones.servidor.pedidos;

import io.swagger.v3.oas.annotations.media.Schema;

/** Cuerpo de PUT /api/pedidos/filas/{id}/marca. marca: ESPERA_STOCK, EN_REVISION, PENDIENTE_COLOR o CANCELADO; nula quita la marca. nota: opcional,
 *  hasta 200 caracteres (se recorta; vacia = sin nota); se ignora al quitar. marcaAnterior: la marca que la persona veia en la fila (nula = no
 *  comprueba; "" = esperaba la fila sin marca); si no coincide con la actual, 409. */
public record PeticionMarca(@Schema(nullable = true) String marca, @Schema(nullable = true) String nota,
                            @Schema(nullable = true) String marcaAnterior) {}
