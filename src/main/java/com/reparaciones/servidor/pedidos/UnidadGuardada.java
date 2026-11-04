package com.reparaciones.servidor.pedidos;

import java.time.LocalDateTime;

/** Una unidad abierta de Pedido_unidad. {@code imeiFuente}: PEDIDOS_2026 o SIMULADOR (null sin IMEI). */
public record UnidadGuardada(int id, int idPedido, int posicion, String sku, String imei, String imeiFuente,
                             LocalDateTime imeiTecleadoEn, boolean sinPedidoConcreto, String otroOrderId) {}
