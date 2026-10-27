package com.reparaciones.servidor.nsys;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/** Un IMEI descartado de «No está en NSYS» (spec 0.9.22 pieza 3 §4.2), para «Ver descartados»: lo de su fila (como FilaAltaNsys)
 *  y quién ({@code descartadoPor}: su nombre, null si ya no existe), cuándo (hora de Madrid) y por qué lo descartó. */
public record FilaDescartadaNsys(String imeiSn, @Schema(nullable = true) String modelo, String ultimoTrabajo, String categoria,
                                 @Schema(nullable = true) String descartadoPor, LocalDateTime descartadoEn, String motivo) {}
