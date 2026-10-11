package com.reparaciones.servidor.model;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/** Trabajo cerrado de un IMEI para el modal «Corregir cliente» (spec 0.9.8 añadido §3.1). */
public record TrabajoCerrado(
        String idRep,
        LocalDateTime fechaFin,
        @Schema(nullable = true) String descripcion,
        @Schema(nullable = true) Integer idCli,
        @Schema(nullable = true) String cliente) {}
