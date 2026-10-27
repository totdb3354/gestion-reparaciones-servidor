package com.reparaciones.servidor.nsys;

import java.time.LocalDateTime;

/** Una fila de Taller_descarte (spec 0.9.22 pieza 3 §3): un IMEI de «No está en NSYS» que no va a NSYS, quién ({@code usuario}:
 *  su nombre, null si ya no existe), cuándo (hora de Madrid) y por qué. */
public record DescarteTaller(String imeiSn, Integer idUsu, String usuario, LocalDateTime fecha, String motivo) {}
