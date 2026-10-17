package com.reparaciones.servidor.nsys;

import java.time.LocalDateTime;

/** Un trabajo del taller (fila de Reparacion) de un IMEI, para el rastro. */
public record TrabajoTaller(String idRep, LocalDateTime asignado, LocalDateTime terminado, String tecnico) {}
