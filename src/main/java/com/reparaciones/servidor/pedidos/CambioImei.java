package com.reparaciones.servidor.pedidos;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/** Un apunte del historial de una fila (Pedido_fila_cambio). usuario: nombre de usuario de quien lo hizo; nulo si el apunte no
 *  tiene usuario (el traspaso desde la hoja o el plugin de la web). tipo: IMEI (cambio de IMEI), FALTA_ENVIAR (se marco que el movil
 *  falta por enviar) o YA_ENVIADO (se quito esa marca); nunca nulo. En IMEI: imeiAnterior nulo = antes no habia; imeiNuevo nulo = se
 *  quito. En los otros dos tipos los dos IMEI son nulos. */
public record CambioImei(@Schema(nullable = true) String usuario, LocalDateTime fecha,
                         @Schema(nullable = true) String imeiAnterior, @Schema(nullable = true) String imeiNuevo, String tipo) {

    public static final String IMEI = "IMEI";
    public static final String FALTA_ENVIAR = "FALTA_ENVIAR";
    public static final String YA_ENVIADO = "YA_ENVIADO";

    /** Un apunte de cambio de IMEI. */
    public CambioImei(String usuario, LocalDateTime fecha, String imeiAnterior, String imeiNuevo) {
        this(usuario, fecha, imeiAnterior, imeiNuevo, IMEI);
    }
}
