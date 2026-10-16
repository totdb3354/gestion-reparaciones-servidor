package com.reparaciones.servidor.nsys;

/** Una fila de Nsys_ubicacion: la caja de NSYS, su ubicación del ERP (null = no se trae), si cuenta como stock y si es
 *  «Pedidos realizados». */
public record UbicacionNsys(String location, String ubicacion, boolean cuentaStock, boolean esPedido) {}
