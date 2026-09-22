package com.reparaciones.servidor.service;

/**
 * Tipo de trabajo derivado del prefijo del ID_REP: {@code A…} reparación, {@code AG…} glass,
 * {@code AP…} pulido. Equivalente del enum del cliente, sin lo que allí es presentación.
 */
public enum TipoTrabajo {
    REPARACION, GLASS, PULIDO;

    /** Un id nulo o desconocido se trata como reparación, igual que en el cliente. */
    public static TipoTrabajo desde(String idRep) {
        if (idRep == null) return REPARACION;
        if (idRep.startsWith("AP") || idRep.startsWith("P")) return PULIDO;
        if (idRep.startsWith("AG") || idRep.startsWith("G")) return GLASS;
        return REPARACION;
    }
}
