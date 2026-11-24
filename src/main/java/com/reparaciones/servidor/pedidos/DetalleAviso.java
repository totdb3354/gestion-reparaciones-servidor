package com.reparaciones.servidor.pedidos;

import com.reparaciones.servidor.nsys.TrabajoAbierto;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/** Lo que acompaña al motivo del aviso de un IMEI (spec 2026-11-24 §3). sim: ESIM (el móvil es eSIM y el pedido no), SIM_FISICA
 *  (al revés) o null. na: el grado que cuenta es n/a. ms: es un Ms. dato: lo que es el móvil para el motivo (modelo, capacidad,
 *  color o grado). trabajos: las asignaciones abiertas, solo con motivo EN_TALLER (si no, vacía). */
public record DetalleAviso(@Schema(nullable = true) String sim, boolean na, boolean ms, @Schema(nullable = true) String dato,
                           List<TrabajoAbierto> trabajos) {

    public DetalleAviso {
        trabajos = trabajos == null ? List.of() : List.copyOf(trabajos);
    }

    /** null si no hay nada que decir. */
    static DetalleAviso de(String sim, boolean na, boolean ms, String dato, List<TrabajoAbierto> trabajos) {
        boolean vacio = sim == null && !na && !ms && dato == null && (trabajos == null || trabajos.isEmpty());
        return vacio ? null : new DetalleAviso(sim, na, ms, dato, trabajos);
    }

    /** Lo que queda al validar el SKU: la SIM, el n/a y el Ms se siguen viendo; el dato del motivo no. */
    DetalleAviso validado() {
        return de(sim, na, ms, null, List.of());
    }
}
