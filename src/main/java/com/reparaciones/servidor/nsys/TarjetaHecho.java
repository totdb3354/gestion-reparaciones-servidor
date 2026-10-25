package com.reparaciones.servidor.nsys;

/** Las tarjetas de «Por pasar a NSYS» que llevan «Hecho» (spec 0.9.22 §3.6): Cambio de color, Cambio de grado y No está en NSYS
 *  (ALTA). «Revisar a mano» no lleva: se va al corregir el móvil o con «Revisado». */
public enum TarjetaHecho { COLOR, GRADO, ALTA }
