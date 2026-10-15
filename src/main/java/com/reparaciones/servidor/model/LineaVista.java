package com.reparaciones.servidor.model;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/** Una línea tal como la vio la web (0.9.9, spec «Pedidos por bloques» §5.3): las acciones de bloque la comprueban
 *  contra la base antes de cambiar nada. Desde la 0.9.15 (spec «Añadir otro» §4.3) lleva su `tipo` e `idCompra` es el id
 *  en su tabla (ID_COMPRA de una pieza, ID_COMPRA_OTRO de un otro). `tipo` nulo o ausente = pieza: la web 0.9.14 no lo
 *  manda. */
public record LineaVista(int idCompra, LocalDateTime updatedAt,
                         @Schema(nullable = true, description = "0.9.15: PIEZA u OTRO; nulo o ausente = PIEZA")
                         TipoLinea tipo) {}
