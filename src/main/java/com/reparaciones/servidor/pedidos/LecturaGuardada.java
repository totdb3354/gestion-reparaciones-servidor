package com.reparaciones.servidor.pedidos;

import java.time.LocalDateTime;

/** Una fila de Pedido_lectura. */
public record LecturaGuardada(int id, FuentePedidos fuente, LocalDateTime inicio, LocalDateTime fin, String resultado, int filas,
                              String skuDesconocidos, String mensaje) {}
