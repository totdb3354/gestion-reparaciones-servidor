package com.reparaciones.servidor.nsys;

import java.time.LocalDateTime;

/** Una fila de Taller_hecho (spec 0.9.22 §3.6): la marca «Hecho» de un IMEI en una tarjeta, quién ({@code usuario}: su nombre,
 *  null si ya no existe) y cuándo; {@code volvio}: la importación vio NSYS igual después de marcarla (null si no). Horas de
 *  Madrid. */
public record HechoTaller(String imeiSn, TarjetaHecho tarjeta, Integer idUsu, String usuario, LocalDateTime fecha,
                          LocalDateTime volvio) {}
