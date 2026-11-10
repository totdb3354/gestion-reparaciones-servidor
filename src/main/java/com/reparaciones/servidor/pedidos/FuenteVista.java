package com.reparaciones.servidor.pedidos;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/** Una fuente para la pildora (spec «Pedidos como la hoja» §3): su ultima lectura buena, el resultado y el motivo de la ultima
 *  de todas, y el nivel del aviso de datos antiguos (OK, RETRASO, SIN_DATOS). */
public record FuenteVista(String fuente, @Schema(nullable = true) LocalDateTime ultimaBuena,
                          @Schema(nullable = true) String resultado, int filas,
                          @Schema(nullable = true) String skuDesconocidos, @Schema(nullable = true) String motivo, String nivel) {}
