package com.reparaciones.servidor.model;

import io.swagger.v3.oas.annotations.media.Schema;

/** Técnico elegido para la glass automática (spec 3b §4.1); los dos a null si no hay candidato. */
public record PrediccionGlassRespuesta(@Schema(nullable = true) Integer idTec,
                                       @Schema(nullable = true) String nombre) {}
