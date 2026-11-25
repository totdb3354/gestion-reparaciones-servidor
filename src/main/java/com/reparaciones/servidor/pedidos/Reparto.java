package com.reparaciones.servidor.pedidos;

import com.reparaciones.servidor.nsys.EstadoMovil;
import com.reparaciones.servidor.nsys.LoQueVale;
import com.reparaciones.servidor.nsys.MovilNsys;
import com.reparaciones.servidor.nsys.Normalizacion;
import com.reparaciones.servidor.nsys.NsysDAO;
import com.reparaciones.servidor.nsys.SkuNa;
import com.reparaciones.servidor.nsys.TrabajoAbierto;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/** Qué teléfono reserva cada fila que espera IMEI (spec reserva de IMEI §2-§4): el orden de las filas, los teléfonos que se
 *  pueden reservar y el puesto de cada uno según el canal (tablas 4.1 y 4.2), y dentro del puesto la caja, el grado y la
 *  antigüedad (4.3). Una reserva no cambia de fila: aquí solo entran las filas que esperan. Es puro. */
public final class Reparto {

    /** Una fila que espera teléfono. */
    public record Fila(int id, Canal canal, String pais, LocalDate fechaPedido, Aceptacion aceptacion) {}

    /** Lo que sale: la reserva de una fila, con su puesto y el porqué («Puesto 1 · Caja · Listos · A-»), o sin stock (imei,
     *  puesto y porqué null). */
    public record Resultado(int idFila, String imei, Integer puesto, String porque) {}

    private record Candidato(MovilNsys movil, int puesto, int caja, int grado, String porque) {}

    private record Elegible(MovilNsys movil, LoQueVale.Valor valor, String clave, boolean conTrabajo) {}

    /** El grado que cuenta de un móvil, ya partido: N/A, Ms, la letra (A-, A, A+, B, C, ID…, en mayúsculas) y la batería. */
    private record Grado(boolean na, boolean naPorCaja, boolean ms, String letra, boolean bateria) {
        boolean familiaA() {
            return !na && (letra.equals("A-") || letra.equals("A") || letra.equals("A+"));
        }

        boolean bOC() {
            return !na && (letra.equals("B") || letra.equals("C"));
        }

        /** ID en cualquier caja (también en Devolución o sin caja, donde cuenta como N/A). */
        boolean id() {
            return letra.equals("ID");
        }
    }

    private static final String DEVOLUCION = "Funcionalidad pendiente";

    private Reparto() {}

    /** Puro. filas: las que esperan (en cualquier orden: aquí se ordenan por §2). stock: móviles en la empresa. ocupados: claves de
     *  cruce ({@link NsysDAO#claveCruce}) de IMEI puestos o reservados en otras filas. Devuelve un Resultado por fila, en el orden
     *  de reparto. */
    public static List<Resultado> de(List<Fila> filas, List<MovilNsys> stock, Function<MovilNsys, LoQueVale.Valor> vale,
                                     Map<String, List<TrabajoAbierto>> trabajos, Set<String> ocupados) {
        List<Elegible> elegibles = new ArrayList<>();
        for (MovilNsys m : stock) {
            LoQueVale.Valor v = vale.apply(m);
            if (m.estado() == null || m.estado().conGrado(v.grado()) != EstadoMovil.STOCK) continue;
            String clave = NsysDAO.claveCruce(m.imeiSn());
            if (ocupados.contains(clave)) continue;
            List<TrabajoAbierto> t = trabajos.get(clave);
            elegibles.add(new Elegible(m, v, clave, t != null && !t.isEmpty()));
        }
        // Los elegibles por modelo y capacidad (como los compara mismoModelo): cada fila solo mira los de lo que pide.
        Map<String, List<Elegible>> porModelo = new HashMap<>();
        for (Elegible e : elegibles) {
            String k = claveModelo(e.movil().modeloMaestro(), e.movil().capacidad());
            if (k != null) porModelo.computeIfAbsent(k, x -> new ArrayList<>()).add(e);
        }
        Set<String> usados = new HashSet<>();
        List<Resultado> resultados = new ArrayList<>();
        for (Fila f : filas.stream().sorted(ORDEN_FILAS).toList()) {
            Candidato mejor = null;
            Elegible deMejor = null;
            // Lo que pide la fila, una sola vez (no por cada teléfono).
            Atributos pide = f.aceptacion() == null || f.canal() == null || !sirve(f.aceptacion()) ? null : f.aceptacion().pide();
            String k = pide == null ? null : claveModelo(pide.modelo(), pide.capacidad());
            for (Elegible e : k == null ? List.<Elegible>of() : porModelo.getOrDefault(k, List.of())) {
                if (usados.contains(e.clave())) continue;
                Candidato c = candidato(f, pide, e);
                if (c != null && (mejor == null || ORDEN_CANDIDATOS.compare(c, mejor) < 0)) {
                    mejor = c;
                    deMejor = e;
                }
            }
            if (mejor == null) {
                resultados.add(new Resultado(f.id(), null, null, null));
                continue;
            }
            usados.add(deMejor.clave());
            resultados.add(new Resultado(f.id(), mejor.movil().imeiSn(), mejor.puesto(), mejor.porque()));
        }
        return resultados;
    }

    /** §2: canal (Amazon y Amazon Stockroom → Distribuidores → Particulares → BackiPhone), fecha del pedido (sin fecha, al
     *  final del canal) e id. */
    private static final Comparator<Fila> ORDEN_FILAS = Comparator.<Fila>comparingInt(f -> rangoCanal(f.canal()))
            .thenComparing(Fila::fechaPedido, Comparator.nullsLast(Comparator.<LocalDate>naturalOrder()))
            .thenComparingInt(Fila::id);

    /** §4.3: puesto, caja, grado, recepción más antigua (sin fecha, al final) e IMEI. */
    private static final Comparator<Candidato> ORDEN_CANDIDATOS = Comparator.comparingInt(Candidato::puesto)
            .thenComparingInt(Candidato::caja)
            .thenComparingInt(Candidato::grado)
            .thenComparing(c -> c.movil().receivingDate(), Comparator.nullsLast(Comparator.<LocalDate>naturalOrder()))
            .thenComparing(c -> c.movil().imeiSn(), Comparator.nullsLast(Comparator.<String>naturalOrder()));

    private static int rangoCanal(Canal c) {
        if (c == null) return 9;
        return switch (c) {
            case AMAZON, AMAZON_STOCKROOM -> 1;
            case DISTRIBUIDORES -> 2;
            case PARTICULARES -> 3;
            case BACKIPHONE -> 4;
        };
    }

    private static Candidato candidato(Fila f, Atributos pide, Elegible e) {
        Integer puesto = f.canal().esAmazon() ? puestoAmazon(f, pide, e.movil(), e.valor(), e.conTrabajo())
                : puestoWeb(f, pide, e.movil(), e.valor(), e.conTrabajo());
        if (puesto == null) return null;
        Grado g = grado(e.movil(), e.valor());
        Caja caja = caja(e.movil());
        return new Candidato(e.movil(), puesto, caja.rango(), rangoGrado(g), porque(puesto, caja, g, e.valor()));
    }

    /** Tabla 4.1 (pedido con SIM física; con un pedido eSIM, el inverso exacto: «mismo formato» es el del pedido). null si el
     *  móvil no vale para la fila. Con un trabajo del taller abierto, solo el puesto 1 y con su SKU entre los aceptados. */
    static Integer puestoAmazon(Fila f, MovilNsys m, LoQueVale.Valor v, boolean conTrabajo) {
        Aceptacion ac = f.aceptacion();
        return sirve(ac) ? puestoAmazon(f, ac.pide(), m, v, conTrabajo) : null;
    }

    private static Integer puestoAmazon(Fila f, Atributos pide, MovilNsys m, LoQueVale.Valor v, boolean conTrabajo) {
        Aceptacion ac = f.aceptacion();
        if (!mismoModelo(pide, m)) return null;
        Grado g = grado(m, v);
        if (g.id()) return null;
        boolean mismoFormato = m.esEsim() == pide.esEsim();
        boolean mismoColor = igualDato(v.color(), pide.color());
        Integer puesto;
        // Ms (también en Devolución o sin caja): nunca se reserva (Imad 25/11). Es una decisión excepcional de una persona: la fila
        // se queda «sin stock» y quien la mira puede picar un Ms a mano (el recomendador los pone al final).
        if (g.ms()) puesto = null;
        else {
            boolean encajaONa = g.na() || encaja(ac, v);
            if (mismoFormato && mismoColor && encajaONa) puesto = 1;
            else if (mismoFormato && g.bOC()) puesto = 2;
            else if (!mismoFormato && mismoColor && encajaONa) puesto = 3;
            else if (!mismoFormato && g.bOC()) puesto = 4;
            else if (mismoFormato && !mismoColor && g.na()) puesto = 5;
            else if (!mismoFormato && !mismoColor && g.na()) puesto = 6;
            else if (mismoFormato && !mismoColor && g.familiaA()) puesto = 7;
            else if (!mismoFormato && !mismoColor && g.familiaA()) puesto = 8;
            else puesto = null;
        }
        return conTrabajo(puesto, conTrabajo, ac, v);
    }

    /** Tabla 4.2: el SKU exacto del pedido (puesto 1) o, solo si el pedido es A o A100, un B o C (puesto 2); siempre del mismo
     *  formato y color, nunca Ms. Un N/A por la caja (Devolución, sin caja) no vale como exacto: su SKU es el de un grado que
     *  aún no está revisado. Con un trabajo del taller abierto, solo el puesto 1. */
    static Integer puestoWeb(Fila f, MovilNsys m, LoQueVale.Valor v, boolean conTrabajo) {
        Aceptacion ac = f.aceptacion();
        return sirve(ac) ? puestoWeb(f, ac.pide(), m, v, conTrabajo) : null;
    }

    private static Integer puestoWeb(Fila f, Atributos pide, MovilNsys m, LoQueVale.Valor v, boolean conTrabajo) {
        Aceptacion ac = f.aceptacion();
        if (!mismoModelo(pide, m)) return null;
        Grado g = grado(m, v);
        if (g.id() || g.ms() || g.naPorCaja() || m.esEsim() != pide.esEsim() || !igualDato(v.color(), pide.color())) return null;
        Integer puesto;
        if (aceptaSku(ac, v)) puesto = 1;
        else if (pedidoA(pide.grado()) && g.bOC()) puesto = 2;
        else puesto = null;
        return conTrabajo(puesto, conTrabajo, ac, v);
    }

    private static Integer conTrabajo(Integer puesto, boolean conTrabajo, Aceptacion ac, LoQueVale.Valor v) {
        if (puesto == null || !conTrabajo) return puesto;
        return puesto == 1 && aceptaSku(ac, v) ? puesto : null;
    }

    /** Una aceptación con la que se puede repartir: sin productos no hay qué pide. */
    private static boolean sirve(Aceptacion ac) {
        return ac != null && !ac.productos().isEmpty();
    }

    /** ¿Su SKU que vale es uno de los que acepta el pedido? */
    private static boolean aceptaSku(Aceptacion ac, LoQueVale.Valor v) {
        return v.sku() != null && ac.acepta(v.sku());
    }

    /** «Encaja tal cual» en el abanico del maestro: su SKU, o su color y grado; si el maestro no trae el grado con la batería
     *  («A-100»), el grado sin ella. aceptaColorYGrado no mira el formato: sirve también para el puesto 3. */
    private static boolean encaja(Aceptacion ac, LoQueVale.Valor v) {
        String grade = v.grade() == null ? null : v.grade().strip();
        return aceptaSku(ac, v) || ac.aceptaColorYGrado(v.color(), grade)
                || ac.aceptaColorYGrado(v.color(), Normalizacion.gradoSinBateria(grade));
    }

    private static boolean pedidoA(String grado) {
        String g = grado == null ? "" : grado.strip().toUpperCase(Locale.ROOT);
        return g.equals("A") || g.equals("A100");
    }

    private static boolean mismoModelo(Atributos pide, MovilNsys m) {
        return igualDato(pide.modelo(), m.modeloMaestro()) && igualDato(pide.capacidad(), m.capacidad());
    }

    /** El grado que cuenta (§3): N/A si NSYS no lo sabe («n/a» o en blanco) o si el móvil está en Devolución o sin caja. El Ms y
     *  la letra (el ID) salen siempre del grado de verdad: la caja no los cambia. */
    private static Grado grado(MovilNsys m, LoQueVale.Valor v) {
        String caja = Normalizacion.caja(m.location());
        boolean naPorCaja = caja.isEmpty() || caja.equals(DEVOLUCION);
        if (SkuNa.esNa(v.grade())) return new Grado(true, naPorCaja, false, "N/A", false);
        String g = v.grade().strip();
        boolean ms = g.length() >= 2 && g.substring(0, 2).equalsIgnoreCase("Ms");
        if (ms) g = g.substring(2);
        boolean bateria = Normalizacion.bateriaNueva(g);
        String letra = Normalizacion.gradoSinBateria(g).toUpperCase(Locale.ROOT);
        return new Grado(naPorCaja, naPorCaja, ms, letra, bateria);
    }

    /** §4.3 paso 2: A- → A-100 → A / A+ → A100 / A+100 → N/A; B → C (con la batería, detrás de su letra). Con Ms, el mismo
     *  orden sin el «Ms». */
    private static int rangoGrado(Grado g) {
        if (g.na()) return 50;
        int base = switch (g.letra()) {
            case "A-", "B" -> 10;
            case "C" -> 20;
            case "A", "A+" -> 30;
            default -> 90;
        };
        if (!g.bateria() || base == 90) return base;
        return g.familiaA() ? base + 10 : base + 5;
    }

    /** §4.3 paso 1: la caja de NSYS y cómo se llama en el porqué (como la píldora de la web). Otra caja que cuente como stock
     *  (hoy no hay) va detrás de todas, con su nombre de NSYS. */
    private record Caja(int rango, String nombre) {}

    private static Caja caja(MovilNsys m) {
        String location = Normalizacion.caja(m.location());
        return switch (location) {
            case "CAJA LISTOS" -> new Caja(1, "Caja · Listos");
            case "CAJA REP. OFICINA" -> new Caja(2, "Caja · Reparación");
            case "C.R. Pantalla Rota" -> new Caja(2, "Caja · Pantalla rota");
            case "CAJA PULIR" -> new Caja(3, "Caja · Pulir");
            case "CAJA GLASS" -> new Caja(4, "Caja · Glass");
            case DEVOLUCION -> new Caja(5, "Devolución");
            case "" -> new Caja(5, "En revisión");
            default -> new Caja(6, location);
        };
    }

    /** «Puesto 1 · Caja · Listos · A-»; si cuenta como N/A por la caja, «… · N/A (es A)». */
    private static String porque(int puesto, Caja caja, Grado g, LoQueVale.Valor v) {
        String grade = v.grade() == null || v.grade().isBlank() ? "N/A" : v.grade().strip();
        String grado = g.naPorCaja() && !SkuNa.esNa(v.grade()) ? "N/A (es " + grade + ")" : g.na() ? "N/A" : grade;
        return "Puesto " + puesto + " · " + caja.nombre() + " · " + grado;
    }

    /** Clave de agrupar por modelo y capacidad, normalizada como {@link #igualDato}; null si falta alguno (no se sabe). */
    private static String claveModelo(String modelo, String capacidad) {
        if (modelo == null || modelo.isBlank() || capacidad == null || capacidad.isBlank()) return null;
        return modelo.strip().toLowerCase(Locale.ROOT) + "|" + capacidad.strip().toLowerCase(Locale.ROOT);
    }

    /** Mismo color, modelo o capacidad: sin espacios a los lados ni mayúsculas. En blanco en cualquiera de los dos lados no es el
     *  mismo (no se sabe). */
    private static boolean igualDato(String a, String b) {
        return a != null && b != null && !a.isBlank() && a.strip().equalsIgnoreCase(b.strip());
    }
}
