package com.reparaciones.servidor.pedidos;

import com.reparaciones.servidor.nsys.EstadoMovil;
import com.reparaciones.servidor.nsys.MovilNsys;
import com.reparaciones.servidor.nsys.NsysDAO;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/** Junta las tres fuentes de una vuelta en pedidos con sus unidades y su IMEI (spec pieza 2 §6.1-6.3). Es puro. */
public final class CrucePedidos {

    public record Entrada(List<PedidoShippyPro> shippyPro, Map<String, Canal> canales, List<FilaPedidos2026> pedidos2026,
                          List<FilaSimulador> simulador, AceptaSku acepta, Map<String, MovilNsys> moviles) {}

    public record Resultado(List<PedidoLeido> pedidos, Set<String> desconocidosShippyPro, Set<String> desconocidosSimulador) {}

    /** Un pedido de ShippyPro de un marketplace del ajuste. Sin equals: cada uno es él mismo, aunque otro se le parezca. */
    private static final class Pedido {
        final PedidoShippyPro leido;
        final Canal canal;

        Pedido(PedidoShippyPro leido, Canal canal) {
            this.leido = leido;
            this.canal = canal;
        }
    }

    /** Una unidad de ShippyPro mientras se le busca IMEI. */
    private static final class Hueco {
        final Pedido pedido;
        final int posicion;
        final String sku;
        final Aceptacion aceptacion;
        FilaPedidos2026 fila;
        boolean sinPedidoConcreto;

        Hueco(Pedido pedido, int posicion, String sku, Aceptacion aceptacion) {
            this.pedido = pedido;
            this.posicion = posicion;
            this.sku = sku;
            this.aceptacion = aceptacion;
        }

        LocalDateTime fecha() {
            return pedido.leido.fecha();
        }

        PedidoLeido.Unidad unidad() {
            FilaPedidos2026 f = fila;
            return new PedidoLeido.Unidad(posicion, sku, f == null ? null : f.imei(), f == null ? null : FuentePedidos.PEDIDOS_2026,
                    f == null ? null : f.fila(), f == null ? null : f.fecha(), sinPedidoConcreto, null, null);
        }
    }

    /** Una unidad del Simulador mientras se recorre la hoja. */
    private static final class UnidadSimulador {
        final FilaSimulador linea;
        final int posicion;
        String otroOrderId;
        String motivoCierre;

        UnidadSimulador(FilaSimulador linea, int posicion) {
            this.linea = linea;
            this.posicion = posicion;
        }

        PedidoLeido.Unidad unidad() {
            boolean conImei = linea.imei() != null;
            return new PedidoLeido.Unidad(posicion, linea.sku(), linea.imei(), conImei ? FuentePedidos.SIMULADOR : null,
                    conImei ? linea.fila() : null, conImei ? linea.fecha().atStartOfDay() : null, false, otroOrderId, motivoCierre);
        }
    }

    private CrucePedidos() {}

    public static Resultado cruzar(Entrada e) {
        Set<String> desconocidosSimulador = new TreeSet<>();
        List<PedidoLeido> pedidos = new ArrayList<>(simulador(e, desconocidosSimulador));
        Set<String> delSimulador = imeisAbiertos(pedidos);

        Set<String> desconocidosShippyPro = new TreeSet<>();
        List<Hueco> huecos = huecos(e, desconocidosShippyPro);
        List<FilaPedidos2026> filas = e.pedidos2026().stream()
                .filter(f -> !delSimulador.contains(NsysDAO.claveCruce(f.imei())))
                .filter(f -> !vendido(movil(e.moviles(), f)))
                .toList();
        repartirAmazon(huecos, filas, e.moviles());
        repartirWebs(huecos, filas);
        pedidos.addAll(pedidosShippyPro(huecos));
        return new Resultado(pedidos, desconocidosShippyPro, desconocidosSimulador);
    }

    // ---------------------------------------------------------------- ShippyPro

    /** Las unidades de los pedidos de ShippyPro de los marketplaces del ajuste, de la más antigua a la más nueva. La posición
     *  cuenta todas las unidades de todos los artículos en orden, también las de un SKU que no conoce el maestro, para que no
     *  cambie entre lecturas (ajuste 7): esas no son unidades y dejan un hueco en la numeración. Un pedido que sale dos veces
     *  en la lista (mismo marketplace y número) cuenta una vez. */
    private static List<Hueco> huecos(Entrada e, Set<String> desconocidos) {
        List<Hueco> huecos = new ArrayList<>();
        Set<String> vistos = new HashSet<>();
        for (PedidoShippyPro p : e.shippyPro()) {
            Canal canal = e.canales().get(p.marketplace());
            if (canal == null || !vistos.add(p.marketplace() + "|" + p.numero())) continue;
            Pedido pedido = new Pedido(p, canal);
            int posicion = 0;
            for (PedidoShippyPro.Articulo a : p.articulos()) {
                Aceptacion ac = e.acepta().para(canal, a.sku());
                if (ac == null && Textos.pareceTelefono(a.sku())) desconocidos.add(a.sku());
                for (int i = 0; i < a.cantidad(); i++) {
                    posicion++;
                    if (ac != null) huecos.add(new Hueco(pedido, posicion, a.sku(), ac));
                }
            }
        }
        huecos.sort(Comparator.comparing(Hueco::fecha).thenComparing(h -> h.pedido.leido.numero())
                .thenComparingInt(h -> h.posicion));
        return huecos;
    }

    /** Los pedidos de ShippyPro con al menos una unidad, del más antiguo al más nuevo; sus unidades ya van por posición. */
    private static List<PedidoLeido> pedidosShippyPro(List<Hueco> huecos) {
        Map<Pedido, List<PedidoLeido.Unidad>> unidades = new LinkedHashMap<>();
        for (Hueco h : huecos) unidades.computeIfAbsent(h.pedido, k -> new ArrayList<>()).add(h.unidad());
        List<PedidoLeido> pedidos = new ArrayList<>();
        unidades.forEach((p, u) -> pedidos.add(new PedidoLeido(p.canal, p.leido.numero(), p.leido.cliente(), p.leido.pais(),
                p.leido.fecha(), FuentePedidos.SHIPPYPRO, u)));
        return pedidos;
    }

    // ---------------------------------------------------------------- PEDIDOS 2026: Amazon

    /** Amazon y Stockroom: las filas con Plataforma «Amazon» no son de un pedido concreto (llevan {@code sinPedidoConcreto}).
     *  Primero se cubren todos los pedidos que se puede, prefiriendo los más antiguos; después, entre los repartos que cubren
     *  esos mismos, el móvil más avanzado va al pedido más antiguo (decisión del usuario, 04/11). Son decenas de unidades: cada
     *  prueba rehace el emparejamiento entero, que se lee mejor que uno incremental. */
    private static void repartirAmazon(List<Hueco> huecos, List<FilaPedidos2026> filas, Map<String, MovilNsys> moviles) {
        List<Hueco> unidades = huecos.stream().filter(h -> h.pedido.canal.esAmazon()).toList();
        List<FilaPedidos2026> candidatas = filas.stream()
                .filter(f -> "amazon".equals(Textos.cliente(f.plataforma())))
                .filter(f -> unidades.stream().anyMatch(h -> cubre(f, h)))
                .sorted(porAvance(moviles))
                .toList();
        List<List<Integer>> cubren = new ArrayList<>();
        for (Hueco h : unidades) {
            List<Integer> suyas = new ArrayList<>();
            for (int f = 0; f < candidatas.size(); f++) if (cubre(candidatas.get(f), h)) suyas.add(f);
            cubren.add(suyas);
        }

        List<Integer> cubiertas = cubiertas(cubren, candidatas.size());
        boolean[] usada = new boolean[candidatas.size()];
        for (int k = 0; k < cubiertas.size(); k++) {
            int u = cubiertas.get(k);
            int f = mejorFila(cubren.get(u), cubiertas.subList(k + 1, cubiertas.size()), cubren, usada);
            usada[f] = true;
            Hueco h = unidades.get(u);
            h.fila = candidatas.get(f);
            h.sinPedidoConcreto = true;
        }
    }

    /** Fase 1: qué unidades quedan cubiertas en un emparejamiento máximo (Kuhn). Cada unidad, de la más antigua a la más nueva,
     *  busca un camino de aumento; una unidad ya cubierta puede cambiar de fila pero no se queda sin ella, así que, entre los
     *  repartos que cubren a más, gana el que cubre a las más antiguas. Las filas concretas de esta fase no importan. */
    private static List<Integer> cubiertas(List<List<Integer>> cubren, int filas) {
        int[] unidadDe = libres(filas);
        List<Integer> cubiertas = new ArrayList<>();
        for (int u = 0; u < cubren.size(); u++) if (aumentar(u, cubren, unidadDe, new boolean[filas])) cubiertas.add(u);
        return cubiertas;
    }

    /** Fase 2: la fila más avanzada para la unidad que deja sitio a las cubiertas que faltan por asignar. Siempre hay una, porque
     *  la fase 1 (o la vuelta anterior de esta) las cubre a la vez. */
    private static int mejorFila(List<Integer> suyas, List<Integer> faltan, List<List<Integer>> cubren, boolean[] usada) {
        for (int f : suyas) {
            if (usada[f]) continue;
            usada[f] = true;
            boolean caben = caben(faltan, cubren, usada);
            usada[f] = false;
            if (caben) return f;
        }
        throw new IllegalStateException("Pedidos: una unidad cubierta en la fase 1 se ha quedado sin fila");
    }

    /** ¿Tienen todas estas unidades una fila distinta sin tocar las ya usadas? */
    private static boolean caben(List<Integer> unidades, List<List<Integer>> cubren, boolean[] usada) {
        int[] unidadDe = libres(usada.length);
        for (int u : unidades) if (!aumentar(u, cubren, unidadDe, usada.clone())) return false;
        return true;
    }

    /** Busca una fila para la unidad {@code u}: una libre o una ocupada cuya unidad pueda irse a otra (camino de aumento).
     *  {@code vista} empieza con las filas que no se pueden tocar y apunta las que ya se han probado en esta búsqueda. */
    private static boolean aumentar(int u, List<List<Integer>> cubren, int[] unidadDe, boolean[] vista) {
        for (int f : cubren.get(u)) {
            if (vista[f]) continue;
            vista[f] = true;
            if (unidadDe[f] < 0 || aumentar(unidadDe[f], cubren, unidadDe, vista)) {
                unidadDe[f] = u;
                return true;
            }
        }
        return false;
    }

    private static int[] libres(int filas) {
        int[] unidadDe = new int[filas];
        Arrays.fill(unidadDe, -1);
        return unidadDe;
    }

    /** Primero Listos, luego taller, luego el resto y al final las que no están en el espejo; en empate, la de fecha de
     *  recepción más antigua y después la de número de fila más bajo. */
    private static Comparator<FilaPedidos2026> porAvance(Map<String, MovilNsys> moviles) {
        return Comparator.comparingInt((FilaPedidos2026 f) -> avance(movil(moviles, f)))
                .thenComparing(f -> recepcion(movil(moviles, f)), Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparingInt(FilaPedidos2026::fila);
    }

    // ---------------------------------------------------------------- PEDIDOS 2026: webs

    /** Webs: las filas cuya Plataforma es el cliente del pedido; las unidades, de la más antigua a la más nueva, cogen la primera
     *  fila libre que las cubre (por número de fila). */
    private static void repartirWebs(List<Hueco> huecos, List<FilaPedidos2026> filas) {
        List<FilaPedidos2026> libres = new ArrayList<>(filas.stream()
                .filter(f -> !"amazon".equals(Textos.cliente(f.plataforma())))
                .sorted(Comparator.comparingInt(FilaPedidos2026::fila)).toList());
        for (Hueco h : huecos) {
            if (!h.pedido.canal.esWeb()) continue;
            String cliente = Textos.cliente(h.pedido.leido.cliente());
            if (cliente == null) continue;
            FilaPedidos2026 f = libres.stream().filter(x -> cliente.equals(Textos.cliente(x.plataforma())) && cubre(x, h))
                    .findFirst().orElse(null);
            if (f != null) {
                h.fila = f;
                libres.remove(f);
            }
        }
    }

    /** ¿Cubre la fila la unidad? (ajuste 4) Su SKU es el del pedido o uno de los nuestros que acepta, y se tecleó el día del
     *  pedido o después (la hora no cuenta). */
    private static boolean cubre(FilaPedidos2026 f, Hueco h) {
        boolean mismoSku = Objects.equals(Textos.sku(f.sku()), Textos.sku(h.sku)) || h.aceptacion.acepta(f.sku());
        return mismoSku && !f.fecha().toLocalDate().isBefore(h.fecha().toLocalDate());
    }

    // ---------------------------------------------------------------- Simulador

    /** El Simulador (spec §6.1; reventa tras un abono, decisión del usuario del 04/11). Se recorren las líneas por número de
     *  fila y cada IMEI tiene como mucho una unidad abierta: una línea que no es BB con un IMEI sin unidad abierta crea una en
     *  su pedido; con unidad abierta, repite ese móvil (X → E): no es unidad y, si es de otro pedido, su Order id va a
     *  {@code otroOrderId} (el primero). Una BB cierra por abono la unidad abierta de su IMEI, sea cual sea su SKU; si ese
     *  móvil se vuelve a vender, es otra unidad. Al final, la unidad que sigue abierta de un móvil en Released se cierra. Las
     *  líneas sin IMEI son unidades sin móvil. Una línea con un SKU que no conoce el maestro no cuenta para nada (si parece de
     *  teléfono, se apunta). La posición es la de la línea entre las del pedido que no son BB, sea unidad o no (ajuste 7). Un
     *  pedido sin unidades no sale; su fecha es la de su línea más antigua. */
    private static List<PedidoLeido> simulador(Entrada e, Set<String> desconocidos) {
        List<FilaSimulador> filas = e.simulador().stream().sorted(Comparator.comparingInt(FilaSimulador::fila)).toList();
        Map<String, List<FilaSimulador>> lineasDe = new LinkedHashMap<>();
        Map<String, List<UnidadSimulador>> unidadesDe = new HashMap<>();
        Map<String, UnidadSimulador> abierta = new HashMap<>();
        for (FilaSimulador f : filas) {
            String clave = f.imei() == null ? null : NsysDAO.claveCruce(f.imei());
            if ("BB".equals(f.linea())) {
                UnidadSimulador abonada = clave == null ? null : abierta.remove(clave);
                if (abonada != null) abonada.motivoCierre = "ABONO";
                continue;
            }
            List<FilaSimulador> lineas = lineasDe.computeIfAbsent(f.orderId(), k -> new ArrayList<>());
            lineas.add(f);
            if (e.acepta().para(Canal.SIMULADOR, f.sku()) == null) {
                if (Textos.pareceTelefono(f.sku())) desconocidos.add(f.sku());
                continue;
            }
            UnidadSimulador previa = clave == null ? null : abierta.get(clave);
            if (previa != null) {
                if (previa.otroOrderId == null && !Objects.equals(previa.linea.orderId(), f.orderId())) previa.otroOrderId = f.orderId();
                continue;
            }
            UnidadSimulador u = new UnidadSimulador(f, lineas.size());
            unidadesDe.computeIfAbsent(f.orderId(), k -> new ArrayList<>()).add(u);
            if (clave != null) abierta.put(clave, u);
        }
        abierta.forEach((clave, u) -> {
            if (vendido(e.moviles().get(clave))) u.motivoCierre = "RELEASED";
        });

        List<PedidoLeido> pedidos = new ArrayList<>();
        lineasDe.forEach((orderId, lineas) -> {
            List<UnidadSimulador> unidades = unidadesDe.get(orderId);
            if (unidades == null) return;
            LocalDate fecha = lineas.stream().map(FilaSimulador::fecha).min(Comparator.naturalOrder()).orElseThrow();
            String cliente = lineas.stream().map(FilaSimulador::cliente).filter(c -> c != null && !c.isBlank()).findFirst().orElse(null);
            pedidos.add(new PedidoLeido(Canal.SIMULADOR, orderId, cliente, null, fecha.atStartOfDay(), FuentePedidos.SIMULADOR,
                    unidades.stream().map(UnidadSimulador::unidad).toList()));
        });
        return pedidos;
    }

    /** Los IMEI de las unidades abiertas del Simulador: esos móviles ya tienen pedido y sus filas de PEDIDOS 2026 no cuentan. Los
     *  de una unidad cerrada (abono o Released) no bloquean nada. */
    private static Set<String> imeisAbiertos(List<PedidoLeido> pedidos) {
        Set<String> claves = new HashSet<>();
        for (PedidoLeido p : pedidos)
            for (PedidoLeido.Unidad u : p.unidades())
                if (u.imei() != null && u.motivoCierre() == null) claves.add(NsysDAO.claveCruce(u.imei()));
        return claves;
    }

    // ---------------------------------------------------------------- móviles

    private static MovilNsys movil(Map<String, MovilNsys> moviles, FilaPedidos2026 f) {
        return moviles.get(NsysDAO.claveCruce(f.imei()));
    }

    private static boolean vendido(MovilNsys m) {
        return m != null && m.estado() == EstadoMovil.VENDIDO;
    }

    /** 0 Listos, 1 taller (ubicación que empieza por «Taller», en mayúsculas o minúsculas), 2 el resto (sin revisar,
     *  devolución…), 3 sin el móvil en el espejo. Un móvil de pedido, por su última caja. */
    private static int avance(MovilNsys m) {
        if (m == null) return 3;
        String u = m.estado() == EstadoMovil.DE_PEDIDO ? m.ubicacionUltima() : m.ubicacion();
        if (u == null) return 2;
        String ubicacion = u.strip().toLowerCase(Locale.ROOT);
        if (ubicacion.equals("listos")) return 0;
        return ubicacion.startsWith("taller") ? 1 : 2;
    }

    private static LocalDate recepcion(MovilNsys m) {
        return m == null ? null : m.receivingDate();
    }
}
