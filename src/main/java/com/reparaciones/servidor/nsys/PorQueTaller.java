package com.reparaciones.servidor.nsys;

import io.swagger.v3.oas.annotations.media.Schema;

/** Por qué un dato de un móvil está «por pasar a NSYS» (spec 0.9.21 §2.4): {@code dato} COLOR o GRADO, {@code origen} MANO
 *  (quién y motivo) o REPARACION (pieza 2: {@code idRep}). */
public record PorQueTaller(String dato, String origen, @Schema(nullable = true) String usuario,
                           @Schema(nullable = true) String motivo, @Schema(nullable = true) String idRep) {}
