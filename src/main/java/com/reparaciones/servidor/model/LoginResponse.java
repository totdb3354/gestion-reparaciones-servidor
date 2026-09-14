package com.reparaciones.servidor.model;

/** Respuesta del login. Mismo JSON que el Map anterior; tipada para el contrato OpenAPI. */
public record LoginResponse(int idUsu, String nombreUsuario, String rol, Integer idTec, String token) {}
