package com.reparaciones.servidor.pedidos;

import com.reparaciones.servidor.nsys.EstadoMovil;
import com.reparaciones.servidor.nsys.MovilNsys;
import com.reparaciones.servidor.nsys.NsysDAO;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/** Junta las tres fuentes de una vuelta en pedidos con sus unidades y su IMEI (spec pieza 2 §6.1-6.3). Es puro. */
public final class CrucePedidos {

    public record Entrada(List<PedidoShippyPro> shippyPro, Map<String, Canal> canales, List<FilaPedidos2026> pedidos2026,
                          List<FilaSimulador> simulador, AceptaSku acepta, Map<String, MovilNsys> moviles) {}

    public record Resultado(List<PedidoLeido> pedidos, Set<String> desconocidosShippyPro, Set<String> desconocidosSimulador) {}

    /** Una unidad de ShippyPro mientras se le busca IMEI. */
    private static final class Hueco {
        final PedidoShippyPro pedido;
        final int posicion;
        final String sku;
        final Aceptacion aceptacion;
        FilaPedidos2026 fila;
        boolean sinPedidoConcreto;

        Hueco(PedidoShippyPro pedido, int posicion, String sku, Aceptacion aceptacion) {
            this.pedido = pedido;
            this.posicion = posicion;
            this.sku = sku;
            this.aceptacion = aceptacion;
        }
    }

    private CrucePedidos() {}

    public static Resultado cruzar(Entrada e) {
        Set<String> desconocidosSimulador = new TreeSet<>();
        List<PedidoLeido> pedidos = new ArrayList<>(simulador(e, desconocidosSimulador));
        Set<String> delSimulador = new HashSet<>();
        for (PedidoLeido p : pedidos)
            for (PedidoLeido.Unidad u : p.unidades()) if (u.imei() != null) delSimulador.add(NsysDAO.claveCruce(u.imei()));

        Set<String> desconocidosShippyPro = new TreeSet<>();
        Map<PedidoShippyPro, Canal> canalDe = new LinkedHashMap<>();
        List<Hueco> huecos = new ArrayList<>();
        for (PedidoShippyPro p : e.shippyPro()) {
            Canal canal = e.canales().get(p.marketplace());
            if (canal == null) continue;
            canalDe.put(p, canal);
            int posicion = 0;
            for (PedidoShippyPro.Articulo a : p.articulos()) {
                Aceptacion ac = e.acepta().para(canal, a.sku());
                if (ac == null) {
                    if (Textos.pareceTelefono(a.sku())) desconocidosShippyPro.add(a.sku());
                    continue;
                }
                for (int i = 0; i < a.cantidad(); i++) huecos.add(new Hueco(p, ++posicion, a.sku(), ac));
            }
        }
        huecos.sort(Comparator.comparing((Hueco h) -> h.pedido.fecha()).thenComparing(h -> h.pedido.numero())
                .thenComparingInt(h -> h.posicion));

        List<FilaPedidos2026> filas = e.pedidos2026().stream()
                .filter(f -> !delSimulador.contains(NsysDAO.claveCruce(f.imei())))
                .filter(f -> !vendido(e.moviles().get(NsysDAO.claveCruce(f.imei()))))
                .toList();
        repartirAmazon(huecos, canalDe, filas, e.moviles());
        repartirWebs(huecos, canalDe, filas);

        Map<PedidoShippyPro, List<PedidoLeido.Unidad>> unidades = new LinkedHashMap<>();
        for (Hueco h : huecos) {
            FilaPedidos2026 f = h.fila;
            unidades.computeIfAbsent(h.pedido, k -> new ArrayList<>()).add(new PedidoLeido.Unidad(h.posicion, h.sku,
                    f == null ? null : f.imei(), f == null ? null : FuentePedidos.PEDIDOS_2026, f == null ? null : f.fila(),
                    f == null ? null : f.fecha(), h.sinPedidoConcreto, null, null));
        }
        unidades.forEach((p, u) -> {
            List<PedidoLeido.Unidad> ordenadas = u.stream().sorted(Comparator.comparingInt(PedidoLeido.Unidad::posicion)).toList();
            pedidos.add(new PedidoLeido(canalDe.get(p), p.numero(), p.cliente(), p.pais(), p.fecha(), FuentePedidos.SHIPPYPRO, ordenadas));
        });
        return new Resultado(pedidos, desconocidosShippyPro, desconocidosSimulador);
    }

    private static void repartirAmazon(List<Hueco> huecos, Map<PedidoShippyPro, Canal> canalDe, List<FilaPedidos2026> filas,
                                       Map<String, MovilNsys> moviles) {
        List<FilaPedidos2026> libres = new ArrayList<>(filas.stream()
                .filter(f -> "amazon".equals(Textos.cliente(f.plataforma())))
                .sorted(Comparator.comparingInt((FilaPedidos2026 f) -> avance(moviles.get(NsysDAO.claveCruce(f.imei()))))
                        .thenComparing(f -> recepcion(moviles.get(NsysDAO.claveCruce(f.imei()))), Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparingInt(FilaPedidos2026::fila))
                .toList());
        for (Hueco h : huecos) {
            if (!canalDe.get(h.pedido).esAmazon()) continue;
            FilaPedidos2026 f = primeraQueCubre(libres, h);
            if (f != null) {
                h.fila = f;
                h.sinPedidoConcreto = true;
                libres.remove(f);
            }
        }
    }

    private static void repartirWebs(List<Hueco> huecos, Map<PedidoShippyPro, Canal> canalDe, List<FilaPedidos2026> filas) {
        List<FilaPedidos2026> libres = new ArrayList<>(filas.stream()
                .filter(f -> !"amazon".equals(Textos.cliente(f.plataforma())))
                .sorted(Comparator.comparingInt(FilaPedidos2026::fila)).toList());
        for (Hueco h : huecos) {
            if (!canalDe.get(h.pedido).esWeb()) continue;
            String cliente = Textos.cliente(h.pedido.cliente());
            if (cliente == null) continue;
            FilaPedidos2026 f = primeraQueCubre(libres.stream().filter(x -> cliente.equals(Textos.cliente(x.plataforma()))).toList(), h);
            if (f != null) {
                h.fila = f;
                libres.remove(f);
            }
        }
    }

    /** La primera fila de la lista que cubre el hueco (ajuste 4: SKU del pedido o aceptado, y del mismo día o después). No la
     *  quita de la lista: lo hace quien la usa. */
    private static FilaPedidos2026 primeraQueCubre(List<FilaPedidos2026> filas, Hueco h) {
        LocalDate dia = h.pedido.fecha().toLocalDate();
        for (FilaPedidos2026 f : filas) {
            boolean mismoSku = Objects.equals(Textos.sku(f.sku()), Textos.sku(h.sku)) || h.aceptacion.acepta(f.sku());
            if (mismoSku && !f.fecha().toLocalDate().isBefore(dia)) return f;
        }
        return null;
    }

    private static List<PedidoLeido> simulador(Entrada e, Set<String> desconocidos) {
        List<FilaSimulador> filas = e.simulador().stream().sorted(Comparator.comparingInt(FilaSimulador::fila)).toList();
        Map<String, FilaSimulador> primera = new HashMap<>();
        Map<String, String> otroOrder = new HashMap<>();
        Set<String> abonados = new HashSet<>();
        for (FilaSimulador f : filas) {
            if (f.imei() == null) continue;
            String clave = NsysDAO.claveCruce(f.imei());
            if ("BB".equals(f.linea())) {
                abonados.add(clave);
                continue;
            }
            FilaSimulador previa = primera.putIfAbsent(clave, f);
            if (previa != null && !previa.orderId().equals(f.orderId())) otroOrder.putIfAbsent(clave, f.orderId());
        }
        Map<String, List<FilaSimulador>> porPedido = new LinkedHashMap<>();
        for (FilaSimulador f : filas) if (!"BB".equals(f.linea())) porPedido.computeIfAbsent(f.orderId(), k -> new ArrayList<>()).add(f);

        List<PedidoLeido> pedidos = new ArrayList<>();
        porPedido.forEach((orderId, lineas) -> {
            List<PedidoLeido.Unidad> unidades = new ArrayList<>();
            int posicion = 0;
            for (FilaSimulador f : lineas) {
                if (e.acepta().para(Canal.SIMULADOR, f.sku()) == null) {
                    if (Textos.pareceTelefono(f.sku())) desconocidos.add(f.sku());
                    continue;
                }
                String clave = f.imei() == null ? null : NsysDAO.claveCruce(f.imei());
                if (clave != null && primera.get(clave).fila() != f.fila()) continue;
                String cierre = clave == null ? null : abonados.contains(clave) ? "ABONO"
                        : vendido(e.moviles().get(clave)) ? "RELEASED" : null;
                unidades.add(new PedidoLeido.Unidad(++posicion, f.sku(), f.imei(), f.imei() == null ? null : FuentePedidos.SIMULADOR,
                        f.imei() == null ? null : f.fila(), f.imei() == null ? null : f.fecha().atStartOfDay(), false,
                        clave == null ? null : otroOrder.get(clave), cierre));
            }
            if (unidades.isEmpty()) return;
            LocalDate fecha = lineas.stream().map(FilaSimulador::fecha).min(Comparator.naturalOrder()).orElseThrow();
            String cliente = lineas.stream().map(FilaSimulador::cliente).filter(c -> c != null && !c.isBlank()).findFirst().orElse(null);
            pedidos.add(new PedidoLeido(Canal.SIMULADOR, orderId, cliente, null, fecha.atStartOfDay(), FuentePedidos.SIMULADOR, unidades));
        });
        return pedidos;
    }

    private static boolean vendido(MovilNsys m) {
        return m != null && m.estado() == EstadoMovil.VENDIDO;
    }

    /** 0 Listos, 1 taller, 2 el resto (sin revisar, devolución…), 3 sin el móvil en el espejo. Un móvil de pedido, por su última
     *  caja. */
    private static int avance(MovilNsys m) {
        if (m == null) return 3;
        String u = m.estado() == EstadoMovil.DE_PEDIDO ? m.ubicacionUltima() : m.ubicacion();
        if (u == null) return 2;
        if (u.strip().equalsIgnoreCase("Listos")) return 0;
        return u.strip().startsWith("Taller") ? 1 : 2;
    }

    private static LocalDate recepcion(MovilNsys m) {
        return m == null ? null : m.receivingDate();
    }
}
