package com.reparaciones.servidor.pedidos;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

/** Un pedido abierto para la pantalla. {@code viejo}: del Simulador con más días que la ventana (sale con el interruptor). */
public record PedidoVista(int idPedido, String canal, String numero, @Schema(nullable = true) String cliente,
                          @Schema(nullable = true) String pais, LocalDateTime fecha, String fuente, boolean viejo,
                          List<UnidadVista> unidades) {}
