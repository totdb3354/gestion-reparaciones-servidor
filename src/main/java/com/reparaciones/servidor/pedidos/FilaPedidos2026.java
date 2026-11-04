package com.reparaciones.servidor.pedidos;

import java.time.LocalDateTime;

/** Una venta de PEDIDOS 2026 con su IMEI (spec pieza 2 §4.2): {@code fila}, su número en la hoja; {@code fecha}, cuándo se
 *  tecleó el IMEI; {@code plataforma}, «Amazon» o el nombre del cliente. */
public record FilaPedidos2026(int fila, LocalDateTime fecha, String plataforma, String sku, String imei) {}
