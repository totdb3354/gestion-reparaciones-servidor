package com.reparaciones.servidor.nsys;

import java.time.LocalDateTime;

/** Una fila de Taller_aviso (spec 0.9.21 «SKU del taller», pieza 2, §2.2 y §2.3): lo que el taller avisa al guardar, editar o
 *  borrar una reparación sin cambiar el dato. {@code de}: en SIN_COLOR, el SKU de la pieza; en SIN_GRADO, el grado que no se
 *  pudo calcular; en DESHECHO y SIN_DESHACER, lo que había puesto la reparación. {@code a}: en DESHECHO, lo que vuelve a
 *  valer. {@code motivo}: la pieza («chasis chai13red nuevo»). {@code usuario}: quien guardó (null si ya no existe). Horas de
 *  Madrid, como el espejo. */
public record AvisoTaller(int id, String imeiSn, String idRep, Tipo tipo, Causa causa, DatoTaller dato, String de, String a,
                          String motivo, Integer idUsu, String usuario, LocalDateTime fecha) {

    /** SIN_COLOR: el color del chasis o de la tapa no se puede deducir del maestro. SIN_GRADO: el grado no es de la forma
     *  [Ms]<letra>[100]. DESHECHO: se deshizo el cambio de la reparación. SIN_DESHACER: ya no mandaba y no se tocó. */
    public enum Tipo { SIN_COLOR, SIN_GRADO, DESHECHO, SIN_DESHACER }

    /** Por qué se deshace (DESHECHO y SIN_DESHACER). */
    public enum Causa { BORRADA, EDITADA }

    /** Un aviso que entra. */
    public record Nuevo(String imeiSn, String idRep, Tipo tipo, Causa causa, DatoTaller dato, String de, String a,
                        String motivo) {}
}
