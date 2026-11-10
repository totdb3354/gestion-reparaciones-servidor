package com.reparaciones.servidor.nsys;

import java.util.List;

/** Las tarjetas de «Por pasar a NSYS» (spec 0.9.22 §3), cada lista de lo más antiguo a lo más reciente, aparte los
 *  descartados de «No está en NSYS» (pieza 3 §4.2), del descarte más reciente al más antiguo, las filas «Sin grado en NSYS» de
 *  «Revisar a mano» (pieza 4 §4.1), del trabajo más antiguo al más reciente, los ignorados de Color, Grado y Revisar (0.9.27
 *  §4.2), del ignorado más reciente al más antiguo, y la quinta tarjeta, «Fuera de su caja» (spec «Fuera de su caja» §4.2), por
 *  la caja que toca. */
public record TarjetasPorPasar(List<FilaColorNsys> color, List<FilaGradoNsys> grado, List<FilaAltaNsys> altas,
                               List<FilaRevisarNsys> revisar, List<FilaDescartadaNsys> descartadas,
                               List<FilaSinGradoNsys> sinGrado, List<FilaIgnoradaNsys> ignorados, List<FilaCajaNsys> cajas) {

    /** Sin «Fuera de su caja». */
    public TarjetasPorPasar(List<FilaColorNsys> color, List<FilaGradoNsys> grado, List<FilaAltaNsys> altas,
                            List<FilaRevisarNsys> revisar, List<FilaDescartadaNsys> descartadas, List<FilaSinGradoNsys> sinGrado,
                            List<FilaIgnoradaNsys> ignorados) {
        this(color, grado, altas, revisar, descartadas, sinGrado, ignorados, List.of());
    }

    /** Sin ignorados ni «Fuera de su caja». */
    public TarjetasPorPasar(List<FilaColorNsys> color, List<FilaGradoNsys> grado, List<FilaAltaNsys> altas,
                            List<FilaRevisarNsys> revisar, List<FilaDescartadaNsys> descartadas, List<FilaSinGradoNsys> sinGrado) {
        this(color, grado, altas, revisar, descartadas, sinGrado, List.of(), List.of());
    }

    /** Sin filas «Sin grado en NSYS», ignorados ni «Fuera de su caja». */
    public TarjetasPorPasar(List<FilaColorNsys> color, List<FilaGradoNsys> grado, List<FilaAltaNsys> altas,
                            List<FilaRevisarNsys> revisar, List<FilaDescartadaNsys> descartadas) {
        this(color, grado, altas, revisar, descartadas, List.of(), List.of(), List.of());
    }
}
