package com.reparaciones.servidor.pedidos;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** Una fila de la tabla de pedidos (Pedido_fila). El id se ignora al insertar. Viva = todavia no enviada. */
public record FilaPedido(int id, LocalDate fecha, String plataforma, String sku, String imei, String pedido,
                         LocalDate fechaCompra, String origen, Canal canal, Integer posicion, LocalDateTime enviadaEn) {

    public boolean viva() {
        return enviadaEn == null;
    }
}
