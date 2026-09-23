package com.reparaciones.servidor.service;

import com.reparaciones.servidor.model.ReparacionResumen;
import com.reparaciones.servidor.model.Tecnico;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Elige al técnico de la glass automática (spec 2026-09-05-glass-prediccion, §4). Puro: sin UI ni red.
 * <p>Candidatos: técnicos activos con {@link Tecnico#isEsGlass()} que no tengan ya una glass abierta de
 * ese IMEI (ni en BD ni verde en el modal). Gana el de menor carga en <b>fracción de jornada de 9h sin
 * escalar</b> a las horas del día: el factor 9/horas es común a todos y en fin de semana vale 0, con lo que
 * comparar el % de pantalla mandaría siempre la glass al primero por alfabeto. La carga suma lo hecho hoy,
 * lo pendiente en BD y las entradas verdes del modal que ya le tocaron (reparación 1/25, chasis 1/8, glass
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
     * @return el técnico elegido, o {@code null} si no hay candidato (la glass nace roja)
     */
    public static Tecnico elegir(List<Tecnico> tecnicos, List<ReparacionResumen> abiertas,
                                 List<ReparacionResumen> cerradasHoy, List<VerdeEnModal> verdesModal,
                                 String imei, boolean conCliente) {
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
            long clave = Math.round(carga(t.getIdTec(), abiertas, cerradasHoy, verdesModal, conCliente) * 1e9);
            if (mejor == null || clave < mejorClave
                    || (clave == mejorClave && t.getNombre().compareToIgnoreCase(mejor.getNombre()) < 0)) {
                mejor = t;
                mejorClave = clave;
            }
        }
        return mejor;
    }

    /** Fracción de jornada de 9h del técnico: hecho hoy + pendiente en BD + entradas verdes del modal, con alcance Pedidos/Total. */
    static double carga(int idTec, List<ReparacionResumen> abiertas, List<ReparacionResumen> cerradasHoy,
                        List<VerdeEnModal> verdesModal, boolean soloPedidos) {
        double f = 0;
        for (ReparacionResumen r : cerradasHoy) if (r.getIdTec() == idTec) f += CargaTecnicos.fraccion9h(r, false, soloPedidos);
        for (ReparacionResumen r : abiertas)    if (r.getIdTec() == idTec) f += CargaTecnicos.fraccion9h(r, true,  soloPedidos);
        for (VerdeEnModal g : verdesModal)
            if (g.idTec() == idTec && (!soloPedidos || g.conCliente())) f += g.fraccion9h();
        return f;
    }
}
