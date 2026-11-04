package com.reparaciones.servidor.pedidos;

import com.reparaciones.servidor.nsys.TrabajoAbierto;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/** El móvil de una unidad, con lo que vale del taller (0.9.21) y sus trabajos abiertos. {@code enNsys}: false sin IMEI o si NSYS
 *  no lo conoce (entonces todo va vacío: {@link #NINGUNO}). {@code ubicacion}: la visible (la última caja si es de pedido);
 *  {@code estadoNsys}: STOCK, DE_PEDIDO, NO_CUENTA, VENDIDO o BAJA. */
public record MovilUnidad(boolean enNsys, @Schema(nullable = true) String modelo, @Schema(nullable = true) String modeloMaestro,
                          @Schema(nullable = true) String capacidad, @Schema(nullable = true) String color,
                          @Schema(nullable = true) String grado, boolean bateriaNueva, boolean esEsim,
                          @Schema(nullable = true) String sku, @Schema(nullable = true) String ubicacion,
                          @Schema(nullable = true) String estadoNsys, List<TrabajoAbierto> trabajos) {

    public static final MovilUnidad NINGUNO = new MovilUnidad(false, null, null, null, null, null, false, false, null, null,
            null, List.of());
}
