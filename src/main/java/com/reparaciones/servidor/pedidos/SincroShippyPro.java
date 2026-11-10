package com.reparaciones.servidor.pedidos;

import com.reparaciones.servidor.nsys.AnchosNsys;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
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
        Map<String, Integer> posiciones = new HashMap<>();
        List<FilaPedido> insertar = new ArrayList<>();
        Set<String> desconocidos = new LinkedHashSet<>();
        for (PedidoShippyPro p : pendientes) {
            Canal canal = canales.get(p.marketplace());
            if (canal == null || p.numero() == null || p.numero().isBlank()) continue;
            // El numero como se guarda (sin espacios y cortado al ancho de la columna): asi la clave coincide con la de la base.
            String numero = AnchosNsys.corta(p.numero().strip(), AnchosPedidos.PEDIDO);
            pedidosPendientes.add(clave(canal, numero, null));
            String plataforma = plataforma(canal, p.cliente());
            String claveCuenta = clave(canal, numero, null);
            for (PedidoShippyPro.Articulo a : p.articulos()) {
                boolean conocido = acepta.para(canal, a.sku()) != null;
                if (!conocido && Textos.pareceTelefono(a.sku())) desconocidos.add(a.sku().strip());
                for (int i = 0; i < a.cantidad(); i++) {
                    int posicion = posiciones.merge(claveCuenta, 1, Integer::sum);
                    if (!conocido || !existentes.add(clave(canal, numero, posicion))) continue;
                    insertar.add(new FilaPedido(0, hoy, plataforma, a.sku(), null, numero, p.fecha().toLocalDate(),
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
