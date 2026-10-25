package com.reparaciones.servidor.nsys;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

/** Una fila de «Por pasar a NSYS» (spec 0.9.21 §2.4): lo que dice NSYS hoy ({@code productoNsys} = Goods, {@code gradoNsys} =
 *  grado entero), lo que hay que poner ({@code ponerProducto}: null si no se sabe) y el SKU nuevo (null: sin SKU en el maestro).
 *  {@code desde}: la fecha del cambio vigente más antiguo del móvil (hora de Madrid). {@code ponerCompruebalo} (0.9.22): el
 *  producto sale del nombre del maestro tal cual, porque NSYS nunca ha escrito ese color. Desde la 0.9.22 la web usa las
 *  tarjetas (TarjetasPorPasar); esta lista sigue para la web anterior. */
public record FilaPorPasar(String imeiSn, @Schema(nullable = true) String modelo,
                           @Schema(nullable = true) String modeloMaestro, @Schema(nullable = true) String capacidad,
                           @Schema(nullable = true) String productoNsys, @Schema(nullable = true) String gradoNsys,
                           @Schema(nullable = true) String ponerProducto, @Schema(nullable = true) String ponerGrado,
                           @Schema(nullable = true) String sku, List<PorQueTaller> porQue, LocalDateTime desde,
                           boolean ponerCompruebalo) {}
