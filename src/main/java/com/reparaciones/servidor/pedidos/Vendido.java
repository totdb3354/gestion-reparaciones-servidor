package com.reparaciones.servidor.pedidos;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/** Un movil vendido que el ERP sirve al bot de stock (GET /api/pedidos/vendidos): el IMEI de 15 cifras y, para diagnosticar a mano,
 *  los datos de la fila (sin la plataforma: en los pedidos web es el nombre del cliente). fechaImei: cuando se puso el IMEI en la
 *  fila (ultimo apunte de Pedido_fila_cambio, hora de Madrid sin zona). */
public record Vendido(String imei, @Schema(nullable = true) String sku, @Schema(nullable = true) String canal,
                      @Schema(nullable = true) String pedido, LocalDateTime fechaImei) {}
