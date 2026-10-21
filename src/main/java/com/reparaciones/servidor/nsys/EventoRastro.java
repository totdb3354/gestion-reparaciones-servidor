package com.reparaciones.servidor.nsys;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/** Un evento del rastro tal como lo recibe la web: de NSYS (lote, de, a, aNsys) o del taller del ERP (idRep, técnico,
 *  categoría). {@code tipo}: los de {@link TipoEvento}, TRABAJO_ASIGNADO / TRABAJO_TERMINADO o CORRECCION (0.9.21: la baja
 *  de un lote y el alta del IMEI en otro; {@code lote} el nuevo, {@code loteAnterior} el viejo y {@code de}/{@code a} el
 *  móvil antes y después, {@link Correcciones}). {@code revision}: ENTRADA o DEVOLUCION en el cambio de caja que es la
 *  revisión del lote (0.9.18, {@link Revision}). La web compone los textos. */
public record EventoRastro(LocalDateTime fecha, boolean soloDia, String fuente, String tipo,
                           @Schema(nullable = true) String lote, @Schema(nullable = true) String de,
                           @Schema(nullable = true) String a, @Schema(nullable = true) String aNsys,
                           @Schema(nullable = true) String idRep, @Schema(nullable = true) String tecnico,
                           @Schema(nullable = true) String categoria, @Schema(nullable = true) String revision,
                           @Schema(nullable = true) String loteAnterior) {}
