package com.reparaciones.servidor.pedidos;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/** Un apunte del historial de una fila (Pedido_fila_cambio). usuario: nombre de usuario de quien lo hizo; nulo si el apunte no
 *  tiene usuario (el traspaso desde la hoja o el plugin de la web). tipo: IMEI (cambio de IMEI), FALTA_ENVIAR (se marco que el movil
 *  falta por enviar), YA_ENVIADO (se quito esa marca), MARCA (se puso o cambio la marca de la fila) o MARCA_QUITADA (se quito); nunca nulo.
 *  En IMEI: imeiAnterior nulo = antes no habia; imeiNuevo nulo = se quito. En los demas tipos los dos IMEI son nulos. detalle: nulo salvo en
 *  MARCA ("<MARCA>" o "<MARCA> — <nota>") y MARCA_QUITADA (la marca que tenia, y "<MARCA> — al poner el IMEI" si la quito el ERP). */
public record CambioImei(@Schema(nullable = true) String usuario, LocalDateTime fecha,
                         @Schema(nullable = true) String imeiAnterior, @Schema(nullable = true) String imeiNuevo, String tipo,
                         @Schema(nullable = true) String detalle) {

    public static final String IMEI = "IMEI";
    public static final String FALTA_ENVIAR = "FALTA_ENVIAR";
    public static final String YA_ENVIADO = "YA_ENVIADO";
    public static final String MARCA = "MARCA";
    public static final String MARCA_QUITADA = "MARCA_QUITADA";

    /** Un apunte sin detalle (IMEI, FALTA_ENVIAR o YA_ENVIADO). */
    public CambioImei(String usuario, LocalDateTime fecha, String imeiAnterior, String imeiNuevo, String tipo) {
        this(usuario, fecha, imeiAnterior, imeiNuevo, tipo, null);
    }

    /** Un apunte de cambio de IMEI. */
    public CambioImei(String usuario, LocalDateTime fecha, String imeiAnterior, String imeiNuevo) {
        this(usuario, fecha, imeiAnterior, imeiNuevo, IMEI, null);
    }
}
