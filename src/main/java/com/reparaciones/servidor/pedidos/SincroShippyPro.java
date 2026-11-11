package com.reparaciones.servidor.pedidos;

import com.reparaciones.servidor.nsys.AnchosNsys;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Lo que hay que hacer con la tabla de pedidos tras leer los pendientes de ShippyPro (spec §6): filas nuevas, filas cerradas
 *  cuyo pedido vuelve a estar pendiente (se reabren con su IMEI), filas que ya no estan pendientes (candidatas a cerrar) y SKU
 *  de telefono que el maestro no conoce. Es puro: no toca la base de datos. */
public final class SincroShippyPro {

    private static final Logger log = LoggerFactory.getLogger(SincroShippyPro.class);
    public static final String ORIGEN = "SHIPPYPRO";
    /** Unidades maximas por linea de pedido: una cantidad absurda no debe crear miles de filas. */
    static final int MAX_UNIDADES = 50;

    private SincroShippyPro() {}

    /** {@code cerrar}: las filas vivas cuyo pedido ya no esta pendiente (quien llama decide cuando cerrarlas de verdad);
     *  {@code reabrir}: las filas cerradas de pedidos que vuelven a estar pendientes. */
    public record Plan(List<FilaPedido> insertar, List<Integer> reabrir, List<Integer> cerrar, Set<String> desconocidos) {}

    /** Un pedido de un canal, con el numero como se guarda en la base. */
    public record ClavePedido(Canal canal, String pedido) {}

    /** Los pedidos pendientes (canal y numero guardado) que tienen canal conocido y numero. */
    public static Set<ClavePedido> clavesPendientes(List<PedidoShippyPro> pendientes, Map<String, Canal> canales) {
        Set<ClavePedido> claves = new LinkedHashSet<>();
        for (PedidoShippyPro p : pendientes) {
            Canal canal = canales.get(p.marketplace());
            if (canal == null || p.numero() == null || p.numero().isBlank()) continue;
            claves.add(new ClavePedido(canal, numeroGuardado(p.numero())));
        }
        return claves;
    }

    /** El numero como se guarda (sin espacios y cortado al ancho de la columna): asi la clave coincide con la de la base. */
    private static String numeroGuardado(String numero) {
        return AnchosNsys.corta(numero.strip(), AnchosPedidos.PEDIDO);
    }

    /** {@code hoy}: la fecha de Madrid. {@code vivas}: las filas vivas de origen ShippyPro. {@code cerradas}: las filas cerradas
     *  de origen ShippyPro de los pedidos pendientes (las de otros pedidos no importan). */
    public static Plan de(List<PedidoShippyPro> pendientes, Map<String, Canal> canales, AceptaSku acepta,
                          List<FilaPedido> vivas, List<FilaPedido> cerradas, LocalDate hoy) {
        Set<String> existentes = new HashSet<>();
        for (FilaPedido v : vivas) existentes.add(clave(v.canal(), v.pedido(), v.posicion()));
        Map<String, Integer> cerradasPorClave = new HashMap<>();
        for (FilaPedido c : cerradas) cerradasPorClave.put(clave(c.canal(), c.pedido(), c.posicion()), c.id());
        List<Integer> reabrir = new ArrayList<>();

        Set<String> pedidosPendientes = new HashSet<>();
        Map<String, Integer> posiciones = new HashMap<>();
        List<FilaPedido> insertar = new ArrayList<>();
        Set<String> desconocidos = new LinkedHashSet<>();
        for (PedidoShippyPro p : pendientes) {
            Canal canal = canales.get(p.marketplace());
            if (canal == null || p.numero() == null || p.numero().isBlank()) continue;
            String numero = numeroGuardado(p.numero());
            pedidosPendientes.add(clave(canal, numero, null));
            String plataforma = plataforma(canal, p.cliente());
            String claveCuenta = clave(canal, numero, null);
            for (PedidoShippyPro.Articulo a : p.articulos()) {
                boolean conocido = acepta.para(canal, a.sku()) != null;
                if (!conocido && Textos.pareceTelefono(a.sku())) desconocidos.add(a.sku().strip());
                int unidades = a.cantidad();
                if (unidades > MAX_UNIDADES) {
                    log.warn("Pedidos: una linea del pedido {} pide {} unidades; se cortan a {}", numero, unidades, MAX_UNIDADES);
                    unidades = MAX_UNIDADES;
                }
                for (int i = 0; i < unidades; i++) {
                    int posicion = posiciones.merge(claveCuenta, 1, Integer::sum);
                    String clavePosicion = clave(canal, numero, posicion);
                    if (existentes.contains(clavePosicion)) continue;
                    Integer cerrada = cerradasPorClave.get(clavePosicion);
                    if (cerrada != null) {
                        // Ya tuvo fila y se cerro: se reabre con su IMEI (insertarla chocaria con la clave unica).
                        reabrir.add(cerrada);
                        existentes.add(clavePosicion);
                        continue;
                    }
                    if (!conocido) continue;
                    existentes.add(clavePosicion);
                    insertar.add(new FilaPedido(0, hoy, plataforma, a.sku(), null, numero, p.fecha().toLocalDate(),
                            ORIGEN, canal, posicion, null));
                }
            }
        }

        List<Integer> cerrar = new ArrayList<>();
        for (FilaPedido v : vivas)
            if (!pedidosPendientes.contains(clave(v.canal(), v.pedido(), null))) cerrar.add(v.id());
        return new Plan(insertar, reabrir, cerrar, desconocidos);
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
