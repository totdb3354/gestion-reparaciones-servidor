package com.reparaciones.servidor.nsys;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/** Lo que necesita el diálogo «Corregir color, grado o batería…» (spec 0.9.21 §2.3): el producto actual del móvil, los
 *  colores del maestro para su modelo, capacidad y eSIM (vacío sin modelo en el maestro) y los SKU por color y grado. */
public record OpcionesTaller(@Schema(nullable = true) String productoActual, List<OpcionColor> colores,
                             List<SkuMaestro> skus) {}
