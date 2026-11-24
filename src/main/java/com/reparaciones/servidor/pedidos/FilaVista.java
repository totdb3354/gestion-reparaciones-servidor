package com.reparaciones.servidor.pedidos;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** Una fila de la tabla de pedidos con el aviso de su IMEI (spec «Pedidos como la hoja» §3.2 y §6 bis). estado: PENDIENTE,
 *  ENVIADA (ShippyPro la da por enviada), NO_PENDIENTE (dejo de estar pendiente sin envio conocido) o HISTORICO. editable: todas
 *  menos las del historico. envioFecha (hora de Madrid), transportista y seguimiento solo vienen en las ENVIADA. impresaEn: cuando ShippyPro dio la etiqueta por impresa (hora de Madrid; nulo = sin imprimir); tieneEtiqueta: la fila tiene el id del envio en ShippyPro. nivel (OK, AVISO
 *  o MAL), motivo, skuMovil y caja solo vienen en filas PENDIENTE, NO_PENDIENTE o ENVIADA sin impresion (impresaEn nulo) con IMEI; en las demas son nulos. canal: nombre del enum Canal; en el historico se deduce al servir de la plataforma (solo AMZ y FNSK/FSNK), nulo en lo demas.
 *  faltaEnviarEn: cuando una persona marco que el movil de la fila falta por enviar (hora de Madrid); nulo = sin marca.
 *  sincronizadaEn: la ultima vez que el plugin de la web aviso de que el pedido ya lleva ese movil; nulo = nunca.
 *  marca: ESPERA_STOCK, EN_REVISION, PENDIENTE_COLOR o CANCELADO; nula = sin marca. marcaNota: nota libre de la marca (hasta 200); marcaEn: cuando se
 *  puso (hora de Madrid); marcaPor: nombre de usuario de quien la puso; los tres nulos sin marca (marcaNota tambien si la marca no lleva nota).
 *  asociadaEn: en Amazon, cuando se confirmo el pedido del IMEI (hora de Madrid); nulo = sin asociar (la web no enseña el numero de una fila
 *  de Amazon sin asociar, aunque pedido venga: lo necesita el estado). asociadaOrigen: LIBRO (libro de ventas) o HISTORICO (carga del
 *  historico de la hoja); nulo sin asociar. En las webs y el historico los dos son nulos y no significan nada.
 *  validadaEn, validadaPor y motivoValidado: solo si una persona dio por bueno el aviso de SKU de este IMEI y el aviso sigue siendo de SKU
 *  (entonces nivel va OK, motivo nulo y motivoValidado es el motivo que tenia: MODELO, CAPACIDAD, COLOR, GRADO, COLOR_O_GRADO, GRADO_NO_VENDIBLE o SKU_DESCONOCIDO);
 *  nulos si no hay validacion o dejo de valer (otro IMEI, o el aviso ya es otro).
 *  fechaSalida: ultimo dia para enviar (Amazon: Latest ship de ShippyPro; webs: el laborable siguiente al primer laborable desde la
 *  entrada; historico: nulo).
 *  salidaDesde: en las webs, primer dia en que el pedido ya «sale hoy» (la entrada, o el primer laborable si entro en festivo o fin de
 *  semana); nulo en Amazon, en el historico y sin fecha de salida.
 *  detalle: lo que acompaña al motivo (DetalleAviso); nulo sin aviso o sin nada que decir. Validada: conserva sim, na y ms.
 *  pais: pais de destino (2 letras), nulo si no se conoce.
 *  imeiReservado: el IMEI que el ERP aparta para la fila (reserva; nulo = sin reserva; la fila sigue contando como sin IMEI);
 *  reservadaEn: cuando se reservo (hora de Madrid). */
public record FilaVista(int idFila, LocalDate fecha, String plataforma, @Schema(nullable = true) String sku,
                        @Schema(nullable = true) String imei, @Schema(nullable = true) String pedido,
                        @Schema(nullable = true) LocalDate fechaCompra, String estado, boolean editable,
                        @Schema(nullable = true) LocalDateTime envioFecha, @Schema(nullable = true) String transportista,
                        @Schema(nullable = true) String seguimiento,
                        @Schema(nullable = true) LocalDateTime impresaEn, boolean tieneEtiqueta,
                        @Schema(nullable = true) String nivel, @Schema(nullable = true) String motivo,
                        @Schema(nullable = true) String skuMovil, @Schema(nullable = true) String caja,
                        @Schema(nullable = true) String canal, @Schema(nullable = true) LocalDateTime faltaEnviarEn,
                        @Schema(nullable = true) LocalDateTime sincronizadaEn,
                        @Schema(nullable = true) String marca, @Schema(nullable = true) String marcaNota,
                        @Schema(nullable = true) LocalDateTime marcaEn, @Schema(nullable = true) String marcaPor,
                        @Schema(nullable = true) LocalDateTime asociadaEn, @Schema(nullable = true) String asociadaOrigen,
                        @Schema(nullable = true) LocalDateTime validadaEn, @Schema(nullable = true) String validadaPor,
                        @Schema(nullable = true) String motivoValidado,
                        @Schema(nullable = true) LocalDate fechaSalida, @Schema(nullable = true) String pais,
                        @Schema(nullable = true) LocalDate salidaDesde,
                        @Schema(nullable = true) DetalleAviso detalle,
                        @Schema(nullable = true) String imeiReservado, @Schema(nullable = true) LocalDateTime reservadaEn) {

    /** Una fila sin reserva de IMEI. */
    public FilaVista(int idFila, LocalDate fecha, String plataforma, String sku, String imei, String pedido, LocalDate fechaCompra,
                     String estado, boolean editable, LocalDateTime envioFecha, String transportista, String seguimiento,
                     LocalDateTime impresaEn, boolean tieneEtiqueta, String nivel, String motivo, String skuMovil, String caja,
                     String canal, LocalDateTime faltaEnviarEn, LocalDateTime sincronizadaEn, String marca, String marcaNota,
                     LocalDateTime marcaEn, String marcaPor, LocalDateTime asociadaEn, String asociadaOrigen,
                     LocalDateTime validadaEn, String validadaPor, String motivoValidado, LocalDate fechaSalida, String pais,
                     LocalDate salidaDesde, DetalleAviso detalle) {
        this(idFila, fecha, plataforma, sku, imei, pedido, fechaCompra, estado, editable, envioFecha, transportista, seguimiento,
                impresaEn, tieneEtiqueta, nivel, motivo, skuMovil, caja, canal, faltaEnviarEn, sincronizadaEn, marca, marcaNota, marcaEn,
                marcaPor, asociadaEn, asociadaOrigen, validadaEn, validadaPor, motivoValidado, fechaSalida, pais, salidaDesde, detalle,
                null, null);
    }

    /** Una fila sin {@code detalle}. */
    public FilaVista(int idFila, LocalDate fecha, String plataforma, String sku, String imei, String pedido, LocalDate fechaCompra,
                     String estado, boolean editable, LocalDateTime envioFecha, String transportista, String seguimiento,
                     LocalDateTime impresaEn, boolean tieneEtiqueta, String nivel, String motivo, String skuMovil, String caja,
                     String canal, LocalDateTime faltaEnviarEn, LocalDateTime sincronizadaEn, String marca, String marcaNota,
                     LocalDateTime marcaEn, String marcaPor, LocalDateTime asociadaEn, String asociadaOrigen,
                     LocalDateTime validadaEn, String validadaPor, String motivoValidado, LocalDate fechaSalida, String pais,
                     LocalDate salidaDesde) {
        this(idFila, fecha, plataforma, sku, imei, pedido, fechaCompra, estado, editable, envioFecha, transportista, seguimiento,
                impresaEn, tieneEtiqueta, nivel, motivo, skuMovil, caja, canal, faltaEnviarEn, sincronizadaEn, marca, marcaNota, marcaEn,
                marcaPor, asociadaEn, asociadaOrigen, validadaEn, validadaPor, motivoValidado, fechaSalida, pais, salidaDesde, null,
                null, null);
    }

    /** Una fila sin {@code salidaDesde} (Amazon, historico o tests). */
    public FilaVista(int idFila, LocalDate fecha, String plataforma, String sku, String imei, String pedido, LocalDate fechaCompra,
                     String estado, boolean editable, LocalDateTime envioFecha, String transportista, String seguimiento,
                     LocalDateTime impresaEn, boolean tieneEtiqueta, String nivel, String motivo, String skuMovil, String caja,
                     String canal, LocalDateTime faltaEnviarEn, LocalDateTime sincronizadaEn, String marca, String marcaNota,
                     LocalDateTime marcaEn, String marcaPor, LocalDateTime asociadaEn, String asociadaOrigen,
                     LocalDateTime validadaEn, String validadaPor, String motivoValidado, LocalDate fechaSalida, String pais) {
        this(idFila, fecha, plataforma, sku, imei, pedido, fechaCompra, estado, editable, envioFecha, transportista, seguimiento,
                impresaEn, tieneEtiqueta, nivel, motivo, skuMovil, caja, canal, faltaEnviarEn, sincronizadaEn, marca, marcaNota, marcaEn,
                marcaPor, asociadaEn, asociadaOrigen, validadaEn, validadaPor, motivoValidado, fechaSalida, pais, null, null, null,
                null);
    }

    /** Una fila sin fecha de salida ni pais. */
    public FilaVista(int idFila, LocalDate fecha, String plataforma, String sku, String imei, String pedido, LocalDate fechaCompra,
                     String estado, boolean editable, LocalDateTime envioFecha, String transportista, String seguimiento,
                     LocalDateTime impresaEn, boolean tieneEtiqueta, String nivel, String motivo, String skuMovil, String caja,
                     String canal, LocalDateTime faltaEnviarEn, LocalDateTime sincronizadaEn, String marca, String marcaNota,
                     LocalDateTime marcaEn, String marcaPor, LocalDateTime asociadaEn, String asociadaOrigen,
                     LocalDateTime validadaEn, String validadaPor, String motivoValidado) {
        this(idFila, fecha, plataforma, sku, imei, pedido, fechaCompra, estado, editable, envioFecha, transportista, seguimiento,
                impresaEn, tieneEtiqueta, nivel, motivo, skuMovil, caja, canal, faltaEnviarEn, sincronizadaEn, marca, marcaNota, marcaEn,
                marcaPor, asociadaEn, asociadaOrigen, validadaEn, validadaPor, motivoValidado, null, null);
    }

    /** Una fila sin validar. */
    public FilaVista(int idFila, LocalDate fecha, String plataforma, String sku, String imei, String pedido, LocalDate fechaCompra,
                     String estado, boolean editable, LocalDateTime envioFecha, String transportista, String seguimiento,
                     LocalDateTime impresaEn, boolean tieneEtiqueta, String nivel, String motivo, String skuMovil, String caja,
                     String canal, LocalDateTime faltaEnviarEn, LocalDateTime sincronizadaEn, String marca, String marcaNota,
                     LocalDateTime marcaEn, String marcaPor, LocalDateTime asociadaEn, String asociadaOrigen) {
        this(idFila, fecha, plataforma, sku, imei, pedido, fechaCompra, estado, editable, envioFecha, transportista, seguimiento,
                impresaEn, tieneEtiqueta, nivel, motivo, skuMovil, caja, canal, faltaEnviarEn, sincronizadaEn, marca, marcaNota, marcaEn,
                marcaPor, asociadaEn, asociadaOrigen, null, null, null, null, null);
    }

    /** Una fila sin asociar. */
    public FilaVista(int idFila, LocalDate fecha, String plataforma, String sku, String imei, String pedido, LocalDate fechaCompra,
                     String estado, boolean editable, LocalDateTime envioFecha, String transportista, String seguimiento,
                     LocalDateTime impresaEn, boolean tieneEtiqueta, String nivel, String motivo, String skuMovil, String caja,
                     String canal, LocalDateTime faltaEnviarEn, LocalDateTime sincronizadaEn, String marca, String marcaNota,
                     LocalDateTime marcaEn, String marcaPor) {
        this(idFila, fecha, plataforma, sku, imei, pedido, fechaCompra, estado, editable, envioFecha, transportista, seguimiento,
                impresaEn, tieneEtiqueta, nivel, motivo, skuMovil, caja, canal, faltaEnviarEn, sincronizadaEn, marca, marcaNota, marcaEn,
                marcaPor, null, null);
    }

    /** Una fila sin marca. */
    public FilaVista(int idFila, LocalDate fecha, String plataforma, String sku, String imei, String pedido, LocalDate fechaCompra,
                     String estado, boolean editable, LocalDateTime envioFecha, String transportista, String seguimiento,
                     LocalDateTime impresaEn, boolean tieneEtiqueta, String nivel, String motivo, String skuMovil, String caja,
                     String canal, LocalDateTime faltaEnviarEn, LocalDateTime sincronizadaEn) {
        this(idFila, fecha, plataforma, sku, imei, pedido, fechaCompra, estado, editable, envioFecha, transportista, seguimiento,
                impresaEn, tieneEtiqueta, nivel, motivo, skuMovil, caja, canal, faltaEnviarEn, sincronizadaEn, null, null, null, null);
    }

    /** Una fila sin marca de «falta enviar» ni sincronizacion. */
    public FilaVista(int idFila, LocalDate fecha, String plataforma, String sku, String imei, String pedido, LocalDate fechaCompra,
                     String estado, boolean editable, LocalDateTime envioFecha, String transportista, String seguimiento,
                     LocalDateTime impresaEn, boolean tieneEtiqueta, String nivel, String motivo, String skuMovil, String caja,
                     String canal) {
        this(idFila, fecha, plataforma, sku, imei, pedido, fechaCompra, estado, editable, envioFecha, transportista, seguimiento,
                impresaEn, tieneEtiqueta, nivel, motivo, skuMovil, caja, canal, null, null, null, null, null, null);
    }
}
