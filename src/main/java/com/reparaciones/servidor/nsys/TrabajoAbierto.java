package com.reparaciones.servidor.nsys;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/** Un trabajo abierto del taller de un IMEI (spec 0.9.18 §6.1): una asignación A… (REPARACION), AG… (GLASS) o AP…
 *  (PULIDO) sin FECHA_FIN, con su técnico y la hora de asignación. {@code entregado} es la llegada del móvil a la glass
 *  (ENTREGADO_AT) y solo se rellena en la glass. Horas de Madrid. */
public record TrabajoAbierto(String categoria, String tecnico, LocalDateTime desde,
                             @Schema(nullable = true) LocalDateTime entregado) {}
