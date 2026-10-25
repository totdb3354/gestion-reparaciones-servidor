package com.reparaciones.servidor.nsys;

import io.swagger.v3.oas.annotations.media.Schema;

/** Un color del maestro para el diálogo «Corregir…» (spec 0.9.21 §2.3) con el producto que habría que poner en NSYS: el de
 *  ahora si es el color de NSYS; si no, el de ProductoNsys; null si no se sabe. {@code compruebalo} (0.9.22): el producto sale del
 *  nombre del maestro tal cual. */
public record OpcionColor(String color, @Schema(nullable = true) String producto, boolean compruebalo) {

    public OpcionColor(String color, String producto) {
        this(color, producto, false);
    }
}
