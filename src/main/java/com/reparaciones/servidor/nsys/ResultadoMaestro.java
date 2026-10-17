package com.reparaciones.servidor.nsys;

import java.util.List;
import java.util.Set;

/** Lo leído del maestro: los productos, los nombres que salieron repetidos con SKU distintos (se quedan sin SKU) y los
 *  textos de «Modelo» que no son un modelo del catálogo del ERP. */
public record ResultadoMaestro(List<ProductoMaestro> productos, List<String> conflictos, Set<String> modelosFueraDeCatalogo) {}
