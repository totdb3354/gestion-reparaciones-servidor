package com.reparaciones.servidor.nsys;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** Una fila «Sin grado en NSYS» de «Revisar a mano» (spec 0.9.22 pieza 4 §4.1): un móvil sin grado (lo que vale, «como n/a») que
 *  ha pasado por el taller desde el 22/10/2026 12:49. El grado de NSYS tal cual («n/a», vacío o el código), el último trabajo (su
 *  ID, su asignación en hora de Madrid y su {@code categoria}: REPARACION, GLASS o PULIDO), la batería recordada (su R…, si es
 *  nueva y cuándo; null sin ninguna) y, si es un vendido o de baja que ha vuelto, el día de la venta o de la baja y desde cuándo lo
 *  tiene el taller (null en la empresa). Sale hasta que tenga grado (sin «Revisado»). */
public record FilaSinGradoNsys(String imeiSn, @Schema(nullable = true) String modelo,
                               @Schema(nullable = true) String modeloMaestro, @Schema(nullable = true) String capacidad,
                               @Schema(nullable = true) String gradoNsys, String ultimoTrabajo, LocalDateTime ultimoTrabajoEn,
                               String categoria, @Schema(nullable = true) String bateriaIdRep,
                               @Schema(nullable = true) Boolean bateriaNueva, @Schema(nullable = true) LocalDateTime bateriaEn,
                               @Schema(nullable = true) LocalDate vendidoEn, @Schema(nullable = true) LocalDate bajaEn,
                               @Schema(nullable = true) LocalDateTime vueltoDesde) {}
