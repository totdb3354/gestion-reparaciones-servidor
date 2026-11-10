package com.reparaciones.servidor.pedidos;

import io.swagger.v3.oas.annotations.media.Schema;

/** Cuerpo de PUT /api/pedidos/filas/{id}/imei: 15 cifras, o nulo (o en blanco) para quitar el IMEI. */
public record PeticionImei(@Schema(nullable = true) String imei) {}
