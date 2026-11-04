package com.reparaciones.servidor.pedidos;

import com.reparaciones.servidor.nsys.AnchosNsys;

/** Anchos de las columnas de texto de Pedido, Pedido_unidad y Pedido_lectura. Manda el DDL de sql/migracion-pedidos.sql y
 *  AnchosPedidosTest comprueba que coinciden. Todo texto que viene de una fuente se corta aquí antes de guardarlo: en modo
 *  estricto, un valor largo haría fallar el {@code aplicar} entero cada 5 minutos. */
final class AnchosPedidos {

    // Pedido
    static final int NUMERO = 60;
    static final int CLIENTE = 150;
    static final int PAIS = 10;

    // Pedido_unidad
    static final int SKU = 60;
    static final int IMEI = 40;
    static final int OTRO_ORDER_ID = 60;

    // Pedido_lectura
    static final int SKU_DESCONOCIDOS = 500;
    static final int MENSAJE = 1000;

    private AnchosPedidos() {}

    /** El texto cortado a {@code ancho} caracteres, como en el espejo de NSYS (sin partir un par sustituto); null sigue siendo null. */
    static String corta(String texto, int ancho) {
        return AnchosNsys.corta(texto, ancho);
    }
}
