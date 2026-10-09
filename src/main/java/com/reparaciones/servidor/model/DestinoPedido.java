package com.reparaciones.servidor.model;

import io.swagger.v3.oas.annotations.media.Schema;

/** Bloque elegido por la web para una línea (0.9.9, spec «Pedidos por bloques» §5.2): `idBloque` nulo = bloque nuevo.
 *  Donde el destino es opcional, no mandarlo (null) = regla por defecto. */
public record DestinoPedido(@Schema(nullable = true, description = "Nulo = bloque nuevo") Integer idBloque) {}
