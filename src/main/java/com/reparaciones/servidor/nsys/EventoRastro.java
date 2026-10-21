package com.reparaciones.servidor.nsys;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/** Un evento del rastro tal como lo recibe la web: de NSYS (lote, de, a, aNsys), del taller del ERP (idRep, técnico,
 *  categoría) o, desde la 0.9.21, de lo del taller por encima de NSYS (fuente TALLER: {@code dato} COLOR o GRADO, de → a,
 *  {@code origen} MANO o REPARACION, {@code tecnico} = quién lo hizo, {@code motivo}). {@code tipo}: los de
 *  {@link TipoEvento}, TRABAJO_ASIGNADO / TRABAJO_TERMINADO, CORRECCION (0.9.21: la baja de un lote y el alta del IMEI en
 *  otro; {@code lote} el nuevo, {@code loteAnterior} el viejo y {@code de}/{@code a} el móvil antes y después,
 *  {@link Correcciones}) o TALLER_CAMBIO / TALLER_VUELTA / TALLER_PASADO / TALLER_MANDA_NSYS. {@code revision}: ENTRADA o
 *  DEVOLUCION en el cambio de caja que es la revisión del lote (0.9.18, {@link Revision}). La web compone los textos. */
public record EventoRastro(LocalDateTime fecha, boolean soloDia, String fuente, String tipo,
                           @Schema(nullable = true) String lote, @Schema(nullable = true) String de,
                           @Schema(nullable = true) String a, @Schema(nullable = true) String aNsys,
                           @Schema(nullable = true) String idRep, @Schema(nullable = true) String tecnico,
                           @Schema(nullable = true) String categoria, @Schema(nullable = true) String revision,
                           @Schema(nullable = true) String loteAnterior, @Schema(nullable = true) String dato,
                           @Schema(nullable = true) String origen, @Schema(nullable = true) String motivo) {}
