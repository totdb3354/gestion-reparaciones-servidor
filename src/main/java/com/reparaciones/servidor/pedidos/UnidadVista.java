package com.reparaciones.servidor.pedidos;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/** Una unidad de un pedido para la pantalla (spec pieza 2 §9.2). {@code pide}: {@link Atributos#NINGUNO} si el maestro no conoce
 *  el SKU; {@code movil}: {@link MovilUnidad#NINGUNO} sin IMEI o si NSYS no lo conoce. Ninguno de los dos es nulo. */
public record UnidadVista(int idUnidad, int posicion, String sku, Atributos pide,
                          @Schema(nullable = true) String imei, @Schema(nullable = true) String imeiFuente,
                          @Schema(nullable = true) LocalDateTime imeiTecleadoEn, boolean sinPedidoConcreto,
                          @Schema(nullable = true) String otroOrderId, boolean repetido, String estado,
                          @Schema(nullable = true) String motivo, MovilUnidad movil) {}
