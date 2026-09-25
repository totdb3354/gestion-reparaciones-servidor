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
    public record Peticion(List<Linea> lineas, Solicitudes solicitudes) {}

    /** idCom e idProv nulos = sin elegir en el formulario (422 "Línea i: selecciona …"). */
    public record Linea(@Schema(nullable = true) Integer idCom, @Schema(nullable = true) Integer idProv,
                        int cantidad, boolean esUrgente, double precioUnidad) {}

    public record Solicitudes(List<Integer> urgentes, List<Integer> preventivas) {}

    /** Ids creados, en el orden de las líneas. También es la respuesta del lote de otros pedidos. */
    public record Respuesta(List<Integer> idsCreados) {}
}
