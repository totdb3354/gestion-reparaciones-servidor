package com.reparaciones.servidor.pedidos;

import java.time.LocalDate;

/** Una línea de «Registro actividad» del Simulador (spec pieza 2 §4.3): {@code linea} en mayúsculas (X, E, BB…); {@code imei}
 *  null si todavía no se ha tecleado. */
public record FilaSimulador(int fila, String orderId, String linea, LocalDate fecha, String cliente, String sku, int cantidad,
                            String imei) {}
