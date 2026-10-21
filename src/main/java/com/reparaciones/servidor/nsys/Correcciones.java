package com.reparaciones.servidor.nsys;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Correcciones de NSYS (spec 0.9.21 «corrección en NSYS» §2). Para corregir un móvil (color, capacidad, producto o grado;
 * también tras una transformación del taller), NSYS da de baja su lote (Removed) y da de alta el IMEI en otro lote. El lote
 * L1 sigue en L2 cuando L1 pasa a Removed con hora el día D y L2, del mismo IMEI, se recibe entre D−{@value #DIAS_ANTES} y
 * D+{@value #DIAS_DESPUES}, no antes que L1; si hay varios, el más cercano a D (empate: el lote mayor). Un L2 sigue a un
 * solo L1 y las correcciones se encadenan: la cadena es un solo móvil. Es puro: se calcula al leer, con los lotes y los
 * eventos de un IMEI.
 */
public final class Correcciones {

    static final int DIAS_ANTES = 7;
    static final int DIAS_DESPUES = 1;
    private static final String REMOVED = "Removed";

    /** Sin lotes ni eventos: cada lote es su propia cadena. */
    public static final Correcciones NINGUNA = de(List.of(), List.of());

    /** Una corrección: el lote dado de baja, el que lo sigue y la hora de la baja. */
    public record Correccion(String loteAnterior, String lote, LocalDateTime fecha) {}

    /** El móvil antes y después de una corrección, como lo enseña el rastro. */
    public record Cambio(String de, String a) {}

    private final Map<String, MovilNsys> lotes = new HashMap<>();
    private final List<Correccion> todas = new ArrayList<>();
    private final Map<String, Correccion> porLoteNuevo = new HashMap<>();
    private final Map<String, Correccion> porLoteViejo = new HashMap<>();

    private Correcciones() {}

    /** Las correcciones de un IMEI con sus lotes (en cualquier orden) y sus eventos (basta con las bajas). */
    public static Correcciones de(List<MovilNsys> lotes, List<EventoNsys> eventos) {
        Correcciones c = new Correcciones();
        for (MovilNsys m : lotes) c.lotes.putIfAbsent(m.batch(), m);
        // La última baja con hora de cada lote: un lote puede volver y darse de baja otra vez.
        Map<String, EventoNsys> bajas = new HashMap<>();
        for (EventoNsys e : eventos) if (esBaja(e) && c.lotes.containsKey(e.batch())) bajas.put(e.batch(), e);
        List<EventoNsys> porFecha = new ArrayList<>(bajas.values());
        porFecha.sort(Comparator.comparing(EventoNsys::fecha).thenComparing(EventoNsys::batch));
        for (EventoNsys b : porFecha) {
            MovilNsys viejo = c.lotes.get(b.batch());
            LocalDate dia = b.fecha().toLocalDate();
            MovilNsys elegido = null;
            for (MovilNsys m : c.lotes.values()) {
                if (!c.puedeSeguir(m, viejo, dia)) continue;
                if (elegido == null || masCercano(m, elegido, dia)) elegido = m;
            }
            if (elegido == null) continue;
            Correccion k = new Correccion(viejo.batch(), elegido.batch(), b.fecha());
            c.todas.add(k);
            c.porLoteNuevo.put(k.lote(), k);
            c.porLoteViejo.put(k.loteAnterior(), k);
        }
        return c;
    }

    /** ¿Puede {@code m} ser el lote que sigue a {@code viejo}, dado de baja el día {@code dia}? */
    private boolean puedeSeguir(MovilNsys m, MovilNsys viejo, LocalDate dia) {
        if (m.batch().equals(viejo.batch()) || porLoteNuevo.containsKey(m.batch()) || m.receivingDate() == null) return false;
        if (m.receivingDate().isBefore(dia.minusDays(DIAS_ANTES)) || m.receivingDate().isAfter(dia.plusDays(DIAS_DESPUES)))
            return false;
        if (viejo.receivingDate() != null && m.receivingDate().isBefore(viejo.receivingDate())) return false;
        // Que no cierre un círculo: m no puede ser un lote anterior de la cadena del viejo.
        return !anterioresDe(viejo.batch()).contains(m.batch());
    }

    private List<String> anterioresDe(String lote) {
        List<String> anteriores = new ArrayList<>();
        for (Correccion k = porLoteNuevo.get(lote); k != null; k = porLoteNuevo.get(k.loteAnterior()))
            anteriores.add(k.loteAnterior());
        return anteriores;
    }

    private static boolean masCercano(MovilNsys m, MovilNsys otro, LocalDate dia) {
        long a = Math.abs(ChronoUnit.DAYS.between(dia, m.receivingDate()));
        long b = Math.abs(ChronoUnit.DAYS.between(dia, otro.receivingDate()));
        return a != b ? a < b : m.batch().compareTo(otro.batch()) > 0;
    }

    /** Una baja con hora: un cambio de estado a Removed que no es del esqueleto. */
    static boolean esBaja(EventoNsys e) {
        return e.tipo() == TipoEvento.ESTADO && REMOVED.equals(e.a()) && !e.soloDia();
    }

    /** Las correcciones, por la hora de la baja. */
    public List<Correccion> todas() {
        return List.copyOf(todas);
    }

    /** El primer lote de la cadena de {@code lote}: el de la entrada de verdad. */
    public String primerLote(String lote) {
        String l = lote;
        for (Correccion k = porLoteNuevo.get(l); k != null; k = porLoteNuevo.get(l)) l = k.loteAnterior();
        return l;
    }

    /** El último lote de la cadena de {@code lote}: el que la representa. */
    public String ultimoLote(String lote) {
        String l = lote;
        for (Correccion k = porLoteViejo.get(l); k != null; k = porLoteViejo.get(l)) l = k.lote();
        return l;
    }

    /** El lote con ese nombre, o null. */
    public MovilNsys lote(String batch) {
        return lotes.get(batch);
    }

    /** La última caja de los lotes anteriores de la cadena (su última ubicación o su ubicación), del más cercano al más
     *  lejano; null si no hay. */
    public String ultimaCajaAnterior(String lote) {
        for (Correccion k = porLoteNuevo.get(lote); k != null; k = porLoteNuevo.get(k.loteAnterior())) {
            MovilNsys m = lotes.get(k.loteAnterior());
            if (m == null) continue;
            if (m.ubicacionUltima() != null) return m.ubicacionUltima();
            if (m.ubicacion() != null) return m.ubicacion();
        }
        return null;
    }

    /** ¿Lo sustituye una corrección? La baja del lote viejo y la entrada y la reentrada del nuevo. */
    public boolean sustituido(EventoNsys e) {
        if (esBaja(e)) {
            Correccion k = porLoteViejo.get(e.batch());
            return k != null && k.fecha().equals(e.fecha());
        }
        return (e.tipo() == TipoEvento.ENTRADA || e.tipo() == TipoEvento.REENTRADA) && porLoteNuevo.containsKey(e.batch());
    }

    /** Los eventos para {@link Revision}, con la misma longitud y orden: los de cada cadena con el nombre de su último
     *  lote y, en lugar de los que sustituye una corrección, un ESTADO vacío que Revision no mira. Salvo la entrada del lote
     *  nuevo en una ubicación pendiente («En revisión», «Devolución»): es un paso desde la última caja del viejo, así la
     *  espera empieza ahí si venía de una caja y sigue si ya estaba pendiente (sin ser una revisión). */
    public List<EventoNsys> paraRevision(List<EventoNsys> eventos) {
        List<EventoNsys> r = new ArrayList<>(eventos.size());
        for (EventoNsys e : eventos) {
            String lote = ultimoLote(e.batch());
            if (!sustituido(e)) r.add(new EventoNsys(e.imeiSn(), lote, e.fecha(), e.soloDia(), e.tipo(), e.de(), e.a(), e.aNsys()));
            else if (e.tipo() == TipoEvento.ENTRADA && Revision.pendiente(e.a()))
                r.add(new EventoNsys(e.imeiSn(), lote, e.fecha(), e.soloDia(), TipoEvento.UBICACION,
                        ultimaCaja(porLoteNuevo.get(e.batch()).loteAnterior()), e.a(), e.aNsys()));
            else r.add(new EventoNsys(e.imeiSn(), lote, e.fecha(), e.soloDia(), TipoEvento.ESTADO, null, null, null));
        }
        return r;
    }

    /** La última caja conocida de un lote (su última ubicación o su ubicación), o null. */
    private String ultimaCaja(String batch) {
        MovilNsys m = lotes.get(batch);
        if (m == null) return null;
        return m.ubicacionUltima() != null ? m.ubicacionUltima() : m.ubicacion();
    }

    /** El móvil antes y después de una corrección: capacidad y color del maestro con el grado de NSYS («512GB Amarillo · A»
     *  → «512GB Negro · A100») si los dos lotes los tienen, son del mismo modelo y eSIM y el texto cambia; si no, el Goods
     *  de NSYS y el grado de los dos, que dicen qué cambió. */
    public Cambio cambio(Correccion k) {
        MovilNsys viejo = lotes.get(k.loteAnterior());
        MovilNsys nuevo = lotes.get(k.lote());
        String de = conMaestro(viejo);
        String a = conMaestro(nuevo);
        boolean maestro = de != null && a != null && !de.equals(a)
                && Objects.equals(viejo.modeloMaestro(), nuevo.modeloMaestro()) && viejo.esEsim() == nuevo.esEsim();
        return maestro ? new Cambio(de, a) : new Cambio(conGoods(viejo), conGoods(nuevo));
    }

    private static String conMaestro(MovilNsys m) {
        return m.capacidad() == null || m.color() == null ? null : m.capacidad() + " " + m.color() + " · " + grado(m);
    }

    private static String conGoods(MovilNsys m) {
        return (m.goods() == null ? "—" : m.goods()) + " · " + grado(m);
    }

    private static String grado(MovilNsys m) {
        return m.grade() == null ? "—" : m.grade();
    }
}
