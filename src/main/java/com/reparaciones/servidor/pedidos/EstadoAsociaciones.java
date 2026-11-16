package com.reparaciones.servidor.pedidos;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

/** GET /api/pedidos/asociaciones: la ultima pasada de cada canal de Amazon que ha tenido alguna (lista vacia si no hubo ninguna), en
 *  orden de canal, con lo que dejo «para revisar» en el orden del libro. Horas de Madrid sin zona. */
public record EstadoAsociaciones(List<Pasada> canales) {

    /** {@code canal}: AMAZON o AMAZON_STOCKROOM; {@code cuando}: la pasada; los recuentos, los de {@link RespuestaAsociaciones}. Del
     *  lector de los libros: {@code libroModificado}, la fecha de modificacion en Drive del libro de su ultima lectura buena (null si el
     *  lector nunca lo leyo); {@code error} y {@code errorEn}, el fallo de su ultima vuelta y cuando (null si la ultima fue buena). */
    public record Pasada(String canal, LocalDateTime cuando, int confirmadas, int intercambiadas, int puestas, int yaEstaban,
                         int paraRevisar, int descartadas, @Schema(nullable = true) LocalDateTime libroModificado,
                         @Schema(nullable = true) String error, @Schema(nullable = true) LocalDateTime errorEn, List<Revisar> revisar) {}

    /** Una asociacion que no se pudo aplicar. {@code motivo}: SIN_HUECO (el pedido no tiene hueco: si el IMEI esta en otra fila, ninguna sin confirmar ni etiqueta; si no esta en ninguna, ninguna libre), YA_SALIO (el IMEI
     *  esta en una fila con etiqueta de otro pedido) o CANCELADO (las unicas filas
     *  posibles estan canceladas). PEDIDO_AJENO (el pedido no tiene filas en el ERP) ya no se guarda. {@code vistoEn}: la pasada que la dejo para revisar. */
    public record Revisar(String pedido, String imei, String motivo, LocalDateTime vistoEn) {}
}
