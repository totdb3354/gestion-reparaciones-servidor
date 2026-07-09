package com.reparaciones.servidor.service;

import java.util.ArrayList;
import java.util.List;

/**
 * LA función de derivación de ubicación (spec F2 §2.2): la ubicación nunca se
 * almacena ni se mantiene a mano, se deriva de estado + trabajos abiertos + cliente.
 * <p>Prioridades: BLOQUEADO/ENVIADO/DESGUACE mandan sobre todo; después cualquier
 * trabajo abierto ⇒ REPARACIONES (estado efectivo EN_REPARACION, implícito);
 * después el estado almacenado. "Es pedido" = tiene cliente asignado (ID_CLI):
 * cuando exista la entidad Pedido se enchufa SOLO aquí.</p>
 */
public final class UbicacionDerivador {

    private UbicacionDerivador() {}

    public record Resultado(String estadoEfectivo, String ubicacion, List<String> subUbicaciones) {}

    public static Resultado derivar(String estado, int pulAbiertos, int glassAbiertos,
                                    int normalAbiertos, Integer idCli) {
        if ("BLOQUEADO".equals(estado)) return new Resultado("BLOQUEADO", "BLOQUEO", List.of());
        if ("ENVIADO".equals(estado))   return new Resultado("ENVIADO", null, List.of());
        if ("DESGUACE".equals(estado))  return new Resultado("DESGUACE", null, List.of());

        if (pulAbiertos + glassAbiertos + normalAbiertos > 0) {
            List<String> subs = new ArrayList<>(2);
            if      (pulAbiertos   > 0) subs.add("PULIDO");   // parte pantalla: pulido antes que glass
            else if (glassAbiertos > 0) subs.add("GLASS");
            if (normalAbiertos > 0) subs.add("NORMAL");        // parte cuerpo
            return new Resultado("EN_REPARACION", "REPARACIONES", List.copyOf(subs));
        }
        if (estado == null) return new Resultado(null, null, List.of()); // histórico fuera del ciclo
        return switch (estado) {
            case "RECIBIDO"    -> new Resultado("RECIBIDO", "ALMACEN", List.of());
            case "EN_REVISION" -> new Resultado("EN_REVISION", "PARA_REVISAR", List.of());
            case "OK"          -> new Resultado("OK", idCli != null ? "PEDIDOS" : "LISTOS", List.of());
            default            -> new Resultado(estado, null, List.of());
        };
    }
}
