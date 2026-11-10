package com.reparaciones.servidor.nsys;

import io.swagger.v3.oas.annotations.media.Schema;

/** Una fila de «Fuera de su caja» (spec «Fuera de su caja» §4.2): un móvil en la empresa con grado F, FD o RMA que NSYS tiene en
 *  una caja que no es la de su grado. {@code color} y {@code grado}: los que valen (el grado, sin el «100» de la batería y como
 *  esté escrito). {@code gradoNsys}: el de NSYS si el taller lo ha cambiado («—» si NSYS no lo sabe); null si es el mismo.
 *  {@code caja}: la de NSYS hoy (null: sin caja) y {@code ubicacion}, cómo la llama el ERP. {@code cajaQueToca}: «CF021»,
 *  «Desguace» o «Funcionalidad pendiente». */
public record FilaCajaNsys(String imeiSn, @Schema(nullable = true) String modelo, @Schema(nullable = true) String modeloMaestro,
                           @Schema(nullable = true) String capacidad, boolean esEsim, @Schema(nullable = true) String color,
                           String grado, @Schema(nullable = true) String gradoNsys, @Schema(nullable = true) String caja,
                           @Schema(nullable = true) String ubicacion, String cajaQueToca) {}
