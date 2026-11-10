package com.reparaciones.servidor.pedidos;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

/** Una fila de la tabla de pedidos con el aviso de su IMEI (spec «Pedidos como la hoja» §3.2). nivel (OK, AVISO o MAL), motivo,
 *  skuMovil y caja solo vienen en filas sin enviar con IMEI; en las demas son nulos. */
public record FilaVista(int idFila, LocalDate fecha, String plataforma, @Schema(nullable = true) String sku,
                        @Schema(nullable = true) String imei, @Schema(nullable = true) String pedido,
                        @Schema(nullable = true) LocalDate fechaCompra, boolean enviada,
                        @Schema(nullable = true) String nivel, @Schema(nullable = true) String motivo,
                        @Schema(nullable = true) String skuMovil, @Schema(nullable = true) String caja) {}
