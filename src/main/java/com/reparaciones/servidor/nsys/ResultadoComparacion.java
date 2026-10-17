package com.reparaciones.servidor.nsys;

import java.util.List;
import java.util.Set;

/** Lo que sale de comparar una exportación: filas nuevas o cambiadas, eventos del rastro, cuántos móviles anteriores no
 *  venían en el fichero y las cajas que no están en Nsys_ubicacion. */
public record ResultadoComparacion(List<MovilNsys> aGuardar, List<EventoNsys> eventos, int desaparecidos,
                                   Set<String> cajasDesconocidas) {}
