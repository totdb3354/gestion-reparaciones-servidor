package com.reparaciones.servidor.nsys;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Un IMEI con trabajos del taller que NSYS no conoce (spec 0.9.22 §3.4, «No está en NSYS»): su modelo del taller
 *  (Telefono.MODELO, un código del ERP como «14pro»), el último trabajo ({@code abierto}: una asignación A… sin terminar), su
 *  técnico y su cliente (el del trabajo o, si no tiene, el del teléfono), cuántos trabajos tiene y desde cuándo (su primera
 *  asignación) y cuándo se asignó su última asignación A… ({@code ultimoEn}, para los descartes de la pieza 3; sin asignaciones,
 *  su último trabajo): terminar una asignación crea su fila R/G/P con la hora del cierre, que no es un trabajo nuevo. Horas de Madrid. */
public record AltaTaller(String imei, String modelo, String ultimo, boolean abierto, String tecnico, String cliente,
                         int trabajos, LocalDateTime desde, LocalDateTime ultimoEn) {

    /** Una fila de Reparacion de esos IMEI, ya en hora de Madrid ({@code terminado}: FECHA_FIN). */
    record Trabajo(String imei, String idRep, LocalDateTime asignado, LocalDateTime terminado, String tecnico, String modelo,
                   String cliente) {

        boolean esAsignacion() {
            return idRep.startsWith("A");
        }
    }

    private static final Comparator<Trabajo> ORDEN = Comparator.comparing(Trabajo::asignado,
            Comparator.nullsFirst(Comparator.naturalOrder())).thenComparing(Trabajo::idRep);

    /** Por IMEI, en el orden en que llegan: cuentan los trabajos hechos (R, G, P) y las asignaciones abiertas (una A terminada ya
     *  tiene su trabajo hecho); el último es el más reciente de todas sus filas (por asignación y luego por id) y «desde», la
     *  primera asignación. Es puro. */
    static List<AltaTaller> de(List<Trabajo> trabajos) {
        Map<String, List<Trabajo>> porImei = new LinkedHashMap<>();
        for (Trabajo t : trabajos) porImei.computeIfAbsent(t.imei(), k -> new ArrayList<>()).add(t);
        List<AltaTaller> altas = new ArrayList<>();
        porImei.forEach((imei, delImei) -> {
            Trabajo ultimo = delImei.stream().max(ORDEN).orElseThrow();
            Trabajo primero = delImei.stream().min(ORDEN).orElseThrow();
            Trabajo ultimaAsignacion = delImei.stream().filter(Trabajo::esAsignacion).max(ORDEN).orElse(ultimo);
            int cuenta = (int) delImei.stream().filter(t -> !t.esAsignacion() || t.terminado() == null).count();
            altas.add(new AltaTaller(imei, ultimo.modelo(), ultimo.idRep(), ultimo.esAsignacion() && ultimo.terminado() == null,
                    ultimo.tecnico(), ultimo.cliente(), cuenta, primero.asignado(), ultimaAsignacion.asignado()));
        });
        return altas;
    }
}
