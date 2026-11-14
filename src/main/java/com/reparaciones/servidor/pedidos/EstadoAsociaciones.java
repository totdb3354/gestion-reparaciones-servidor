package com.reparaciones.servidor.pedidos;

import java.time.LocalDateTime;
import java.util.List;

/** GET /api/pedidos/asociaciones: la ultima pasada de cada canal de Amazon que ha tenido alguna (lista vacia si no hubo ninguna), en
 *  orden de canal, con lo que dejo «para revisar» en el orden del libro. Horas de Madrid sin zona. */
public record EstadoAsociaciones(List<Pasada> canales) {

    /** {@code canal}: AMAZON o AMAZON_STOCKROOM; {@code cuando}: la pasada; los recuentos, los de {@link RespuestaAsociaciones}. */
    public record Pasada(String canal, LocalDateTime cuando, int confirmadas, int intercambiadas, int puestas, int yaEstaban,
                         int paraRevisar, int descartadas, List<Revisar> revisar) {}

    /** Una asociacion que no se pudo aplicar. {@code motivo}: SIN_HUECO (el pedido no tiene fila libre ni sin confirmar), YA_SALIO (el IMEI
     *  esta en una fila con etiqueta de otro pedido), PEDIDO_AJENO (el pedido no tiene filas en el ERP) o CANCELADO (las unicas filas
     *  posibles estan canceladas). {@code vistoEn}: la pasada que la dejo para revisar. */
    public record Revisar(String pedido, String imei, String motivo, LocalDateTime vistoEn) {}
}
