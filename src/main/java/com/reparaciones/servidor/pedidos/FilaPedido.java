package com.reparaciones.servidor.pedidos;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** Una fila de la tabla de pedidos (Pedido_fila). El id se ignora al insertar. Viva = su pedido sigue pendiente ({@code cerradaEn}
 *  nula). {@code envioFecha}, {@code transportista} y {@code seguimiento} los pone la lectura cuando ShippyPro da el pedido por
 *  enviado; nulos si no se conoce el envio. {@code reabiertaEn}: cuando el pedido volvio a estar pendiente (interna; un
 *  envio anterior a esa hora no vale para la fila). {@code envioId}: id del envio en ShippyPro; {@code impresaEn}: cuando
 *  ShippyPro dio la etiqueta por impresa (hora de Madrid); nulos si no se conocen. {@code faltaEnviarEn}: cuando una persona marco
 *  que el movil de la fila falta por enviar (envio parcial de un pedido de web); nulo = sin marca. {@code sincronizadaEn}: la ultima vez
 *  que el plugin de la web aviso de que el pedido ya lleva ese movil; nulo = nunca. {@code marca}: ESPERA_STOCK, EN_REVISION,
 *  PENDIENTE_COLOR o CANCELADO (nula = sin marca), con su nota, cuando se puso y el nombre de usuario de quien la puso. */
public record FilaPedido(int id, LocalDate fecha, String plataforma, String sku, String imei, String pedido,
                         LocalDate fechaCompra, String origen, Canal canal, Integer posicion, LocalDateTime cerradaEn,
                         LocalDateTime envioFecha, String transportista, String seguimiento,
                         LocalDateTime reabiertaEn, Long envioId, LocalDateTime impresaEn, LocalDateTime faltaEnviarEn,
                         LocalDateTime sincronizadaEn, String marca, String marcaNota, LocalDateTime marcaEn, String marcaPor) {

    /** Una fila sin marca. */
    public FilaPedido(int id, LocalDate fecha, String plataforma, String sku, String imei, String pedido,
                      LocalDate fechaCompra, String origen, Canal canal, Integer posicion, LocalDateTime cerradaEn,
                      LocalDateTime envioFecha, String transportista, String seguimiento,
                      LocalDateTime reabiertaEn, Long envioId, LocalDateTime impresaEn, LocalDateTime faltaEnviarEn,
                      LocalDateTime sincronizadaEn) {
        this(id, fecha, plataforma, sku, imei, pedido, fechaCompra, origen, canal, posicion, cerradaEn, envioFecha, transportista,
                seguimiento, reabiertaEn, envioId, impresaEn, faltaEnviarEn, sincronizadaEn, null, null, null, null);
    }

    /** Una fila sin marca de «falta enviar» ni sincronizacion. */
    public FilaPedido(int id, LocalDate fecha, String plataforma, String sku, String imei, String pedido,
                      LocalDate fechaCompra, String origen, Canal canal, Integer posicion, LocalDateTime cerradaEn,
                      LocalDateTime envioFecha, String transportista, String seguimiento,
                      LocalDateTime reabiertaEn, Long envioId, LocalDateTime impresaEn) {
        this(id, fecha, plataforma, sku, imei, pedido, fechaCompra, origen, canal, posicion, cerradaEn, envioFecha, transportista,
                seguimiento, reabiertaEn, envioId, impresaEn, null, null);
    }

    /** Una fila sin id de envio ni impresion. */
    public FilaPedido(int id, LocalDate fecha, String plataforma, String sku, String imei, String pedido,
                      LocalDate fechaCompra, String origen, Canal canal, Integer posicion, LocalDateTime cerradaEn,
                      LocalDateTime envioFecha, String transportista, String seguimiento, LocalDateTime reabiertaEn) {
        this(id, fecha, plataforma, sku, imei, pedido, fechaCompra, origen, canal, posicion, cerradaEn, envioFecha, transportista,
                seguimiento, reabiertaEn, null, null);
    }

    /** Una fila nunca reabierta. */
    public FilaPedido(int id, LocalDate fecha, String plataforma, String sku, String imei, String pedido,
                      LocalDate fechaCompra, String origen, Canal canal, Integer posicion, LocalDateTime cerradaEn,
                      LocalDateTime envioFecha, String transportista, String seguimiento) {
        this(id, fecha, plataforma, sku, imei, pedido, fechaCompra, origen, canal, posicion, cerradaEn, envioFecha, transportista,
                seguimiento, null, null, null);
    }

    /** Una fila sin datos de envio. */
    public FilaPedido(int id, LocalDate fecha, String plataforma, String sku, String imei, String pedido,
                      LocalDate fechaCompra, String origen, Canal canal, Integer posicion, LocalDateTime cerradaEn) {
        this(id, fecha, plataforma, sku, imei, pedido, fechaCompra, origen, canal, posicion, cerradaEn, null, null, null, null, null, null);
    }

    public boolean viva() {
        return cerradaEn == null;
    }

    public boolean tieneEtiqueta() {
        return envioId != null;
    }

    public boolean enviada() {
        return envioFecha != null;
    }
}
