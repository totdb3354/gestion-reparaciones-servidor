package com.reparaciones.servidor.bot;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/** Un movimiento que el bot de NSYS informa (spec 2026-11-24-bot-devoluciones §6.2). accion: DESCONTADO o DEVUELTO; resultado: OK,
 *  NO_APARECE o ERROR; origen: la Location que leyo antes de mover, tal cual; destino: a donde lo movio (o intento); motivo: en
 *  DEVUELTO, el de la lista; fecha: hora de Madrid del bot. */
public record MovimientoBot(String imei, String accion, String resultado, @Schema(nullable = true) String origen,
                            @Schema(nullable = true) String destino, @Schema(nullable = true) String motivo, LocalDateTime fecha) {}
