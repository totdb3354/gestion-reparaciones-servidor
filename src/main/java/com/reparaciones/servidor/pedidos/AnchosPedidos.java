package com.reparaciones.servidor.pedidos;

/** Anchos de las columnas de texto de Pedido_fila, Pedido_fila_cambio y Pedido_lectura. Manda el DDL de
 *  sql/migracion-pedidos-tabla.sql, y AnchosPedidosTest comprueba que coinciden. */
final class AnchosPedidos {

    static final int PLATAFORMA = 150;
    static final int SKU = 60;
    static final int IMEI = 60;
    static final int PEDIDO = 60;
    static final int SKU_DESCONOCIDOS = 500;
    static final int MENSAJE = 1000;

    private AnchosPedidos() {}
}
