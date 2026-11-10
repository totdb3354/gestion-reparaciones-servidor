package com.reparaciones.servidor.pedidos;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Lo que hay que hacer con la tabla de pedidos tras leer los pendientes de ShippyPro (spec §6): filas nuevas, filas que ya no
 *  estan pendientes (se cierran) y SKU de telefono que el maestro no conoce. Es puro: no toca la base de datos. */
public final class SincroShippyPro {

    public static final String ORIGEN = "SHIPPYPRO";

    private SincroShippyPro() {}

    public record Plan(List<FilaPedido> insertar, List<Integer> cerrar, Set<String> desconocidos) {}

    /** {@code hoy}: la fecha de Madrid. {@code vivas}: las filas vivas de origen ShippyPro. */
    public static Plan de(List<PedidoShippyPro> pendientes, Map<String, Canal> canales, AceptaSku acepta,
                          List<FilaPedido> vivas, LocalDate hoy) {
        Set<String> existentes = new HashSet<>();
        for (FilaPedido v : vivas) existentes.add(clave(v.canal(), v.pedido(), v.posicion()));

        Set<String> pedidosPendientes = new HashSet<>();
        List<FilaPedido> insertar = new ArrayList<>();
        Set<String> desconocidos = new LinkedHashSet<>();
        for (PedidoShippyPro p : pendientes) {
            Canal canal = canales.get(p.marketplace());
            if (canal == null || p.numero() == null || p.numero().isBlank()) continue;
            pedidosPendientes.add(clave(canal, p.numero(), null));
            String plataforma = plataforma(canal, p.cliente());
            int posicion = 0;
            for (PedidoShippyPro.Articulo a : p.articulos()) {
                boolean conocido = acepta.para(canal, a.sku()) != null;
                if (!conocido && Textos.pareceTelefono(a.sku())) desconocidos.add(a.sku().strip());
                for (int i = 0; i < a.cantidad(); i++) {
                    posicion++;
                    if (!conocido || !existentes.add(clave(canal, p.numero(), posicion))) continue;
                    insertar.add(new FilaPedido(0, hoy, plataforma, a.sku(), null, p.numero(), p.fecha().toLocalDate(),
                            ORIGEN, canal, posicion, null));
                }
            }
        }

        List<Integer> cerrar = new ArrayList<>();
        for (FilaPedido v : vivas)
            if (!pedidosPendientes.contains(clave(v.canal(), v.pedido(), null))) cerrar.add(v.id());
        return new Plan(insertar, cerrar, desconocidos);
    }

    /** AMZ, FNSK o el cliente del pedido (sin cliente: «Web»). */
    static String plataforma(Canal canal, String cliente) {
        if (canal == Canal.AMAZON) return "AMZ";
        if (canal == Canal.AMAZON_STOCKROOM) return "FNSK";
        return cliente == null || cliente.isBlank() ? "Web" : cliente.strip();
    }

    private static String clave(Canal canal, String pedido, Integer posicion) {
        return canal + "|" + pedido + "|" + posicion;
    }
}
