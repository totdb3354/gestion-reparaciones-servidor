package com.reparaciones.servidor.util;

import com.reparaciones.servidor.model.PuntoEstadisticaPuntos;

import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;

/**
 * Cálculo puro de puntos de dificultad (spec 2026-09-01-estadisticas-puntos-design §2).
 * R/G = suma de sus piezas, cada fila UNA vez (la cantidad no multiplica — ajuste smoke
 * 2026-09-03); sin piezas → 'otro'; P = 'pulido' fijo.
 */
public final class PuntosCalculo {

    private PuntosCalculo() {}

    /** Orden de matching de prefijos SKU → clave de Dificultad_puntos ('g' SIEMPRE el último). */
    private static final LinkedHashMap<String, String> PREFIJO_CLAVE = new LinkedHashMap<>();
    static {
        PREFIJO_CLAVE.put("bat", "bateria");
        PREFIJO_CLAVE.put("cha", "chasis");
        PREFIJO_CLAVE.put("cam", "camara");
        PREFIJO_CLAVE.put("lcd", "pantalla");
        PREFIJO_CLAVE.put("mc",  "marco");
        PREFIJO_CLAVE.put("g",   "glass");
    }

    public record Pieza(String tipo, int cantidad) {}

    /** Fila cruda de la query (una por pieza; reparación sin piezas = una fila con tipoPieza null). */
    public record FilaPuntos(String tecnico, LocalDate fecha, String idRep, String imei,
                             String tipoPieza, Integer cantidad) {}

    public static String claveDeTipo(String tipo) {
        if (tipo == null) return "otro";
        String lower = tipo.toLowerCase();
        for (var e : PREFIJO_CLAVE.entrySet())
            if (lower.startsWith(e.getKey())) return e.getValue();
        return "otro";
    }

    private static double valor(Map<String, Double> valores, String clave) {
        return valores.getOrDefault(clave, 0.0);
    }

    public static double puntosDeReparacion(String idRep, List<Pieza> piezas, Map<String, Double> valores) {
        if (idRep.startsWith("P")) return valor(valores, "pulido");
        if (piezas.isEmpty())      return valor(valores, "otro");
        return piezas.stream()
                // Cada fila de pieza puntúa UNA vez, ignorando CANTIDAD (decisión smoke
                // 2026-09-03): cantidad>1 suele ser pieza rota o venida defectuosa, y
                // multiplicar premiaba la rotura. Cubre también las acciones "otro",
                // guardadas con CANTIDAD=0 (stock neutro), que antes puntuaban cero.
                .mapToDouble(p -> valor(valores, claveDeTipo(p.tipo())))
                .sum();
    }

    public static List<PuntoEstadisticaPuntos> agregar(List<FilaPuntos> filas,
                                                       Map<String, Double> valores,
                                                       Function<LocalDate, String> periodoDe) {
        // 1) agrupar filas por reparación (conservando técnico, fecha e IMEI)
        record Rep(String tecnico, LocalDate fecha, String idRep, String imei) {}
        Map<Rep, List<Pieza>> piezasPorRep = new LinkedHashMap<>();
        for (FilaPuntos f : filas) {
            Rep rep = new Rep(f.tecnico(), f.fecha(), f.idRep(), f.imei());
            List<Pieza> lista = piezasPorRep.computeIfAbsent(rep, k -> new ArrayList<>());
            if (f.tipoPieza() != null || f.cantidad() != null)
                lista.add(new Pieza(f.tipoPieza(), f.cantidad() == null ? 1 : f.cantidad()));
        }

        // 2) acumular por técnico + periodo
        record Acum(double[] puntos, int[] contadores, Set<String> imeis) {}  // puntos: total,N,G,P — contadores: nN,nG,nP,nSin
        Map<String, Map<String, Acum>> mapa = new LinkedHashMap<>();
        piezasPorRep.forEach((rep, piezas) -> {
            String periodo = periodoDe.apply(rep.fecha());
            Acum a = mapa.computeIfAbsent(rep.tecnico(), k -> new LinkedHashMap<>())
                         .computeIfAbsent(periodo, k -> new Acum(new double[4], new int[4], new HashSet<>()));
            double pts = puntosDeReparacion(rep.idRep(), piezas, valores);
            a.puntos()[0] += pts;
            char pref = rep.idRep().charAt(0);
            if (pref == 'R') { a.puntos()[1] += pts; a.contadores()[0]++; }
            if (pref == 'G') { a.puntos()[2] += pts; a.contadores()[1]++; }
            if (pref == 'P') { a.puntos()[3] += pts; a.contadores()[2]++; }
            if (pref != 'P' && piezas.isEmpty()) a.contadores()[3]++;
            if (rep.imei() != null) a.imeis().add(rep.imei()); // IMEIs distintos del periodo
        });

        // 3) aplanar y ordenar como el endpoint viejo (periodo, técnico)
        List<PuntoEstadisticaPuntos> out = new ArrayList<>();
        mapa.forEach((tec, periodos) -> periodos.forEach((periodo, a) ->
                out.add(new PuntoEstadisticaPuntos(tec, periodo,
                        a.puntos()[0], a.puntos()[1], a.puntos()[2], a.puntos()[3],
                        a.contadores()[0], a.contadores()[1], a.contadores()[2], a.contadores()[3],
                        a.imeis().size()))));
        out.sort(Comparator.comparing(PuntoEstadisticaPuntos::getPeriodo)
                           .thenComparing(PuntoEstadisticaPuntos::getNombreTecnico));
        return out;
    }
}
