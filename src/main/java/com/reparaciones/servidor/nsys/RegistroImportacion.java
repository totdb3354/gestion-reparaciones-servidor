package com.reparaciones.servidor.nsys;

import java.time.LocalDateTime;

/** Una fila de Nsys_importacion con sus horas (Madrid), para la lista de la píldora de NSYS (spec 0.9.18 §6.5).
 *  {@code terminada} es null mientras está EN_CURSO. */
public record RegistroImportacion(int id, String fichero, LocalDateTime fechaExportacion, String resultado, int filas,
                                  int cambios, LocalDateTime empezada, LocalDateTime terminada, String mensaje) {}
