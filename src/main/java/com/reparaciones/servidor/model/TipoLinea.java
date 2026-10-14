package com.reparaciones.servidor.model;

/** Tipo de una línea de un bloque de pedido (0.9.15, spec «Añadir otro» §2): una pieza del catálogo
 *  (Compra_componente) o un otro (Compra_otro). Las dos tablas numeran aparte: una línea se identifica por tipo + id. */
public enum TipoLinea { PIEZA, OTRO }
