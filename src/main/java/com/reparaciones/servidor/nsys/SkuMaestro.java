package com.reparaciones.servidor.nsys;

/** Un SKU del maestro para un color y un grado del modelo de un móvil (spec 0.9.21 §2.3): la vista previa del diálogo
 *  «Corregir…». Solo salen las combinaciones con un SKU. */
public record SkuMaestro(String color, String grado, String sku) {}
