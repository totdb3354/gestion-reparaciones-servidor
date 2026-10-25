package com.reparaciones.servidor.nsys;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/** Una fila de «Revisar a mano» (spec 0.9.22 §3.5): un aviso del taller ({@code tipo} SIN_COLOR, SIN_GRADO o SIN_DESHACER;
 *  {@code causa} BORRADA o EDITADA en SIN_DESHACER; {@code dato} COLOR o GRADO; {@code de}, {@code a} y {@code motivo} como en
 *  Taller_aviso) con el modelo del espejo (null si el IMEI ya no está), su reparación, quién la guardó y cuándo. */
public record FilaRevisarNsys(int idAviso, String imeiSn, @Schema(nullable = true) String modelo,
                              @Schema(nullable = true) String modeloMaestro, @Schema(nullable = true) String capacidad,
                              String tipo, @Schema(nullable = true) String causa, String dato, @Schema(nullable = true) String de,
                              @Schema(nullable = true) String a, @Schema(nullable = true) String motivo, String idRep,
                              @Schema(nullable = true) String usuario, LocalDateTime fecha) {}
