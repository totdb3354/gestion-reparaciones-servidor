package com.reparaciones.servidor.pedidos;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/** Un apunte del historial de una fila (Pedido_fila_cambio). usuario: nombre de usuario de quien lo hizo; nulo si el apunte no
 *  tiene usuario (el traspaso desde la hoja, el plugin de la web o la asociacion de los libros de ventas de Amazon). tipo: IMEI (cambio
 *  de IMEI), FALTA_ENVIAR (se marco que el movil falta por enviar), YA_ENVIADO (se quito esa marca; detalle «al cancelar» si la quito el ERP al cancelar la fila), MARCA (se puso o cambio la marca de la
 *  fila), MARCA_QUITADA (se quito), ASOCIADA (se confirmo el pedido del IMEI en Amazon) o IMEI_INTERCAMBIADO (la asociacion intercambio el
 *  IMEI con otra fila), VALIDADA (una persona dio por bueno el aviso de SKU del IMEI) o VALIDACION_QUITADA (se quito esa validacion, o
 *  la quito el ERP al cambiar el IMEI); nunca nulo. En IMEI: imeiAnterior nulo = antes no habia; imeiNuevo nulo = se quito. En los demas tipos los dos IMEI
 *  son nulos. detalle: nulo salvo en MARCA ("<MARCA>" o "<MARCA> — <nota>"), MARCA_QUITADA (la marca que tenia, y "<MARCA> — al poner el
 *  IMEI" si la quito el ERP), ASOCIADA ("LIBRO" o "HISTORICO"), IMEI_INTERCAMBIADO ("con la fila <id>"), YA_ENVIADO (nulo, o "al cancelar"), VALIDADA (el motivo del aviso: MODELO, CAPACIDAD, COLOR, GRADO, COLOR_O_GRADO, GRADO_NO_VENDIBLE o SKU_DESCONOCIDO) y
 *  VALIDACION_QUITADA (nulo; "al cambiar el IMEI" si la quito el ERP).
 *  Reserva de IMEI (imeiAnterior/imeiNuevo son el IMEI reservado, segun el caso): RESERVADO (el ERP aparto un IMEI para la fila: imeiNuevo),
 *  RESERVA_CONFIRMADA (una persona o el bot puso ese mismo IMEI y la reserva paso a ser el IMEI de la fila), RESERVA_SUSTITUIDA (se puso
 *  otro IMEI distinto y la reserva se solto: imeiAnterior), RESERVA_QUITADA (la reserva se solto sin poner IMEI: imeiAnterior; detalle = el motivo: CANCELADA, ENVIADA_SIN_IMEI
 *  (la fila se envio sin IMEI: el movil seguramente salio sin teclearlo y no se devuelve a stock), NO_ENCONTRADO o NO_DISPONIBLE) y SIN_STOCK
 *  (no habia IMEI libre que reservar; una vez hasta que reserve; sin IMEI). */
public record CambioImei(@Schema(nullable = true) String usuario, LocalDateTime fecha,
                         @Schema(nullable = true) String imeiAnterior, @Schema(nullable = true) String imeiNuevo, String tipo,
                         @Schema(nullable = true) String detalle) {

    public static final String IMEI = "IMEI";
    public static final String FALTA_ENVIAR = "FALTA_ENVIAR";
    public static final String YA_ENVIADO = "YA_ENVIADO";
    public static final String MARCA = "MARCA";
    public static final String MARCA_QUITADA = "MARCA_QUITADA";
    public static final String ASOCIADA = "ASOCIADA";
    public static final String IMEI_INTERCAMBIADO = "IMEI_INTERCAMBIADO";
    public static final String VALIDADA = "VALIDADA";
    public static final String VALIDACION_QUITADA = "VALIDACION_QUITADA";
    /** El ERP aparto un IMEI para la fila. */
    public static final String RESERVADO = "RESERVADO";
    /** La reserva paso a ser el IMEI de la fila (se puso ese mismo IMEI). */
    public static final String RESERVA_CONFIRMADA = "RESERVA_CONFIRMADA";
    /** Se puso otro IMEI y la reserva se solto. */
    public static final String RESERVA_SUSTITUIDA = "RESERVA_SUSTITUIDA";
    /** La reserva se solto sin poner IMEI; DETALLE = CANCELADA, ENVIADA_SIN_IMEI, NO_ENCONTRADO o NO_DISPONIBLE. */
    public static final String RESERVA_QUITADA = "RESERVA_QUITADA";
    /** No habia IMEI libre que reservar. */
    public static final String SIN_STOCK = "SIN_STOCK";

    /** Un apunte sin detalle (IMEI, FALTA_ENVIAR o YA_ENVIADO). */
    public CambioImei(String usuario, LocalDateTime fecha, String imeiAnterior, String imeiNuevo, String tipo) {
        this(usuario, fecha, imeiAnterior, imeiNuevo, tipo, null);
    }

    /** Un apunte de cambio de IMEI. */
    public CambioImei(String usuario, LocalDateTime fecha, String imeiAnterior, String imeiNuevo) {
        this(usuario, fecha, imeiAnterior, imeiNuevo, IMEI, null);
    }
}
