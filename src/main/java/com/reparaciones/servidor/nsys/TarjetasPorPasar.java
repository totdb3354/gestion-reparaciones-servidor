package com.reparaciones.servidor.nsys;

import java.util.List;

/** Las cuatro tarjetas de «Por pasar a NSYS» (spec 0.9.22 §3), cada lista de lo más antiguo a lo más reciente, aparte los
 *  descartados de «No está en NSYS» (pieza 3 §4.2), del descarte más reciente al más antiguo, y las filas «Sin grado en NSYS» de
 *  «Revisar a mano» (pieza 4 §4.1), del trabajo más antiguo al más reciente. */
public record TarjetasPorPasar(List<FilaColorNsys> color, List<FilaGradoNsys> grado, List<FilaAltaNsys> altas,
                               List<FilaRevisarNsys> revisar, List<FilaDescartadaNsys> descartadas,
                               List<FilaSinGradoNsys> sinGrado) {

    /** Sin filas «Sin grado en NSYS». */
    public TarjetasPorPasar(List<FilaColorNsys> color, List<FilaGradoNsys> grado, List<FilaAltaNsys> altas,
                            List<FilaRevisarNsys> revisar, List<FilaDescartadaNsys> descartadas) {
        this(color, grado, altas, revisar, descartadas, List.of());
    }
}
