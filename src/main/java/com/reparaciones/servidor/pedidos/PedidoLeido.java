package com.reparaciones.servidor.pedidos;

import java.time.LocalDateTime;
import java.util.List;

/** Un pedido tal como sale del cruce de una vuelta (spec pieza 2 §6), antes de guardarlo. */
public record PedidoLeido(Canal canal, String numero, String cliente, String pais, LocalDateTime fecha, FuentePedidos fuente,
                          List<Unidad> unidades) {

    public PedidoLeido {
        unidades = List.copyOf(unidades);
    }

    /** {@code motivoCierre}: RELEASED o ABONO (solo el Simulador); null si sigue abierta. */
    public record Unidad(int posicion, String sku, String imei, FuentePedidos imeiFuente, Integer imeiRef,
                         LocalDateTime imeiTecleadoEn, boolean sinPedidoConcreto, String otroOrderId, String motivoCierre) {}
}
