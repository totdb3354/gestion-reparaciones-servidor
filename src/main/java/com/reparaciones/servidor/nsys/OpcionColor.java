package com.reparaciones.servidor.nsys;

import io.swagger.v3.oas.annotations.media.Schema;

/** Un color del maestro para el diálogo «Corregir…» (spec 0.9.21 §2.3) con el producto que habría que poner en NSYS: el de
 *  ahora si es el color de NSYS; si no, el más usado de ese color; null si ningún móvil lo tiene. */
public record OpcionColor(String color, @Schema(nullable = true) String producto) {}
