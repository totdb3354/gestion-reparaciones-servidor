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
 *  PENDIENTE_COLOR o CANCELADO (nula = sin marca), con su nota, cuando se puso y el nombre de usuario de quien la puso.
 *  {@code asociadaEn}: en Amazon, cuando se confirmo el pedido del IMEI (libro de ventas o historico); nulo = sin asociar;
 *  {@code asociadaOrigen}: LIBRO o HISTORICO (nulo sin asociar).
 *  {@code validadaImei}: el IMEI para el que una persona dio por bueno el aviso de SKU (vale solo mientras sea el IMEI de la fila);
 *  {@code validadaEn} y {@code validadaPor} (nombre de usuario): cuando y quien; los tres nulos = sin validar.
 *  {@code fechaSalida}: ultimo dia para enviar el pedido (la lectura la recalcula en cada vuelta; nula = no se conoce o historico);
 *  {@code pais}: pais de destino de ShippyPro (2 letras; nulo = no se conoce).
 *  {@code imeiReservado}: el IMEI que el ERP aparta para la fila (reserva; nulo = sin reserva; cuenta como sin IMEI);
 *  {@code reservadaEn}: cuando se reservo; {@code sinStockEn}: cuando se apunto «sin stock» (una vez hasta que reserve). */
public record FilaPedido(int id, LocalDate fecha, String plataforma, String sku, String imei, String pedido,
                         LocalDate fechaCompra, String origen, Canal canal, Integer posicion, LocalDateTime cerradaEn,
                         LocalDateTime envioFecha, String transportista, String seguimiento,
                         LocalDateTime reabiertaEn, Long envioId, LocalDateTime impresaEn, LocalDateTime faltaEnviarEn,
                         LocalDateTime sincronizadaEn, String marca, String marcaNota, LocalDateTime marcaEn, String marcaPor,
                         LocalDateTime asociadaEn, String asociadaOrigen,
                         String validadaImei, LocalDateTime validadaEn, String validadaPor,
                         LocalDate fechaSalida, String pais,
                         String imeiReservado, LocalDateTime reservadaEn, LocalDateTime sinStockEn) {

    /** Una fila sin reserva de IMEI. */
    public FilaPedido(int id, LocalDate fecha, String plataforma, String sku, String imei, String pedido,
                      LocalDate fechaCompra, String origen, Canal canal, Integer posicion, LocalDateTime cerradaEn,
                      LocalDateTime envioFecha, String transportista, String seguimiento,
                      LocalDateTime reabiertaEn, Long envioId, LocalDateTime impresaEn, LocalDateTime faltaEnviarEn,
                      LocalDateTime sincronizadaEn, String marca, String marcaNota, LocalDateTime marcaEn, String marcaPor,
                      LocalDateTime asociadaEn, String asociadaOrigen,
                      String validadaImei, LocalDateTime validadaEn, String validadaPor,
                      LocalDate fechaSalida, String pais) {
        this(id, fecha, plataforma, sku, imei, pedido, fechaCompra, origen, canal, posicion, cerradaEn, envioFecha, transportista,
                seguimiento, reabiertaEn, envioId, impresaEn, faltaEnviarEn, sincronizadaEn, marca, marcaNota, marcaEn, marcaPor,
                asociadaEn, asociadaOrigen, validadaImei, validadaEn, validadaPor, fechaSalida, pais, null, null, null);
    }

    /** Una fila sin fecha de salida ni pais. */
    public FilaPedido(int id, LocalDate fecha, String plataforma, String sku, String imei, String pedido,
                      LocalDate fechaCompra, String origen, Canal canal, Integer posicion, LocalDateTime cerradaEn,
                      LocalDateTime envioFecha, String transportista, String seguimiento,
                      LocalDateTime reabiertaEn, Long envioId, LocalDateTime impresaEn, LocalDateTime faltaEnviarEn,
                      LocalDateTime sincronizadaEn, String marca, String marcaNota, LocalDateTime marcaEn, String marcaPor,
                      LocalDateTime asociadaEn, String asociadaOrigen,
                      String validadaImei, LocalDateTime validadaEn, String validadaPor) {
        this(id, fecha, plataforma, sku, imei, pedido, fechaCompra, origen, canal, posicion, cerradaEn, envioFecha, transportista,
                seguimiento, reabiertaEn, envioId, impresaEn, faltaEnviarEn, sincronizadaEn, marca, marcaNota, marcaEn, marcaPor,
                asociadaEn, asociadaOrigen, validadaImei, validadaEn, validadaPor, null, null);
    }

    /** Una fila sin validar. */
    public FilaPedido(int id, LocalDate fecha, String plataforma, String sku, String imei, String pedido,
                      LocalDate fechaCompra, String origen, Canal canal, Integer posicion, LocalDateTime cerradaEn,
                      LocalDateTime envioFecha, String transportista, String seguimiento,
                      LocalDateTime reabiertaEn, Long envioId, LocalDateTime impresaEn, LocalDateTime faltaEnviarEn,
                      LocalDateTime sincronizadaEn, String marca, String marcaNota, LocalDateTime marcaEn, String marcaPor,
                      LocalDateTime asociadaEn, String asociadaOrigen) {
        this(id, fecha, plataforma, sku, imei, pedido, fechaCompra, origen, canal, posicion, cerradaEn, envioFecha, transportista,
                seguimiento, reabiertaEn, envioId, impresaEn, faltaEnviarEn, sincronizadaEn, marca, marcaNota, marcaEn, marcaPor,
                asociadaEn, asociadaOrigen, null, null, null, null, null);
    }

    /** Una fila sin asociar. */
    public FilaPedido(int id, LocalDate fecha, String plataforma, String sku, String imei, String pedido,
                      LocalDate fechaCompra, String origen, Canal canal, Integer posicion, LocalDateTime cerradaEn,
                      LocalDateTime envioFecha, String transportista, String seguimiento,
                      LocalDateTime reabiertaEn, Long envioId, LocalDateTime impresaEn, LocalDateTime faltaEnviarEn,
                      LocalDateTime sincronizadaEn, String marca, String marcaNota, LocalDateTime marcaEn, String marcaPor) {
        this(id, fecha, plataforma, sku, imei, pedido, fechaCompra, origen, canal, posicion, cerradaEn, envioFecha, transportista,
                seguimiento, reabiertaEn, envioId, impresaEn, faltaEnviarEn, sincronizadaEn, marca, marcaNota, marcaEn, marcaPor, null, null);
    }

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
