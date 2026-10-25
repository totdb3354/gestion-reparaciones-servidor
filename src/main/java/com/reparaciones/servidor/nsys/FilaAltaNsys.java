package com.reparaciones.servidor.nsys;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/** Una fila de «No está en NSYS» (spec 0.9.22 §3.4): el modelo del taller (código del ERP), el último trabajo con su
 *  {@code categoria} (REPARACION, GLASS o PULIDO) y si es una asignación abierta, su técnico y su cliente, cuántos trabajos, desde
 *  cuándo (el primero) y la marca «Hecho» que vale, como en FilaColorNsys. */
public record FilaAltaNsys(String imeiSn, @Schema(nullable = true) String modelo, String ultimoTrabajo, String categoria,
                           boolean abierto, @Schema(nullable = true) String tecnico, @Schema(nullable = true) String cliente,
                           int trabajos, LocalDateTime desde, @Schema(nullable = true) String hechoPor,
                           @Schema(nullable = true) LocalDateTime hechoEn, @Schema(nullable = true) LocalDateTime hechoVolvio) {}
