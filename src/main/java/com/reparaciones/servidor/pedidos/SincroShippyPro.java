package com.reparaciones.servidor.pedidos;

import com.reparaciones.servidor.nsys.AnchosNsys;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.LocalDateTime;
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
    static String numeroGuardado(String numero) {
        return AnchosNsys.corta(numero.strip(), AnchosPedidos.PEDIDO);
    }

    /** Una fila de ShippyPro a la que hay que poner los datos de su envio (y cerrarla si estaba viva). */
    public record MarcaEnvio(int fila, LocalDateTime fecha, String transportista, String seguimiento, Long envioId,
                             LocalDateTime impresaEn) {}

    /** Un envio mas reciente (otra etiqueta) que sustituye al de una fila que aun no se habia imprimido. */
    public record EnvioNuevo(LocalDateTime fecha, String transportista, String seguimiento) {}

    /** Una fila que ya tenia envio a la que hay que completar el id del envio y/o la impresion; nunca borra lo ya puesto.
     *  Con {@code nuevo} no nulo la fila (sin imprimir) cambia a ese envio: {@code envioId} e {@code impresaEn} pasan a ser los
     *  de ese envio (la impresion puede ser nula) y se sustituyen tambien fecha, transportista y seguimiento. */
    public record MarcaEtiqueta(int fila, Long envioId, LocalDateTime impresaEn, EnvioNuevo nuevo) {
        public MarcaEtiqueta(int fila, Long envioId, LocalDateTime impresaEn) {
            this(fila, envioId, impresaEn, null);
        }
    }

    /** Las filas que hay que marcar como enviadas (spec §6 bis): las de {@code candidatas} (filas de ShippyPro sin envio, vivas o
     *  cerradas) cuyo pedido (canal y numero guardado) figura entre los enviados y no esta entre los {@code pendientes} de esta
     *  vuelta. Un pedido con varias expediciones toma la mas reciente. Reglas:
     *  <ul>
     *  <li>Una fila reabierta (su pedido volvio a estar pendiente) solo acepta un envio posterior a {@code reabiertaEn}: el envio
     *  anterior, aun dentro de la ventana de lectura, no es el de esta vez; la fila sigue el camino normal (se cierra como «ya no
     *  pendiente» tras dos lecturas buenas) salvo que aparezca un envio mas nuevo.</li>
     *  <li>Un pedido con varias posiciones (filas) que sale de los pendientes y tiene un envio: TODAS sus filas reciben los datos de
     *  ese envio; con esta API no se distingue un envio parcial.</li>
     *  <li>Un envio de un marketplace que no esta en {@code canales} se ignora. Los envios manuales llevan el marketplace «0»:
     *  «0» no debe asignarse nunca a un canal en {@code pedidos.shippypro.canales}.</li>
     *  </ul> */
    public static List<MarcaEnvio> marcarEnviadas(List<ClienteShippyPro.EnvioShippyPro> enviados, Map<String, Canal> canales,
                                                  Set<ClavePedido> pendientes, List<FilaPedido> candidatas) {
        Map<ClavePedido, ClienteShippyPro.EnvioShippyPro> recientes = enviadosPorPedido(enviados, canales);
        List<MarcaEnvio> marcas = new ArrayList<>();
        for (FilaPedido f : candidatas) {
            if (f.envioFecha() != null || f.canal() == null || f.pedido() == null) continue;
            ClavePedido clave = new ClavePedido(f.canal(), f.pedido());
            ClienteShippyPro.EnvioShippyPro e = recientes.get(clave);
            if (e == null || pendientes.contains(clave)) continue;
            if (f.reabiertaEn() != null && !e.fecha().isAfter(f.reabiertaEn())) continue;
            marcas.add(new MarcaEnvio(f.id(), e.fecha(), e.transportista(), e.seguimiento(), e.idEnvio(), e.impresaEn()));
        }
        return marcas;
    }

    /** Las filas que ya tienen envio y aun no tienen el id del envio o la impresion de su etiqueta (spec 6 bis): si su pedido
     *  figura entre los enviados leidos, se completa lo que falta. Un pedido con varias expediciones toma la mas reciente. Mientras
     *  la fila no este impresa, el envio mas reciente manda: si su id es otro (etiqueta regenerada) la fila pasa a ese envio. Una
     *  fila ya impresa no cambia nunca de envio. Mismas reglas de reapertura y de pendientes que {@link #marcarEnviadas}. */
    public static List<MarcaEtiqueta> completarEtiquetas(List<ClienteShippyPro.EnvioShippyPro> enviados, Map<String, Canal> canales,
                                                         Set<ClavePedido> pendientes, List<FilaPedido> candidatas) {
        Map<ClavePedido, ClienteShippyPro.EnvioShippyPro> recientes = enviadosPorPedido(enviados, canales);
        List<MarcaEtiqueta> marcas = new ArrayList<>();
        for (FilaPedido f : candidatas) {
            if (f.envioFecha() == null || f.canal() == null || f.pedido() == null) continue;
            ClavePedido clave = new ClavePedido(f.canal(), f.pedido());
            ClienteShippyPro.EnvioShippyPro e = recientes.get(clave);
            if (e == null || pendientes.contains(clave)) continue;
            if (f.reabiertaEn() != null && !e.fecha().isAfter(f.reabiertaEn())) continue;
            if (f.impresaEn() == null && f.envioId() != null && e.idEnvio() != null && !f.envioId().equals(e.idEnvio())
                    && e.fecha().isAfter(f.envioFecha())) {
                marcas.add(new MarcaEtiqueta(f.id(), e.idEnvio(), e.impresaEn(),
                        new EnvioNuevo(e.fecha(), e.transportista(), e.seguimiento())));
                continue;
            }
            if (f.envioId() != null && e.idEnvio() != null && !f.envioId().equals(e.idEnvio())) continue;
            Long id = f.envioId() == null ? e.idEnvio() : null;
            LocalDateTime impresa = f.impresaEn() == null ? e.impresaEn() : null;
            if (id != null || impresa != null) marcas.add(new MarcaEtiqueta(f.id(), id, impresa));
        }
        return marcas;
    }

    /** Los pedidos (canal y numero guardado) con envio, para pedir a la base sus filas. */
    public static Set<ClavePedido> clavesEnviadas(List<ClienteShippyPro.EnvioShippyPro> enviados, Map<String, Canal> canales) {
        return new LinkedHashSet<>(enviadosPorPedido(enviados, canales).keySet());
    }

    private static Map<ClavePedido, ClienteShippyPro.EnvioShippyPro> enviadosPorPedido(
            List<ClienteShippyPro.EnvioShippyPro> enviados, Map<String, Canal> canales) {
        Map<ClavePedido, ClienteShippyPro.EnvioShippyPro> recientes = new HashMap<>();
        for (ClienteShippyPro.EnvioShippyPro e : enviados) {
            Canal canal = canales.get(e.marketplace());
            if (canal == null || e.numero() == null || e.numero().isBlank()) continue;
            recientes.merge(new ClavePedido(canal, numeroGuardado(e.numero())), e,
                    (a, b) -> b.fecha().isAfter(a.fecha()) ? b : a);
        }
        return recientes;
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
                            ORIGEN, canal, posicion, null, null, null, null));
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
