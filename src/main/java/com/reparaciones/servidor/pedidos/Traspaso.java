package com.reparaciones.servidor.pedidos;

import com.reparaciones.servidor.pedidos.LectorHistorico.FilaHoja;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** El traspaso de los IMEI de la hoja a las filas del ERP (spec «Pedidos como la hoja» §8). Es una función pura: recibe las filas
 *  útiles de la hoja y las filas de ShippyPro que ya tiene el ERP, y dice qué IMEI pasan a qué fila y qué filas de la hoja se
 *  cargan como histórico (las demás son de un pedido que el ERP ya tiene y no se cargan). No lee ni escribe nada.
 *
 *  Reglas, en dos pasadas:
 *  A (toda la hoja primero). «Pedido Amazon» coincide con el PEDIDO de filas de ShippyPro de Amazon o Amazon Stockroom (vivas o
 *     cerradas). Las filas NP nunca van por A.
 *  Las filas de incidencia no llegan aquí: {@link LectorHistorico} las descarta antes (no se cargan ni cuentan para nada).
 *  B. Plataforma AMZ (Amazon) o FNSK/FSNK (Stockroom), sin «Pedido Amazon», IMEI de 15 cifras y fecha de los últimos 14 días: la
 *     fila PENDIENTE de ese canal con el mismo SKU y sin IMEI.
 *  C. Cualquier otra plataforma con nombre (no NP), IMEI de 15 cifras y fecha de los últimos 14 días: la fila
 *     PENDIENTE de un canal web cuya plataforma tiene el mismo nombre y el mismo SKU, sin IMEI.
 *  En B y C no son destino las filas cuyo PEDIDO aparece como «Pedido Amazon» en alguna fila de la hoja (son de A), ni los IMEI
 *  que NSYS da por vendidos (van al histórico). Si hay más filas de la hoja que pendientes libres, se quedan las más recientes.
 *  Un IMEI que ya está en cualquier fila de ShippyPro, o que se puso en este plan, no se pone en otra fila: «ya puesto». */
public final class Traspaso {

    /** Hasta cuántos días atrás vale una fila de la hoja para B y C. */
    static final int DIAS = 14;
    /** Una fila de la hoja de más de estos días antes que la fila del ERP que tiene su IMEI es una venta anterior del mismo móvil. */
    static final int DIAS_VENTA_ANTERIOR = 7;
    /** FECHA de una fila del ERP sin fecha: todo lo posterior cuenta como «ya puesto». */
    private static final LocalDate SIN_FECHA = LocalDate.of(1, 1, 1);

    public enum Via { A, B, C }

    /** Poner {@code imei} en la fila {@code idFila} del ERP. */
    public record Traslado(int idFila, String imei, Via via) {}

    /** Recuentos del traspaso. {@code casadasSinImei}: casaron por A pero su celda de IMEI no es un IMEI (ni se traspasan ni se
     *  cargan); {@code sinPareja}: B o C sin fila destino (se cargan como histórico); {@code yaPuesto}: ese IMEI ya estaba en una
     *  fila de ShippyPro (o ya se puso en este plan); {@code conflicto}: casó por A pero todas las filas del pedido tienen otro
     *  IMEI; {@code yaVendido}: candidatas de B o C cuyo IMEI NSYS da por vendido (se cargan como histórico);
     *  {@code ventaAnterior}: su IMEI ya está en una fila de ShippyPro pero la fila de la hoja es de más de {@value #DIAS_VENTA_ANTERIOR}
     *  días antes que esa fila: una venta anterior del mismo móvil (se carga como histórico). */
    public record Recuentos(int a, int b, int c, int yaPuesto, int conflicto, int casadasSinImei, int sinPareja, int yaVendido, int ventaAnterior) {}

    /** {@code historico}: las filas de la hoja que se cargan como histórico, en el orden de la hoja. */
    public record Plan(List<Traslado> traslados, List<FilaHoja> historico, Recuentos recuentos) {}

    private Traspaso() {}

    /** Una fila del ERP con su IMEI actual, que va cambiando según se reparte. */
    private static final class Destino {
        final FilaPedido fila;
        final String sku;
        String imei;

        Destino(FilaPedido fila) {
            this.fila = fila;
            this.sku = Textos.sku(fila.sku());
            this.imei = fila.imei() == null || fila.imei().isBlank() ? null : fila.imei().strip();
        }

        boolean libre() {
            return imei == null;
        }
    }

    private static final Comparator<Destino> POR_ANTIGUEDAD = Comparator
            .<Destino, LocalDate>comparing(d -> d.fila.fechaCompra(), Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(d -> d.fila.fecha(), Comparator.nullsLast(Comparator.naturalOrder())).thenComparingInt(d -> d.fila.id());
    private static final Comparator<Destino> POR_POSICION = Comparator.<Destino, Integer>comparing(d -> d.fila.posicion(),
            Comparator.nullsLast(Comparator.naturalOrder())).thenComparingInt(d -> d.fila.id());
    private static final Comparator<FilaHoja> POR_FECHA_DE_HOJA = Comparator.comparing(FilaHoja::fecha).thenComparingInt(FilaHoja::numero);

    /** Los IMEI de las filas de la hoja que B o C intentarían emparejar: para preguntar a NSYS cuáles ya salieron. */
    public static Set<String> imeisCandidatos(List<FilaHoja> hoja, LocalDate hoy) {
        LocalDate limite = hoy.minusDays(DIAS);
        Set<String> imeis = new HashSet<>();
        for (FilaHoja f : hoja) if (via(f, limite) != null) imeis.add(f.imei());
        return imeis;
    }

    /** ¿Es un pedido pendiente que el ERP ya tiene por ShippyPro? Fecha dentro de los últimos {@value #DIAS} días (la misma ventana
     *  que B y C), sin IMEI de 15 cifras (vacío o un texto que no es IMEI) y sin número de pedido. No se carga como histórico. */
    static boolean esPendienteSinImei(FilaHoja f, LocalDate hoy) {
        return !f.fecha().isBefore(hoy.minusDays(DIAS)) && !f.imeiValido() && (f.pedido() == null || f.pedido().isBlank());
    }

    /** B o C si la fila de la hoja es candidata a una de ellas (IMEI de 15 cifras, de los últimos 14 días, ni incidencia ni NP);
     *  si no, null. */
    private static Via via(FilaHoja f, LocalDate limite) {
        if (!f.imeiValido() || f.fecha().isBefore(limite) || esIncidenciaONp(f.plataforma())) return null;
        String p = f.plataforma() == null ? "" : f.plataforma().strip().toUpperCase(Locale.ROOT);
        boolean sinPedido = f.pedido() == null || f.pedido().isBlank();
        if (p.equals("AMZ") || p.equals("FNSK") || p.equals("FSNK")) return sinPedido ? Via.B : null;
        return p.isEmpty() ? null : Via.C;
    }

    /** Las incidencias y las filas NP nunca son de un pedido: siempre van al histórico. */
    private static boolean esIncidenciaONp(String plataforma) {
        if (plataforma == null) return false;
        String p = plataforma.strip().toUpperCase(Locale.ROOT);
        return p.equals("NP") || p.startsWith("INCIDENCIA");
    }

    private static LocalDate masAntigua(LocalDate x, LocalDate y) {
        return x.isBefore(y) ? x : y;
    }

    /** {@code vendidos}: los IMEI que NSYS da por vendidos; una candidata de B o C con uno de ellos va al histórico. */
    public static Plan planear(List<FilaHoja> hoja, List<FilaPedido> filasErp, LocalDate hoy, Set<String> vendidos) {
        List<Destino> destinos = new ArrayList<>();
        Map<String, LocalDate> puestos = new HashMap<>(); // IMEI -> FECHA mas antigua de las filas del ERP que lo tienen
        for (FilaPedido f : filasErp) {
            if (!"SHIPPYPRO".equals(f.origen())) continue;
            if (f.imei() != null && !f.imei().isBlank()) puestos.merge(f.imei().strip(), f.fecha() == null ? SIN_FECHA : f.fecha(), Traspaso::masAntigua);
            if (f.canal() != null) destinos.add(new Destino(f));
        }
        Map<String, List<Destino>> porPedido = new HashMap<>();
        for (Destino d : destinos)
            if (d.fila.canal().esAmazon() && d.fila.pedido() != null && !d.fila.pedido().isBlank())
                porPedido.computeIfAbsent(d.fila.pedido().strip(), k -> new ArrayList<>()).add(d);
        porPedido.values().forEach(l -> l.sort(POR_POSICION));
        Set<String> reclamados = new HashSet<>();
        for (FilaHoja f : hoja) if (f.pedido() != null && !f.pedido().isBlank()) reclamados.add(f.pedido().strip());

        LocalDate limite = hoy.minusDays(DIAS);
        boolean[] fuera = new boolean[hoja.size()]; // true = no se carga como historico
        List<Traslado> traslados = new ArrayList<>();
        int a = 0, b = 0, c = 0, yaPuesto = 0, conflicto = 0, sinImei = 0, sinPareja = 0, yaVendido = 0;

        // Pasada 1: A, con toda la hoja.
        for (int i = 0; i < hoja.size(); i++) {
            FilaHoja f = hoja.get(i);
            if (esIncidenciaONp(f.plataforma()) || f.pedido() == null || f.pedido().isBlank()) continue;
            List<Destino> grupo = porPedido.get(f.pedido().strip());
            if (grupo == null) continue;
            fuera[i] = true;
            if (!f.imeiValido()) {
                sinImei++;
                continue;
            }
            // Mismo numero de pedido = misma venta: si una fila del propio pedido ya tiene el IMEI, «ya puesto».
            if (grupo.stream().anyMatch(d -> f.imei().equals(d.imei))) {
                yaPuesto++;
                continue;
            }
            // El IMEI esta en una fila de OTRO pedido: si esa fila es mas de 7 dias anterior a la fila de la hoja, es una venta anterior
            // del movil (vendido, devuelto y revendido) y esta se traspasa; si es cercana o posterior, es «ya puesto».
            LocalDate otra = puestos.get(f.imei());
            if (otra != null && !otra.isBefore(f.fecha().minusDays(DIAS_VENTA_ANTERIOR))) {
                yaPuesto++;
                continue;
            }
            String sku = Textos.sku(f.sku());
            Destino libre = grupo.stream().filter(d -> d.libre() && sku != null && sku.equals(d.sku)).findFirst()
                    .or(() -> grupo.stream().filter(Destino::libre).findFirst()).orElse(null);
            if (libre == null) {
                conflicto++;
                continue;
            }
            libre.imei = f.imei();
            puestos.merge(f.imei(), libre.fila.fecha() == null ? SIN_FECHA : libre.fila.fecha(), Traspaso::masAntigua);
            traslados.add(new Traslado(libre.fila.id(), f.imei(), Via.A));
            a++;
        }

        // Pasada 2: candidatas de B y C, por grupo.
        Map<String, List<Integer>> candidatas = new LinkedHashMap<>();
        Map<String, Via> viaDelGrupo = new HashMap<>();
        Set<String> vistos = new HashSet<>();
        for (int i = 0; i < hoja.size(); i++) {
            FilaHoja f = hoja.get(i);
            if (fuera[i] || esIncidenciaONp(f.plataforma()) || !f.imeiValido()) continue;
            if (puestos.containsKey(f.imei())) continue; // ya puesto o venta anterior: se decide al final
            Via via = via(f, limite);
            if (via == null) continue;
            if (vendidos.contains(f.imei())) {
                yaVendido++;
                continue;
            }
            if (!vistos.add(f.imei())) continue; // el mismo IMEI otra vez: solo una fila puede colocarlo; la otra se decide al final
            String sku = Textos.sku(f.sku());
            if (sku == null) {
                sinPareja++;
                continue;
            }
            String canal = f.plataforma().strip().equalsIgnoreCase("AMZ") ? Canal.AMAZON.name() : Canal.AMAZON_STOCKROOM.name();
            String clave = via == Via.B ? "B|" + canal + "|" + sku : "C|" + Textos.nombre(f.plataforma()) + "|" + sku;
            candidatas.computeIfAbsent(clave, k -> new ArrayList<>()).add(i);
            viaDelGrupo.put(clave, via);
        }
        Map<String, List<Destino>> libres = new HashMap<>();
        for (Destino d : destinos) {
            if (!d.fila.viva() || !d.libre() || d.sku == null) continue;
            if (d.fila.pedido() != null && reclamados.contains(d.fila.pedido().strip())) continue;
            libres.computeIfAbsent("B|" + d.fila.canal().name() + "|" + d.sku, k -> new ArrayList<>()).add(d);
            String nombre = Textos.nombre(d.fila.plataforma());
            if (d.fila.canal().esWeb() && nombre != null) libres.computeIfAbsent("C|" + nombre + "|" + d.sku, k -> new ArrayList<>()).add(d);
        }
        libres.values().forEach(l -> l.sort(POR_ANTIGUEDAD));
        for (Map.Entry<String, List<Integer>> e : candidatas.entrySet()) {
            List<Destino> pendientes = libres.getOrDefault(e.getKey(), List.of());
            List<Integer> filas = new ArrayList<>(e.getValue());
            filas.sort((x, y) -> POR_FECHA_DE_HOJA.compare(hoja.get(x), hoja.get(y)));
            Via via = viaDelGrupo.get(e.getKey());
            // De la mas reciente a la mas antigua, cada una toma la PRIMERA pendiente libre que cumpla Fecha >= FECHA del destino - 7 dias
            // (destino sin fecha: vale), es decir, que repetir la carga la daria por «ya puesta». Si no cumple con ninguna libre, va al
            // historico: asi tampoco cumplira con las que queden libres despues y repetir da siempre lo mismo.
            List<Destino> libresDelGrupo = new ArrayList<>(pendientes);
            List<Object[]> colocadas = new ArrayList<>(); // {indice de la hoja, destino}
            for (int k = filas.size() - 1; k >= 0; k--) {
                int i = filas.get(k);
                Destino elegido = null;
                for (Destino d : libresDelGrupo)
                    if (d.fila.fecha() == null || !hoja.get(i).fecha().isBefore(d.fila.fecha().minusDays(DIAS_VENTA_ANTERIOR))) {
                        elegido = d;
                        break;
                    }
                if (elegido == null) {
                    sinPareja++;
                    continue;
                }
                libresDelGrupo.remove(elegido);
                colocadas.add(new Object[]{i, elegido});
            }
            java.util.Collections.reverse(colocadas); // en el orden de la hoja
            for (Object[] par : colocadas) {
                int i = (Integer) par[0];
                Destino d = (Destino) par[1];
                d.imei = hoja.get(i).imei();
                puestos.merge(d.imei, d.fila.fecha() == null ? SIN_FECHA : d.fila.fecha(), Traspaso::masAntigua);
                traslados.add(new Traslado(d.fila.id(), d.imei, via));
                fuera[i] = true;
                if (via == Via.B) b++;
                else c++;
            }
        }
        // Una fila de la hoja cuyo IMEI ya esta en una fila del ERP (o se acaba de colocar): si no es de mas de 7 dias antes que esa
        // fila es la misma venta («ya puesto», no se carga); si es anterior, es otra venta del mismo movil y va al historico.
        int ventaAnterior = 0;
        for (int i = 0; i < hoja.size(); i++) {
            FilaHoja f = hoja.get(i);
            if (fuera[i] || esIncidenciaONp(f.plataforma()) || !f.imeiValido()) continue;
            LocalDate fechaErp = puestos.get(f.imei());
            if (fechaErp == null) continue;
            if (!f.fecha().isBefore(fechaErp.minusDays(DIAS_VENTA_ANTERIOR))) {
                yaPuesto++;
                fuera[i] = true;
            } else {
                ventaAnterior++;
            }
        }
        List<FilaHoja> historico = new ArrayList<>();
        for (int i = 0; i < hoja.size(); i++) if (!fuera[i]) historico.add(hoja.get(i));
        return new Plan(List.copyOf(traslados), List.copyOf(historico),
                new Recuentos(a, b, c, yaPuesto, conflicto, sinImei, sinPareja, yaVendido, ventaAnterior));
    }
}
