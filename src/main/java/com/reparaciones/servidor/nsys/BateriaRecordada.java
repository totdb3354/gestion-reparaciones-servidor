package com.reparaciones.servidor.nsys;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Comparator;
import java.util.Optional;

/** Una batería puesta en un móvil «como n/a» (spec 0.9.22 pieza 4 §5.1): su aviso «Grado sin calcular» sin revisar, su R…, si es
 *  nueva (la pieza de hoy de su R…), el motivo («batería bati13 nueva»), quién la guardó (null si ya no existe) y cuándo (hora de
 *  Madrid). Se aplica cuando el grado del móvil tiene letra. */
public record BateriaRecordada(int idAviso, String imeiSn, String idRep, boolean nueva, String motivo, Integer idUsu,
                               LocalDateTime fecha) {

    private static final Comparator<BateriaRecordada> ORDEN = Comparator.comparing(BateriaRecordada::fecha)
            .thenComparingInt(BateriaRecordada::idAviso);

    /** La que vale de las de un IMEI: la más reciente (por fecha y luego por id); vacío sin ninguna. */
    public static Optional<BateriaRecordada> ultima(Collection<BateriaRecordada> deUnImei) {
        return deUnImei.stream().max(ORDEN);
    }
}
