package com.reparaciones.servidor.nsys;

import java.time.LocalDateTime;

/** Una fila de Nsys_importacion. */
public record Importacion(int id, String fichero, LocalDateTime fechaExportacion, String resultado, int filas, String mensaje) {}
