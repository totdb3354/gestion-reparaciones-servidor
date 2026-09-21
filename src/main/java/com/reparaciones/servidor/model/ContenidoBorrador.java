package com.reparaciones.servidor.model;

import io.swagger.v3.oas.annotations.media.Schema;

/** Respuesta de GET …/borrador: {"contenido": json|null}. El servidor guarda el borrador opaco. */
public record ContenidoBorrador(@Schema(nullable = true) String contenido) {}
