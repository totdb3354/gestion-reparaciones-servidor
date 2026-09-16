package com.reparaciones.servidor.model;

import io.swagger.v3.oas.annotations.media.Schema;

/** Respuesta del login. Mismo JSON que el Map anterior; tipada para el contrato OpenAPI. */
public record LoginResponse(int idUsu, String nombreUsuario, String rol,
                            @Schema(nullable = true) Integer idTec, String token) {}
