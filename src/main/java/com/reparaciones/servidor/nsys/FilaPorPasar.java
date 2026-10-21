package com.reparaciones.servidor.nsys;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

/** Una fila de «Por pasar a NSYS» (spec 0.9.21 §2.4): lo que dice NSYS hoy ({@code productoNsys} = Goods, {@code gradoNsys} =
 *  grado entero), lo que hay que poner ({@code ponerProducto}: null si ningún móvil de ese color está en NSYS) y el SKU nuevo
 *  (null: sin SKU en el maestro). {@code desde}: la fecha del cambio vigente más antiguo del móvil (hora de Madrid). */
public record FilaPorPasar(String imeiSn, @Schema(nullable = true) String modelo,
                           @Schema(nullable = true) String modeloMaestro, @Schema(nullable = true) String capacidad,
                           @Schema(nullable = true) String productoNsys, @Schema(nullable = true) String gradoNsys,
                           @Schema(nullable = true) String ponerProducto, @Schema(nullable = true) String ponerGrado,
                           @Schema(nullable = true) String sku, List<PorQueTaller> porQue, LocalDateTime desde) {}
