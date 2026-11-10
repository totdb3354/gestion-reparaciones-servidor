package com.reparaciones.servidor.pedidos;

import io.swagger.v3.oas.annotations.media.Schema;

/** Modelo (el texto del maestro), capacidad, color, grado y eSIM: lo que pide un pedido o lo que es un móvil. Nunca va nulo en
 *  la API (springdoc no marca bien un objeto anidado nulo): si no se sabe, {@link #NINGUNO}, con todo vacío. */
public record Atributos(@Schema(nullable = true) String modelo, @Schema(nullable = true) String capacidad,
                        @Schema(nullable = true) String color, @Schema(nullable = true) String grado, boolean esEsim) {

    public static final Atributos NINGUNO = new Atributos(null, null, null, null, false);
}
