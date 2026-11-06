package com.reparaciones.servidor.nsys;

import java.time.LocalDateTime;

/** Una fila de Taller_vuelta_ignorada (spec 0.9.27 §3): un vendido o de baja que salía en ámbar y no ha vuelto, quién
 *  ({@code usuario}: su nombre, null si ya no existe) y cuándo lo ignoró (hora de Madrid). */
public record VueltaIgnorada(String imeiSn, Integer idUsu, String usuario, LocalDateTime fecha) {}
