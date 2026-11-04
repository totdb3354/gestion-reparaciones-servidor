package com.reparaciones.servidor.pedidos;

import java.time.LocalDateTime;

/** Un pedido abierto de la tabla Pedido. */
public record PedidoGuardado(int id, Canal canal, String numero, String cliente, String pais, LocalDateTime fecha,
                             FuentePedidos fuente) {}
