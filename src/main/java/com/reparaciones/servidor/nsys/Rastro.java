package com.reparaciones.servidor.nsys;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** Junta los eventos de NSYS y los trabajos del taller de un IMEI en una sola línea de tiempo, del más reciente al más
 *  antiguo (spec pieza 1 §8). La venta del esqueleto (solo el día) no sale si el mismo lote tiene una con hora. */
public final class Rastro {

    private Rastro() {}

    public static List<EventoRastro> unir(List<EventoNsys> nsys, List<TrabajoTaller> trabajos) {
        Set<String> lotesConVentaConHora = nsys.stream().filter(e -> e.tipo() == TipoEvento.VENTA && !e.soloDia())
                .map(EventoNsys::batch).collect(Collectors.toSet());
        // Sobre la lista entera, antes de quitar la venta del esqueleto: así cada tipo sigue con su evento.
        List<String> revisiones = Revision.tipos(nsys);
        List<EventoRastro> todos = new ArrayList<>();
        for (int i = 0; i < nsys.size(); i++) {
            EventoNsys e = nsys.get(i);
            if (e.tipo() == TipoEvento.VENTA && e.soloDia() && lotesConVentaConHora.contains(e.batch())) continue;
            todos.add(new EventoRastro(e.fecha(), e.soloDia(), "NSYS", e.tipo().name(), e.batch(), e.de(), e.a(), e.aNsys(),
                    null, null, null, revisiones.get(i)));
        }
        for (TrabajoTaller t : trabajos) {
            boolean asignacion = t.idRep().startsWith("A");
            LocalDateTime fecha = asignacion || t.terminado() == null ? t.asignado() : t.terminado();
            if (fecha == null) continue;
            todos.add(new EventoRastro(fecha, false, "ERP", asignacion ? "TRABAJO_ASIGNADO" : "TRABAJO_TERMINADO",
                    null, null, null, null, t.idRep(), t.tecnico(), categoria(t.idRep()), null));
        }
        // Del más reciente al más antiguo. A la misma hora, primero lo último apuntado: los eventos de NSYS llegan por
        // orden de alta (ID_EVENTO) y los del taller van detrás, así que un trabajo terminado sale antes que el cambio de
        // NSYS de esa misma exportación, y la venta del esqueleto antes que su entrada.
        List<Integer> orden = new ArrayList<>();
        for (int i = 0; i < todos.size(); i++) orden.add(i);
        orden.sort(Comparator.comparing((Integer i) -> todos.get(i).fecha()).thenComparing(i -> i).reversed());
        return orden.stream().map(todos::get).toList();
    }

    /** Asignaciones A/AG/AP y trabajos hechos R/G/P. */
    static String categoria(String idRep) {
        if (idRep.startsWith("AG") || idRep.startsWith("G")) return "GLASS";
        if (idRep.startsWith("AP") || idRep.startsWith("P")) return "PULIDO";
        return "REPARACION";
    }
}
