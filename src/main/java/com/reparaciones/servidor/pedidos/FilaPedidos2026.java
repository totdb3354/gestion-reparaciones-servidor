package com.reparaciones.servidor.pedidos;

import java.time.LocalDateTime;

/** Una venta de PEDIDOS 2026 con su IMEI (spec pieza 2 §4.2): {@code fila}, su número en la hoja; {@code fecha}, cuándo se
 *  tecleó el IMEI; {@code plataforma}, «AMZ» o «FNSK/FSNK» (Amazon y Stockroom) o el nombre del cliente; {@code pedidoAmazon}, el número del pedido de
 *  Amazon al que se envió (columna «Pedido Amazon», que se rellena al enviar), o null si está vacío o no hay columna. */
public record FilaPedidos2026(int fila, LocalDateTime fecha, String plataforma, String sku, String imei, String pedidoAmazon) {

    public FilaPedidos2026(int fila, LocalDateTime fecha, String plataforma, String sku, String imei) {
        this(fila, fecha, plataforma, sku, imei, null);
    }
}
