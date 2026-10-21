package com.reparaciones.servidor.nsys;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/** Qué se deshace al borrar una reparación o al cambiar su pieza (spec 0.9.21 «SKU del taller», pieza 2, §2.3). De cada dato
 *  cuenta el último cambio de esa R…: si es VIGENTE se deshace y vuelve el cambio al que sustituyó, si sigue SUSTITUIDO
 *  (saltando los de reparaciones que ya no existen), o lo de NSYS; si ya no manda (lo sustituyó otro posterior, pasó a NSYS,
 *  mandó NSYS o volvió a NSYS al nacer), solo se avisa. Un SUSTITUIDO al que no apunta ningún cambio ya se deshizo antes (una
 *  edición de la propia reparación) y no cuenta. La misma cuenta para guardar y para la banda del formulario. Es puro. */
public final class PlanDeshacer {

    /** Un cambio que se deshace y el que vuelve a valer (null: lo de NSYS). */
    public record Deshecho(CambioTaller cambio, CambioTaller vuelve) {}

    public record Plan(List<Deshecho> deshechos, List<CambioTaller> sinDeshacer) {

        /** Los vigentes del IMEI tras deshacer: sin los deshechos y con los que vuelven. */
        public List<CambioTaller> vigentes(List<CambioTaller> vigentes) {
            Set<Integer> fuera = deshechos.stream().map(d -> d.cambio().id()).collect(Collectors.toSet());
            List<CambioTaller> lista = new ArrayList<>(vigentes.stream().filter(c -> !fuera.contains(c.id())).toList());
            for (Deshecho d : deshechos) if (d.vuelve() != null) lista.add(d.vuelve());
            return lista;
        }

        public boolean vacio() {
            return deshechos.isEmpty() && sinDeshacer.isEmpty();
        }
    }

    private PlanDeshacer() {}

    /** {@code delImei}: todos los cambios del IMEI (TallerDAO.deImei). {@code reparacionExiste}: si una R… sigue existiendo
     *  (no hace falta que excluya a {@code idRep}: sus cambios anteriores se saltan siempre). */
    public static Plan de(List<CambioTaller> delImei, String idRep, Predicate<String> reparacionExiste) {
        Map<Integer, CambioTaller> porId = new HashMap<>();
        Set<Integer> apuntados = new HashSet<>();
        for (CambioTaller c : delImei) {
            porId.put(c.id(), c);
            if (c.idAnterior() != null) apuntados.add(c.idAnterior());
        }
        List<Deshecho> deshechos = new ArrayList<>();
        List<CambioTaller> sinDeshacer = new ArrayList<>();
        for (DatoTaller dato : DatoTaller.values()) {
            Optional<CambioTaller> ultimo = delImei.stream().filter(c -> c.dato() == dato && idRep.equals(c.idRep()))
                    .max(Comparator.comparingInt(CambioTaller::id));
            if (ultimo.isEmpty()) continue;
            CambioTaller c = ultimo.get();
            if (c.estado() == CambioTaller.Estado.VIGENTE) deshechos.add(new Deshecho(c, vuelve(c, idRep, porId, reparacionExiste)));
            else if (c.estado() != CambioTaller.Estado.SUSTITUIDO || apuntados.contains(c.id())) sinDeshacer.add(c);
        }
        return new Plan(List.copyOf(deshechos), List.copyOf(sinDeshacer));
    }

    /** Se saltan los anteriores de la propia R… (sus ediciones previas) y los de reparaciones que ya no existen. */
    private static CambioTaller vuelve(CambioTaller c, String idRep, Map<Integer, CambioTaller> porId,
                                       Predicate<String> reparacionExiste) {
        CambioTaller a = anterior(c, porId);
        while (a != null && a.idRep() != null && (a.idRep().equals(idRep) || !reparacionExiste.test(a.idRep()))) a = anterior(a, porId);
        return a != null && a.estado() == CambioTaller.Estado.SUSTITUIDO ? a : null;
    }

    private static CambioTaller anterior(CambioTaller c, Map<Integer, CambioTaller> porId) {
        return c.idAnterior() == null ? null : porId.get(c.idAnterior());
    }
}
