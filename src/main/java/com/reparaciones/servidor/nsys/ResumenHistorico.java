package com.reparaciones.servidor.nsys;

import java.time.LocalDateTime;

/** El histórico en Nsys_importacion (spec 0.9.18 §6.5): cuántas importaciones HISTORICO hay de cualquier resultado,
 *  cuántas están hechas (OK o RECHAZADO) y la fecha del Items del último fichero hecho (null si ninguno). */
public record ResumenHistorico(int total, int hechos, LocalDateTime ultimo) {}
