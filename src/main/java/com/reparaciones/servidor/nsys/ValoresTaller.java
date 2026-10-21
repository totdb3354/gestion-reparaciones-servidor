package com.reparaciones.servidor.nsys;

import io.swagger.v3.oas.annotations.media.Schema;

/** El color y el grado que valen de un móvil (spec 0.9.21 pieza 2 §2.4); null si NSYS no los sabe. */
public record ValoresTaller(@Schema(nullable = true) String color, @Schema(nullable = true) String grado) {

    static final ValoresTaller VACIO = new ValoresTaller(null, null);
}
