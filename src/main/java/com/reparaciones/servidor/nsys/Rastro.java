package com.reparaciones.servidor.nsys;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** Junta los eventos de NSYS, los trabajos del taller y, desde la 0.9.21, los cambios del taller por encima de NSYS (spec
 *  «SKU del taller» §2.4) de un IMEI en una sola línea de tiempo, del más reciente al más antiguo (spec pieza 1 §8). La venta
 *  del esqueleto (solo el día) no sale si el mismo lote tiene una con hora. Con los lotes del IMEI, cada corrección de NSYS
 *  sale como un solo evento CORRECCION (0.9.21, {@link Correcciones}). */
public final class Rastro {

    private Rastro() {}

    /** Sin los lotes: no detecta correcciones. */
    public static List<EventoRastro> unir(List<EventoNsys> nsys, List<TrabajoTaller> trabajos) {
        return unir(List.of(), nsys, trabajos, List.of());
    }

    /** Sin los cambios del taller. */
    public static List<EventoRastro> unir(List<MovilNsys> lotes, List<EventoNsys> nsys, List<TrabajoTaller> trabajos) {
        return unir(lotes, nsys, trabajos, List.of());
    }

    /** Sin los avisos del taller. */
    public static List<EventoRastro> unir(List<MovilNsys> lotes, List<EventoNsys> nsys, List<TrabajoTaller> trabajos,
                                          List<CambioTaller> cambios) {
        return unir(lotes, nsys, trabajos, cambios, List.of());
    }

    /** Cada cambio del taller sale a su hora («Taller: …» o, si vuelve a lo de NSYS, «Vuelve a lo de NSYS: …») y su cierre
     *  automático a la hora del cierre («Pasado a NSYS», «Manda NSYS»). Un SUSTITUIDO no tiene evento de cierre: lo cuenta el
     *  cambio que lo sustituye. Desde la pieza 2, los avisos del taller (TALLER_SIN_COLOR, TALLER_SIN_GRADO, TALLER_DESHECHO_*
     *  y TALLER_SIN_DESHACER_*), a su hora; a la misma hora que un cambio, detrás de él.
     *  Desde la 0.9.22, un aviso revisado sale también a la hora de la revisión (TALLER_REVISADO_*). */
    public static List<EventoRastro> unir(List<MovilNsys> lotes, List<EventoNsys> nsys, List<TrabajoTaller> trabajos,
                                          List<CambioTaller> cambios, List<AvisoTaller> avisos) {
        Correcciones correcciones = Correcciones.de(lotes, nsys);
        Set<String> lotesConVentaConHora = nsys.stream().filter(e -> e.tipo() == TipoEvento.VENTA && !e.soloDia())
                .map(EventoNsys::batch).collect(Collectors.toSet());
        // Sobre la lista entera, antes de quitar la venta del esqueleto: así cada tipo sigue con su evento. Con la cadena de
        // cada corrección como un solo lote (0.9.21).
        List<String> revisiones = Revision.tipos(correcciones.paraRevision(nsys));
        List<EventoRastro> todos = new ArrayList<>();
        for (int i = 0; i < nsys.size(); i++) {
            EventoNsys e = nsys.get(i);
            if (e.tipo() == TipoEvento.VENTA && e.soloDia() && lotesConVentaConHora.contains(e.batch())) continue;
            if (correcciones.sustituido(e)) continue;
            todos.add(new EventoRastro(e.fecha(), e.soloDia(), "NSYS", e.tipo().name(), e.batch(), e.de(), e.a(), e.aNsys(),
                    null, null, null, revisiones.get(i), null, null, null, null));
        }
        for (Correcciones.Correccion k : correcciones.todas()) {
            Correcciones.Cambio cambio = correcciones.cambio(k);
            todos.add(new EventoRastro(k.fecha(), false, "NSYS", "CORRECCION", k.lote(), cambio.de(), cambio.a(), null,
                    null, null, null, null, k.loteAnterior(), null, null, null));
        }
        for (TrabajoTaller t : trabajos) {
            boolean asignacion = t.idRep().startsWith("A");
            LocalDateTime fecha = asignacion || t.terminado() == null ? t.asignado() : t.terminado();
            if (fecha == null) continue;
            todos.add(new EventoRastro(fecha, false, "ERP", asignacion ? "TRABAJO_ASIGNADO" : "TRABAJO_TERMINADO",
                    null, null, null, null, t.idRep(), t.tecnico(), categoria(t.idRep()), null, null, null, null, null));
        }
        // 0.9.21, pieza 2: los avisos van antes que los cambios en la lista para que, a la misma hora (una edición: se
        // deshace lo suyo y se aplica la pieza nueva en el mismo segundo), el cambio salga primero.
        for (AvisoTaller a : avisos) {
            todos.add(deAviso(a));
            if (a.revisadoEn() != null) todos.add(deRevisado(a));
        }
        for (CambioTaller c : cambios) {
            todos.add(deTaller(c.fecha(), c.estado() == CambioTaller.Estado.QUITADO ? "TALLER_VUELTA" : "TALLER_CAMBIO", c,
                    c.valorAntes(), c.valor()));
            if (c.cerradoEn() == null) continue;
            if (c.estado() == CambioTaller.Estado.PASADO)
                todos.add(deTaller(c.cerradoEn(), "TALLER_PASADO", c, c.valor(), c.valorCierre()));
            if (c.estado() == CambioTaller.Estado.MANDA_NSYS)
                todos.add(deTaller(c.cerradoEn(), "TALLER_MANDA_NSYS", c, c.valor(), c.valorCierre()));
        }
        // Del más reciente al más antiguo. A la misma hora, primero lo último apuntado: los eventos de NSYS llegan por
        // orden de alta (ID_EVENTO) y los del taller van detrás, así que un trabajo terminado sale antes que el cambio de
        // NSYS de esa misma exportación, y la venta del esqueleto antes que su entrada.
        List<Integer> orden = new ArrayList<>();
        for (int i = 0; i < todos.size(); i++) orden.add(i);
        orden.sort(Comparator.comparing((Integer i) -> todos.get(i).fecha()).thenComparing(i -> i).reversed());
        return orden.stream().map(todos::get).toList();
    }

    private static EventoRastro deTaller(LocalDateTime fecha, String tipo, CambioTaller c, String de, String a) {
        return new EventoRastro(fecha, false, "TALLER", tipo, null, de, a, null, c.idRep(), c.usuario(), null, null, null,
                c.dato().name(), c.origen(), c.motivo());
    }

    /** «TALLER_» + el tipo del aviso (+ «_BORRADA» o «_EDITADA»). {@code tecnico}: quien guardó; {@code motivo}: la pieza. */
    private static EventoRastro deAviso(AvisoTaller a) {
        String tipo = "TALLER_" + a.tipo().name() + (a.causa() == null ? "" : "_" + a.causa().name());
        return new EventoRastro(a.fecha(), false, "TALLER", tipo, null, a.de(), a.a(), null, a.idRep(), a.usuario(), null, null,
                null, a.dato().name(), CambioTaller.REPARACION, a.motivo());
    }

    /** «TALLER_REVISADO_» + el tipo del aviso (0.9.22): alguien lo dio por bueno desde «Revisar a mano», a la hora de la
     *  revisión. {@code tecnico}: quién lo revisó; el resto, como el aviso. Una batería recordada que usó el ERP (pieza 4 §5.3: un
     *  aviso de grado revisado sin quién) es «TALLER_BATERIA_USADA», con el grado que quedó en {@code a}. */
    private static EventoRastro deRevisado(AvisoTaller a) {
        boolean bateriaUsada = a.tipo() == AvisoTaller.Tipo.SIN_GRADO && a.revisadoPor() == null;
        return new EventoRastro(a.revisadoEn(), false, "TALLER", bateriaUsada ? "TALLER_BATERIA_USADA" : "TALLER_REVISADO_" + a.tipo().name(),
                null, a.de(), a.a(), null, a.idRep(), a.revisadoPor(), null, null, null, a.dato().name(),
                bateriaUsada ? CambioTaller.REPARACION : CambioTaller.MANO, a.motivo());
    }

    /** Asignaciones A/AG/AP y trabajos hechos R/G/P. */
    static String categoria(String idRep) {
        if (idRep.startsWith("AG") || idRep.startsWith("G")) return "GLASS";
        if (idRep.startsWith("AP") || idRep.startsWith("P")) return "PULIDO";
        return "REPARACION";
    }
}
