package com.reparaciones.servidor.nsys;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** «Revisado» (spec 0.9.18 §5.1, con la decisión del usuario del 19/10). Están pendientes de revisar los móviles en
 *  «sin caja» (la ubicación «En revisión») y en «Funcionalidad pendiente» (la ubicación «Devolución»): un lote se revisa al
 *  pasar de una de las dos a una caja de verdad, que es cualquiera menos esas dos y «Pedidos realizados» (una venta sin
 *  revisar). Si el lote llegó a «Devolución» (o sale de ella), esa revisión es la de una devolución; si no, la primera es la
 *  de entrada. Los pasos entre las dos pendientes no son revisión. La hora es la de la exportación en que se vio el
 *  cambio, y solo hay pasos intermedios desde dic. 2023. */
public final class Revision {

    public static final String ENTRADA = "ENTRADA";
    public static final String DEVOLUCION = "DEVOLUCION";
    static final String EN_REVISION = "En revisión";
    static final String SALIDA_DEVOLUCION = "Devolución";
    static final String CAJA_PEDIDOS = "Pedidos realizados";

    /** La última revisión de un lote: su hora y si fue la de una devolución. */
    public record Ultima(LocalDateTime fecha, boolean devolucion) {}

    private Revision() {}

    /** Qué revisión es cada evento, en el orden de la lista (FECHA, ID_EVENTO): ENTRADA, DEVOLUCION o null. Por lote: un
     *  evento hacia «Devolución» deja una devolución por revisar; el paso siguiente de una ubicación pendiente a una caja de
     *  verdad es DEVOLUCION (también si sale de «Devolución»). Si no hay devolución por medio, ENTRADA solo es el primero de
     *  esos pasos del lote; los demás, null. */
    public static List<String> tipos(List<EventoNsys> eventos) {
        Set<String> conEntrada = new HashSet<>();
        Set<String> conDevolucionPendiente = new HashSet<>();
        List<String> tipos = new ArrayList<>(eventos.size());
        for (EventoNsys e : eventos) {
            String tipo = null;
            if (llevaUbicacion(e) && SALIDA_DEVOLUCION.equals(e.a())) {
                conDevolucionPendiente.add(e.batch());
            } else if (e.tipo() == TipoEvento.UBICACION && pendiente(e.de()) && esCaja(e)) {
                // remove primero: la devolución pendiente se gasta aunque el paso salga de «Devolución».
                boolean devolucion = conDevolucionPendiente.remove(e.batch()) || SALIDA_DEVOLUCION.equals(e.de());
                if (devolucion) tipo = DEVOLUCION;
                else if (conEntrada.add(e.batch())) tipo = ENTRADA;
            }
            tipos.add(tipo);
        }
        return tipos;
    }

    /** La última revisión del lote, de entrada o de devolución; null si no la hay. */
    public static Ultima ultima(List<EventoNsys> eventos, String lote) {
        List<String> tipos = tipos(eventos);
        Ultima ultima = null;
        for (int i = 0; i < eventos.size(); i++) {
            if (tipos.get(i) != null && Objects.equals(eventos.get(i).batch(), lote))
                ultima = new Ultima(eventos.get(i).fecha(), DEVOLUCION.equals(tipos.get(i)));
        }
        return ultima;
    }

    /** Si el móvil está ahora pendiente de revisar («En revisión» o «Devolución»), desde cuándo: el principio del periodo
     *  pendiente actual del lote, el último evento que lo llevó a una ubicación pendiente desde una que no lo es (o su
     *  entrada); un paso entre las dos pendientes no lo cambia. La vuelta del lote desde vendido o de baja también empieza
     *  un periodo: si vuelve sin caja, la caja de NSYS no cambia y no hay evento de ubicación (0.9.18.2). Si no lo hay, la
     *  recepción del lote. null si no está pendiente o no hay fecha. */
    public static LocalDate pendienteDesde(MovilNsys m, List<EventoNsys> eventos) {
        if (!pendiente(m.ubicacion())) return null;
        LocalDate desde = null;
        for (EventoNsys e : eventos) {
            if (!Objects.equals(e.batch(), m.batch())) continue;
            if (esVuelta(e)) desde = e.fecha().toLocalDate();
            else if (llevaUbicacion(e)) {
                if (!pendiente(e.a())) desde = null;
                else if (e.tipo() == TipoEvento.ENTRADA || !pendiente(e.de())) desde = e.fecha().toLocalDate();
            }
        }
        return desde != null ? desde : m.receivingDate();
    }

    /** La vuelta de un lote a la empresa: una venta deshecha, o un cambio de un Status que no es vivo a uno vivo (una
     *  baja deshecha). */
    private static boolean esVuelta(EventoNsys e) {
        return e.tipo() == TipoEvento.VENTA_DESHECHA
                || e.tipo() == TipoEvento.ESTADO && !EstadoMovil.vivo(e.de()) && EstadoMovil.vivo(e.a());
    }

    /** Las ubicaciones pendientes de revisar: «En revisión» (sin caja) y «Devolución» (Funcionalidad pendiente). */
    static boolean pendiente(String ubicacion) {
        return EN_REVISION.equals(ubicacion) || SALIDA_DEVOLUCION.equals(ubicacion);
    }

    /** Un evento que deja el móvil en una ubicación: un cambio de caja o la entrada del lote. */
    private static boolean llevaUbicacion(EventoNsys e) {
        return e.tipo() == TipoEvento.UBICACION || e.tipo() == TipoEvento.ENTRADA;
    }

    /** Una caja de verdad, destino de una revisión: ni pendiente ni «Pedidos realizados». Un destino que no se trae
     *  ({@code a} null, p. ej. «Desguace») cuenta como caja. */
    private static boolean esCaja(EventoNsys e) {
        return !pendiente(e.a()) && !CAJA_PEDIDOS.equals(e.aNsys());
    }
}
