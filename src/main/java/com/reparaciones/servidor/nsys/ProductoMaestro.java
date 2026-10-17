package com.reparaciones.servidor.nsys;

/** Una fila de Nsys_producto: lo que el maestro dice de un «Goods + Grade». {@code modelo} es el código del ERP (null si
 *  no está en el catálogo); {@code modeloMaestro}, el texto de la columna «Modelo». */
public record ProductoMaestro(String clave, String modelo, String modeloMaestro, String capacidad, String color,
                              String calidadDetalle, String skuWeb, String skuAmazon, String skuSinGrado, boolean esEsim) {}
