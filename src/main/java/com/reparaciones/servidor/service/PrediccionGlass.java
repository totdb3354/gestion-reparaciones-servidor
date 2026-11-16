package com.reparaciones.servidor.service;

import com.reparaciones.servidor.model.ReparacionResumen;
import com.reparaciones.servidor.model.Tecnico;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Elige al técnico de la glass automática (spec 2026-09-05-glass-prediccion, §4). Puro: sin UI ni red.
 * <p>Candidatos: técnicos activos con {@link Tecnico#isEsGlass()} que no tengan ya una glass abierta de
 * ese IMEI (ni en BD ni verde en el modal). Gana el de menor carga en <b>fracción de jornada de 9h sin
 * escalar</b> a las horas del día: el factor 9/horas es común a todos y en fin de semana vale 0, con lo que
 * comparar el % de pantalla mandaría siempre la glass al primero por alfabeto. La carga suma lo hecho hoy,
 * lo pendiente en BD y las entradas verdes del modal que ya le tocaron (reparación 1/20, chasis 1/8, glass
 * 1/17), con el alcance de la carga diaria: IMEI con cliente → solo lo que tiene cliente (Pedidos); sin
 * cliente → todo (Total). Empate → alfabético por nombre.</p>
 */
public final class PrediccionGlass {

    private PrediccionGlass() {}

    /**
     * Entrada verde del modal (sin guardar): IMEI, técnico, tipo (reparación/glass), chasis y si tiene
     * cliente. Pesa como pesaría una vez guardada.
     */
    public record VerdeEnModal(String imei, int idTec, TipoTrabajo tipo, boolean esChasis, boolean conCliente) {
        double fraccion9h() {
            return switch (tipo) {
                case GLASS -> 1.0 / CargaTecnicos.TOPE_GLASS_9H;
                case REPARACION -> esChasis ? 1.0 / CargaTecnicos.TOPE_CHASIS_9H : 1.0 / CargaTecnicos.TOPE_NORMALES_9H;
                case PULIDO -> 0;
            };
        }
    }

    /**
     * @param tecnicos    técnicos activos del modal (se filtra por {@code isActivo() && isEsGlass()})
     * @param abiertas    asignaciones abiertas cargadas en la vista (todas las categorías)
     * @param cerradasHoy asignaciones completadas hoy
     * @param verdesModal entradas verdes del modal en este momento
     * @param imei        IMEI que se asigna
     * @param conCliente  {@code true} si la reparación que se asigna tiene cliente (alcance Pedidos)
     * @param inicioHoy   inicio de hoy en Madrid; con él una «por cerrar» marcada hoy pesa entera
     * @return el técnico elegido, o {@code null} si no hay candidato (la glass nace roja)
     */
    /** Sin corte: nada cuenta como marcado «por cerrar» hoy (el cálculo de antes de la 0.9.36). */
    public static Tecnico elegir(List<Tecnico> tecnicos, List<ReparacionResumen> abiertas,
                                 List<ReparacionResumen> cerradasHoy, List<VerdeEnModal> verdesModal,
                                 String imei, boolean conCliente) {
        return elegir(tecnicos, abiertas, cerradasHoy, verdesModal, imei, conCliente, null);
    }

    public static Tecnico elegir(List<Tecnico> tecnicos, List<ReparacionResumen> abiertas,
                                 List<ReparacionResumen> cerradasHoy, List<VerdeEnModal> verdesModal,
                                 String imei, boolean conCliente, LocalDateTime inicioHoy) {
        Set<Integer> ocupados = new HashSet<>();
        for (ReparacionResumen r : abiertas)
            if (imei.equals(r.getImei()) && TipoTrabajo.desde(r.getIdRep()) == TipoTrabajo.GLASS) ocupados.add(r.getIdTec());
        for (VerdeEnModal g : verdesModal)
            if (g.tipo() == TipoTrabajo.GLASS && imei.equals(g.imei())) ocupados.add(g.idTec());

        Tecnico mejor = null;
        long mejorClave = Long.MAX_VALUE;
        for (Tecnico t : tecnicos) {
            if (!t.isActivo() || !t.isEsGlass() || ocupados.contains(t.getIdTec())) continue;
            // Redondeo a 1e-9 para que dos sumas iguales en distinto orden empaten de verdad (y el orden sea transitivo).
            long clave = Math.round(carga(t.getIdTec(), abiertas, cerradasHoy, verdesModal, conCliente, inicioHoy) * 1e9);
            if (mejor == null || clave < mejorClave
                    || (clave == mejorClave && t.getNombre().compareToIgnoreCase(mejor.getNombre()) < 0)) {
                mejor = t;
                mejorClave = clave;
            }
        }
        return mejor;
    }

    static double carga(int idTec, List<ReparacionResumen> abiertas, List<ReparacionResumen> cerradasHoy,
                        List<VerdeEnModal> verdesModal, boolean soloPedidos) {
        return carga(idTec, abiertas, cerradasHoy, verdesModal, soloPedidos, null);
    }

    /** Fracción de jornada de 9h del técnico: hecho hoy + pendiente en BD + entradas verdes del modal, con alcance
     *  Pedidos/Total. Suma los dos tramos de {@link CargaTecnicos#reparto}, así que da el mismo total que calcularDia. */
    static double carga(int idTec, List<ReparacionResumen> abiertas, List<ReparacionResumen> cerradasHoy,
                        List<VerdeEnModal> verdesModal, boolean soloPedidos, LocalDateTime inicioHoy) {
        double f = 0;
        for (ReparacionResumen r : cerradasHoy)
            if (r.getIdTec() == idTec) { double[] p = CargaTecnicos.reparto(r, false, soloPedidos, inicioHoy); f += p[0] + p[1]; }
        for (ReparacionResumen r : abiertas)
            if (r.getIdTec() == idTec) { double[] p = CargaTecnicos.reparto(r, true, soloPedidos, inicioHoy); f += p[0] + p[1]; }
        for (VerdeEnModal g : verdesModal)
            if (g.idTec() == idTec && (!soloPedidos || g.conCliente())) f += g.fraccion9h();
        return f;
    }
}
