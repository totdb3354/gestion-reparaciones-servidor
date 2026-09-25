package com.reparaciones.servidor.model;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/** Alta por lotes del formulario "Nuevo otro pedido" (spec 4b §4.4, P5). Anidados: springdoc los publica como
 *  LoteComprasOtrosPeticion y LoteComprasOtrosLinea; la respuesta es LoteCompras.Respuesta. Sin divisa ni precioEur:
 *  la divisa es la del proveedor y el importe en euros lo calcula el servidor. */
public final class LoteComprasOtros {
    private LoteComprasOtros() {}

    public record Peticion(List<Linea> lineas) {}

    /** idProv nulo o concepto nulo/en blanco = sin rellenar en el formulario (422 "Línea i: …"). */
    public record Linea(@Schema(nullable = true) Integer idProv, @Schema(nullable = true) String concepto,
                        int cantidad, boolean esUrgente, double precioUnidad) {}
}
