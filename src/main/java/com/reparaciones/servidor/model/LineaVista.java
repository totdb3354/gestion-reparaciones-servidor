package com.reparaciones.servidor.model;

import java.time.LocalDateTime;

/** Una línea tal como la vio la web (0.9.9, spec «Pedidos por bloques» §5.3): las acciones de bloque la comprueban
 *  contra la base antes de cambiar nada. */
public record LineaVista(int idCompra, LocalDateTime updatedAt) {}
