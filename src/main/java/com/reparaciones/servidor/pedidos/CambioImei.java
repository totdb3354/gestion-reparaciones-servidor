package com.reparaciones.servidor.pedidos;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/** Un apunte de cambio de IMEI de una fila (Pedido_fila_cambio). usuario: nombre de usuario de quien lo hizo; nulo si el apunte no
 *  tiene usuario (el traspaso desde la hoja). imeiAnterior nulo = antes no habia; imeiNuevo nulo = se quito. */
public record CambioImei(@Schema(nullable = true) String usuario, LocalDateTime fecha,
                         @Schema(nullable = true) String imeiAnterior, @Schema(nullable = true) String imeiNuevo) {}
