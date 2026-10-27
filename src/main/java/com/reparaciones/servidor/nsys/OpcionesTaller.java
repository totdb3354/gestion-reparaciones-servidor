package com.reparaciones.servidor.nsys;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

/** Lo que necesita el diálogo «Corregir color, grado o batería…» (spec 0.9.21 §2.3): el producto actual del móvil, los
 *  colores del maestro para su modelo, capacidad y eSIM (vacío sin modelo en el maestro) y los SKU por color y grado. Desde la
 *  0.9.22 pieza 4 (§5.5), la batería recordada del móvil: su R…, si es nueva y cuándo se guardó (hora de Madrid; null sin
 *  ninguna). */
public record OpcionesTaller(@Schema(nullable = true) String productoActual, List<OpcionColor> colores,
                             List<SkuMaestro> skus, @Schema(nullable = true) String bateriaIdRep,
                             @Schema(nullable = true) Boolean bateriaNueva, @Schema(nullable = true) LocalDateTime bateriaEn) {

    /** Sin batería recordada. */
    public OpcionesTaller(String productoActual, List<OpcionColor> colores, List<SkuMaestro> skus) {
        this(productoActual, colores, skus, null, null, null);
    }
}
