package com.reparaciones.servidor.model;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/** Alta por lotes del formulario "Nuevo pedido" (spec 4b §4.4, P5). Anidados: springdoc los publica como
 *  LoteComprasPeticion, LoteComprasLinea, LoteComprasSolicitudes y LoteComprasRespuesta. Sin divisa ni precioEur:
 *  la divisa es la del proveedor y el importe en euros lo calcula el servidor. */
public final class LoteCompras {
    private LoteCompras() {}

    /** solicitudes: las urgentes (ID_RC) y preventivas (ID_SOL) que originan las líneas; se marcan GESTIONADA en la
     *  misma transacción. Si llega null se trata como vacía. */
    public record Peticion(List<Linea> lineas, Solicitudes solicitudes,
                           @Schema(nullable = true) List<Destino> destinos,
                           @Schema(nullable = true, description = "0.9.15: otros pedidos del mismo guardado; nulo = ninguno")
                           List<LoteComprasOtros.Linea> otros) {}

    /** 0.9.9 (spec «Pedidos por bloques» §5.2): bloque al que van las líneas de un proveedor; `idBloque` nulo = bloque
     *  nuevo. Un proveedor sin entrada sigue la regla por defecto (su abierto más reciente o uno nuevo). */
    public record Destino(int idProv, @Schema(nullable = true) Integer idBloque) {}

    /** idCom e idProv nulos = sin elegir en el formulario (422 "Línea i: selecciona …"). */
    public record Linea(@Schema(nullable = true) Integer idCom, @Schema(nullable = true) Integer idProv,
                        int cantidad, boolean esUrgente, double precioUnidad) {}

    public record Solicitudes(List<Integer> urgentes, List<Integer> preventivas) {}

    /** Ids creados, en el orden de cada lista: `idsCreados` las piezas e `idsOtrosCreados` los otros de «Nuevo pedido»
     *  (0.9.15). También es la respuesta del lote de otros pedidos: allí `idsCreados` son los otros y `idsOtrosCreados`
     *  va vacía. */
    public record Respuesta(List<Integer> idsCreados, List<Integer> idsOtrosCreados) {
        /** Sin otros en `idsOtrosCreados` (el lote de otros y los tests de antes de la 0.9.15). */
        public Respuesta(List<Integer> idsCreados) {
            this(idsCreados, List.of());
        }
    }
}
