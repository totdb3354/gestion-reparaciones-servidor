package com.reparaciones.servidor.pedidos;

import java.time.LocalDateTime;

/** Una fila de Pedido_lectura: una vuelta de lectura de ShippyPro. */
public record LecturaGuardada(int id, LocalDateTime inicio, LocalDateTime fin, String resultado, int filas,
                              String skuDesconocidos, String mensaje) {}
