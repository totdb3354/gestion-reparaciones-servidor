package com.reparaciones.servidor.pedidos;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/** Una fuente para la píldora (spec pieza 2 §4.4 y §9.2): su última lectura buena, la última de todas, el nivel del aviso de
 *  datos antiguos (OK, RETRASO, SIN_DATOS) y los SKU de teléfono que el maestro no conoce. */
public record FuentePedidosVista(String fuente, @Schema(nullable = true) LocalDateTime ultimaBuena,
                                 @Schema(nullable = true) LocalDateTime ultimaLectura, @Schema(nullable = true) String resultado,
                                 int filas, @Schema(nullable = true) String skuDesconocidos, @Schema(nullable = true) String motivo,
                                 String nivel) {}
