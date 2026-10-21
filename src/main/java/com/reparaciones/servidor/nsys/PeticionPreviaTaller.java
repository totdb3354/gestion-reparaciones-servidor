package com.reparaciones.servidor.nsys;

import java.util.List;

/** POST /api/reparaciones/sku-taller/previsualizar (spec 0.9.21 pieza 2 §2.4): el IMEI del formulario y las filas que se van
 *  a guardar. */
public record PeticionPreviaTaller(String imei, List<FilaPreviaTaller> filas) {}
