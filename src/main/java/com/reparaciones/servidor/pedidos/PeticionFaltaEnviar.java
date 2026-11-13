package com.reparaciones.servidor.pedidos;

import io.swagger.v3.oas.annotations.media.Schema;

/** Cuerpo de PUT /api/pedidos/filas/{id}/falta-enviar: true pone la marca «falta enviar», false la quita. Obligatorio (nulo es 400). */
public record PeticionFaltaEnviar(@Schema(nullable = true) Boolean falta) {}
