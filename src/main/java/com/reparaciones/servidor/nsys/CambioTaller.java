package com.reparaciones.servidor.nsys;

import java.time.LocalDateTime;

/** Una fila de Taller_cambio (spec 0.9.21 §4): un cambio del taller a un dato de un móvil, por IMEI y no por lote.
 *  {@code valorAntes}: lo que valía antes del cambio (el VALOR del cambio al que sustituye o, si no sustituye a ninguno, lo
 *  de NSYS); se deduce al leer, no se guarda. {@code usuario}: el nombre de quien lo hizo (null si ya no existe).
 *  {@code valorCierre}: lo que NSYS tenía al cerrarse solo (PASADO y MANDA_NSYS). {@code idAnterior}: el cambio al que
 *  sustituye. Horas de Madrid, como el espejo. */
public record CambioTaller(int id, String imeiSn, DatoTaller dato, String valor, String valorAntes, String valorNsys,
                           String origen, String idRep, Integer idUsu, String usuario, String motivo,
                           LocalDateTime fecha, Estado estado, LocalDateTime cerradoEn, String valorCierre,
                           Integer idAnterior) {

    /** VIGENTE: manda sobre NSYS (como mucho uno por IMEI y dato). SUSTITUIDO: lo tapó otro cambio del mismo dato.
     *  QUITADO: un cambio a mano que vuelve a lo de NSYS («Volver a lo de NSYS» o elegir lo que dice NSYS); nace cerrado.
     *  PASADO: NSYS lo alcanzó. MANDA_NSYS: NSYS puso después otra cosa en ese dato. */
    public enum Estado { VIGENTE, SUSTITUIDO, QUITADO, PASADO, MANDA_NSYS }

    /** Origen de los cambios a mano («Corregir…» y «Volver a lo de NSYS»). */
    public static final String MANO = "MANO";
    /** Origen de los cambios que hace una reparación al guardarse (pieza 2): llevan su {@code idRep}. */
    public static final String REPARACION = "REPARACION";
}
