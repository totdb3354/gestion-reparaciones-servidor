package com.reparaciones.servidor.pedidos;

import com.reparaciones.servidor.nsys.EstadoMovil;
import com.reparaciones.servidor.nsys.LoQueVale;
import com.reparaciones.servidor.nsys.MovilNsys;
import com.reparaciones.servidor.nsys.NsysDAO;
import com.reparaciones.servidor.nsys.TrabajoAbierto;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/** Los móviles en stock que valen para una fila de pedido, del mejor al peor (spec §5). Es puro. */
public final class Recomendador {

    /** nivel: 1 igual que el pedido, 2 en el taller y acabará igual, 3 hay que transformarlo. reservado: es el IMEI que el ERP
     *  reservó para esta fila (va el primero). */
    public record Candidato(String imei, @Schema(nullable = true) String sku, @Schema(nullable = true) String caja, int nivel,
                            boolean reservado) {
        public Candidato(String imei, String sku, String caja, int nivel) {
            this(imei, sku, caja, nivel, false);
        }
    }

    public static final int TOPE = 5;

    /** ms: el grado del móvil empieza por «Ms» (sin mirar mayúsculas). */
    private record Puntuado(MovilNsys movil, Candidato candidato, boolean ms) {}

    private Recomendador() {}

    /** Sin reserva propia. */
    public static List<Candidato> de(Aceptacion ac, Canal canal, List<MovilNsys> stock, Function<MovilNsys, LoQueVale.Valor> vale,
                                     Map<String, List<TrabajoAbierto>> trabajos, Set<String> yaPuestos) {
        return de(ac, canal, stock, vale, trabajos, yaPuestos, null);
    }

    /** Puro. stock: los móviles en la empresa. yaPuestos: claves de cruce ({@link NsysDAO#claveCruce}) de los IMEI de filas vivas y
     *  de los reservados para otras filas (Imad 25/11: no se proponen). reservadaPropia: clave de cruce del IMEI que el ERP reservó
     *  para esta fila, o null: si está en la empresa sale el primero, marcado, aunque no pase los filtros (la reserva tiene sus
     *  propios criterios: sustitutos eSIM, B/C para el taller…) y aunque el bot ya lo haya pasado a «Pedidos realizados». */
    public static List<Candidato> de(Aceptacion ac, Canal canal, List<MovilNsys> stock, Function<MovilNsys, LoQueVale.Valor> vale,
                                     Map<String, List<TrabajoAbierto>> trabajos, Set<String> yaPuestos, String reservadaPropia) {
        if (ac == null) return List.of();
        List<Puntuado> validos = new ArrayList<>();
        Candidato reservado = null;
        for (MovilNsys m : stock) {
            LoQueVale.Valor v = vale.apply(m);
            String clave = NsysDAO.claveCruce(m.imeiSn());
            if (reservado == null && clave.equals(reservadaPropia) && m.estado() != null) {
                EstadoMovil e = m.estado().conGrado(v.grado());
                if (e == EstadoMovil.STOCK || e == EstadoMovil.DE_PEDIDO) {
                    int nivel = nivel(ac, canal, m, v, trabajos.get(clave));
                    reservado = new Candidato(m.imeiSn(), v.sku(), m.ubicacion(), nivel == 0 ? 3 : nivel, true);
                    continue;
                }
            }
            if (m.estado() == null || m.estado().conGrado(v.grado()) != EstadoMovil.STOCK) continue;
            if (yaPuestos.contains(clave) || clave.equals(reservadaPropia)) continue;
            int nivel = nivel(ac, canal, m, v, trabajos.get(clave));
            if (nivel == 0) continue;
            validos.add(new Puntuado(m, new Candidato(m.imeiSn(), v.sku(), m.ubicacion(), nivel), esMs(v.grade())));
        }
        // Un Ms es una decisión excepcional de una persona (Imad 25/11): se propone, pero detrás de todos los demás, sea cual sea
        // su nivel; entre Ms, el orden de siempre.
        Comparator<Puntuado> orden = Comparator.<Puntuado, Boolean>comparing(Puntuado::ms)
                .thenComparingInt(p -> p.candidato().nivel())
                .thenComparing(p -> p.movil().receivingDate(), Comparator.nullsLast(Comparator.<LocalDate>naturalOrder()))
                .thenComparing(p -> p.movil().imeiSn(), Comparator.nullsLast(Comparator.<String>naturalOrder()));
        List<Candidato> lista = new ArrayList<>();
        if (reservado != null) lista.add(reservado);
        validos.stream().sorted(orden).limit(TOPE).map(Puntuado::candidato).forEach(lista::add);
        return lista;
    }

    /** 0 = no se propone. Una SIM distinta no se propone (antes era MODELO); un n/a hay que transformarlo aunque el color cuadre.
     *  Con un técnico, como antes de la 0.9.46.1: solo si acabará como pide el pedido (su SKU lo acepta), y nunca si es otro
     *  modelo, capacidad o no se vende. */
    private static int nivel(Aceptacion ac, Canal canal, MovilNsys m, LoQueVale.Valor v, List<TrabajoAbierto> conTecnico) {
        EstadoUnidad.Resultado r = EstadoUnidad.de(ac, m, v, conTecnico, canal);
        if (r.sim() != null) return 0;
        if (conTecnico != null && !conTecnico.isEmpty()) return "NO_CUADRA".equals(r.estado()) || !ac.acepta(v.sku()) ? 0 : 2;
        if (r.na()) return 3;
        return switch (r.estado()) {
            case "CUADRA", "CON_AVISO" -> 1;
            case "EN_TALLER" -> ac.acepta(v.sku()) ? 2 : 0;
            case "NECESITA_TALLER" -> 3;
            default -> 0;
        };
    }

    private static boolean esMs(String grado) {
        String g = grado == null ? "" : grado.strip();
        return g.length() >= 2 && g.substring(0, 2).equalsIgnoreCase("Ms");
    }
}
