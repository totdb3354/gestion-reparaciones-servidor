package com.reparaciones.servidor.model;

import io.swagger.v3.oas.annotations.media.Schema;

/** Envoltorio {"value": texto|null} de los endpoints que devuelven un único texto opcional. */
public record ValorTexto(@Schema(nullable = true) String value) {}
