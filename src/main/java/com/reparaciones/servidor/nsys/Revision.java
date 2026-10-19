package com.reparaciones.servidor.nsys;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** «Revisado» (spec 0.9.18 §5.1). Un lote se revisa al pasar de «sin caja» (la ubicación «En revisión») a una caja que no
 *  sea «Pedidos realizados», que es una venta sin revisar; salir de «Devolución» hacia una caja es la revisión de una
 *  devolución. La hora es la de la exportación en que se vio el cambio, y solo hay pasos intermedios desde dic. 2023. */
public final class Revision {

    public static final String ENTRADA = "ENTRADA";
    public static final String DEVOLUCION = "DEVOLUCION";
    static final String EN_REVISION = "En revisión";
    static final String SALIDA_DEVOLUCION = "Devolución";
    static final String CAJA_PEDIDOS = "Pedidos realizados";

    /** La última revisión de un lote: su hora y si fue la de una devolución. */
    public record Ultima(LocalDateTime fecha, boolean devolucion) {}

    private Revision() {}

    /** Qué revisión es cada evento, en el orden de la lista (FECHA, ID_EVENTO): ENTRADA, DEVOLUCION o null. ENTRADA solo es
     *  el primer cambio de «En revisión» a una caja de cada lote; una devolución puede revisarse varias veces. */
    public static List<String> tipos(List<EventoNsys> eventos) {
        Set<String> lotesRevisados = new HashSet<>();
        List<String> tipos = new ArrayList<>(eventos.size());
        for (EventoNsys e : eventos) {
            String tipo = null;
            if (e.tipo() == TipoEvento.UBICACION && !CAJA_PEDIDOS.equals(e.aNsys())) {
                if (EN_REVISION.equals(e.de())) {
                    if (lotesRevisados.add(e.batch())) tipo = ENTRADA;
                } else if (SALIDA_DEVOLUCION.equals(e.de())) {
                    tipo = DEVOLUCION;
                }
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

    /** Si el móvil está ahora en «En revisión», desde cuándo: el último evento del lote que lo dejó allí (su entrada o un
     *  cambio de caja) o, si no lo hay, la recepción del lote. null si no está pendiente o no hay fecha. */
    public static LocalDate pendienteDesde(MovilNsys m, List<EventoNsys> eventos) {
        if (!EN_REVISION.equals(m.ubicacion())) return null;
        LocalDate desde = null;
        for (EventoNsys e : eventos) {
            boolean loDejaAlli = (e.tipo() == TipoEvento.UBICACION || e.tipo() == TipoEvento.ENTRADA) && EN_REVISION.equals(e.a());
            if (loDejaAlli && Objects.equals(e.batch(), m.batch())) desde = e.fecha().toLocalDate();
        }
        return desde != null ? desde : m.receivingDate();
    }
}
