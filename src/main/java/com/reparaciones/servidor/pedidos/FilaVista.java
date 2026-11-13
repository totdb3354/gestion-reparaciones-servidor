package com.reparaciones.servidor.pedidos;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** Una fila de la tabla de pedidos con el aviso de su IMEI (spec «Pedidos como la hoja» §3.2 y §6 bis). estado: PENDIENTE,
 *  ENVIADA (ShippyPro la da por enviada), NO_PENDIENTE (dejo de estar pendiente sin envio conocido) o HISTORICO. editable: todas
 *  menos las del historico. envioFecha (hora de Madrid), transportista y seguimiento solo vienen en las ENVIADA. impresaEn: cuando ShippyPro dio la etiqueta por impresa (hora de Madrid; nulo = sin imprimir); tieneEtiqueta: la fila tiene el id del envio en ShippyPro. nivel (OK, AVISO
 *  o MAL), motivo, skuMovil y caja solo vienen en filas PENDIENTE, NO_PENDIENTE o ENVIADA sin impresion (impresaEn nulo) con IMEI; en las demas son nulos. canal: nombre del enum Canal; en el historico se deduce al servir de la plataforma (solo AMZ y FNSK/FSNK), nulo en lo demas.
 *  faltaEnviarEn: cuando una persona marco que el movil de la fila falta por enviar (hora de Madrid); nulo = sin marca.
 *  sincronizadaEn: la ultima vez que el plugin de la web aviso de que el pedido ya lleva ese movil; nulo = nunca. */
public record FilaVista(int idFila, LocalDate fecha, String plataforma, @Schema(nullable = true) String sku,
                        @Schema(nullable = true) String imei, @Schema(nullable = true) String pedido,
                        @Schema(nullable = true) LocalDate fechaCompra, String estado, boolean editable,
                        @Schema(nullable = true) LocalDateTime envioFecha, @Schema(nullable = true) String transportista,
                        @Schema(nullable = true) String seguimiento,
                        @Schema(nullable = true) LocalDateTime impresaEn, boolean tieneEtiqueta,
                        @Schema(nullable = true) String nivel, @Schema(nullable = true) String motivo,
                        @Schema(nullable = true) String skuMovil, @Schema(nullable = true) String caja,
                        @Schema(nullable = true) String canal, @Schema(nullable = true) LocalDateTime faltaEnviarEn,
                        @Schema(nullable = true) LocalDateTime sincronizadaEn) {

    /** Una fila sin marca de «falta enviar» ni sincronizacion. */
    public FilaVista(int idFila, LocalDate fecha, String plataforma, String sku, String imei, String pedido, LocalDate fechaCompra,
                     String estado, boolean editable, LocalDateTime envioFecha, String transportista, String seguimiento,
                     LocalDateTime impresaEn, boolean tieneEtiqueta, String nivel, String motivo, String skuMovil, String caja,
                     String canal) {
        this(idFila, fecha, plataforma, sku, imei, pedido, fechaCompra, estado, editable, envioFecha, transportista, seguimiento,
                impresaEn, tieneEtiqueta, nivel, motivo, skuMovil, caja, canal, null, null);
    }
}
