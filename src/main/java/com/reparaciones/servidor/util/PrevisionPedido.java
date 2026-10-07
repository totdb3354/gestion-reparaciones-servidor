package com.reparaciones.servidor.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Previsión de pedidos de piezas (spec 0.9.5 §3.1). El consumo diario es una media ponderada de tres tramos de 30
 * días hacia atrás (lo reciente pesa más) y el pedido cubre 15 o 30 días con el stock mínimo como suelo:
 * {@code pedir = máx(0, ⌈máx(mínimo, consumo/día × días) − (stock + en camino)⌉)}.
 *
 * <p>Todo se calcula en enteros escalados por 3000 (100 de los porcentajes × 30 días del tramo): en coma flotante,
 * 0,1 × 30 da 3,0000000000000004 y el redondeo hacia arriba pediría una pieza de más.
 */
public final class PrevisionPedido {

    public static final int DIAS_TRAMO = 30;
    public static final int DIAS_VENTANA = 3 * DIAS_TRAMO;
    private static final long ESCALA = 100L * DIAS_TRAMO;

    /** Pesos en % de los tramos 1-30, 31-60 y 61-90 días. Válidos si son enteros de 0 a 100 que suman 100. */
    public record Pesos(int p1, int p2, int p3) {
        public static final Pesos POR_DEFECTO = new Pesos(50, 30, 20);

        public boolean validos() {
            return enRango(p1) && enRango(p2) && enRango(p3) && p1 + p2 + p3 == 100;
        }

        private static boolean enRango(int p) { return p >= 0 && p <= 100; }

        /** "50/30/20", para el registro de actividad. */
        public String texto() { return p1 + "/" + p2 + "/" + p3; }
    }

    /** Unidades consumidas en cada tramo: días 1-30, 31-60 y 61-90 antes de hoy. */
    public record Tramos(int t1, int t2, int t3) {
        public static final Tramos CERO = new Tramos(0, 0, 0);
    }

    /** Unidades de un master consumidas en un día (una fila de la consulta de consumo). */
    public record ConsumoDia(int idMaster, LocalDate dia, int unidades) {}

    public record Resultado(double consumoDiario, int pedir15, int pedir30) {}

    private PrevisionPedido() {}

    /** Reparte el consumo de cada master en sus tres tramos. Hoy no cuenta (día a medias) ni lo de hace más de 90 días. */
    public static Map<Integer, Tramos> agrupar(List<ConsumoDia> consumos, LocalDate hoy) {
        Map<Integer, int[]> acumulado = new HashMap<>();
        for (ConsumoDia c : consumos) {
            long hace = ChronoUnit.DAYS.between(c.dia(), hoy);
            if (hace < 1 || hace > DIAS_VENTANA) continue;
            int tramo = (int) ((hace - 1) / DIAS_TRAMO);
            acumulado.computeIfAbsent(c.idMaster(), k -> new int[3])[tramo] += c.unidades();
        }
        Map<Integer, Tramos> resultado = new HashMap<>();
        acumulado.forEach((id, t) -> resultado.put(id, new Tramos(t[0], t[1], t[2])));
        return resultado;
    }

    public static Resultado calcular(Tramos t, Pesos p, int minimo, int stock, int enCamino) {
        long ponderado = (long) p.p1() * t.t1() + (long) p.p2() * t.t2() + (long) p.p3() * t.t3();
        double consumoDiario = BigDecimal.valueOf(ponderado)
                .divide(BigDecimal.valueOf(ESCALA), 2, RoundingMode.HALF_UP).doubleValue();
        int disponible = stock + enCamino;
        return new Resultado(consumoDiario, pedir(ponderado, 15, minimo, disponible), pedir(ponderado, 30, minimo, disponible));
    }

    private static int pedir(long ponderado, int dias, int minimo, int disponible) {
        long objetivo = Math.max((long) minimo * ESCALA, ponderado * dias);
        long falta = objetivo - (long) disponible * ESCALA;
        if (falta <= 0) return 0;
        return (int) ((falta + ESCALA - 1) / ESCALA);
    }
}
