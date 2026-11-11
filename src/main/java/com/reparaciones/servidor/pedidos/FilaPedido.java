package com.reparaciones.servidor.pedidos;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** Una fila de la tabla de pedidos (Pedido_fila). El id se ignora al insertar. Viva = su pedido sigue pendiente ({@code cerradaEn}
 *  nula). {@code envioFecha}, {@code transportista} y {@code seguimiento} los pone la lectura cuando ShippyPro da el pedido por
 *  enviado; nulos si no se conoce el envio. */
public record FilaPedido(int id, LocalDate fecha, String plataforma, String sku, String imei, String pedido,
                         LocalDate fechaCompra, String origen, Canal canal, Integer posicion, LocalDateTime cerradaEn,
                         LocalDateTime envioFecha, String transportista, String seguimiento) {

    /** Una fila sin datos de envio. */
    public FilaPedido(int id, LocalDate fecha, String plataforma, String sku, String imei, String pedido,
                      LocalDate fechaCompra, String origen, Canal canal, Integer posicion, LocalDateTime cerradaEn) {
        this(id, fecha, plataforma, sku, imei, pedido, fechaCompra, origen, canal, posicion, cerradaEn, null, null, null);
    }

    public boolean viva() {
        return cerradaEn == null;
    }

    public boolean enviada() {
        return envioFecha != null;
    }
}
