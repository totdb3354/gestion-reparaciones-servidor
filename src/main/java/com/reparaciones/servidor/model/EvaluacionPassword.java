package com.reparaciones.servidor.model;

import io.swagger.v3.oas.annotations.media.Schema;

/** Resultado de comprobar una contraseña propuesta: la nota de 0 a 4 (la barra de la web), si se acepta y, si no, el
 *  primer motivo, con el mismo texto que el 422 al guardarla. */
public record EvaluacionPassword(int nota, boolean aceptable, @Schema(nullable = true) String mensaje) {}
