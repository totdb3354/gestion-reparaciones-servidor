package com.reparaciones.servidor.pedidos;

import com.reparaciones.servidor.pedidos.LectorHistorico.FilaHoja;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** El traspaso de los IMEI de la hoja a las filas del ERP (spec «Pedidos como la hoja» §8). Es una función pura: recibe las filas
 *  útiles de la hoja y las filas de ShippyPro que ya tiene el ERP, y dice qué IMEI pasan a qué fila y qué filas de la hoja se
 *  cargan como histórico (las demás son de un pedido que el ERP ya tiene y no se cargan). No lee ni escribe nada.
 *
 *  Reglas, por este orden y sobre las filas de la hoja en su orden:
 *  A. «Pedido Amazon» coincide con el PEDIDO de filas de ShippyPro de Amazon o Amazon Stockroom (vivas o cerradas).
 *  B. Plataforma AMZ (Amazon) o FNSK/FSNK (Stockroom), sin «Pedido Amazon», IMEI de 15 cifras y fecha de los últimos 14 días: la
 *     fila PENDIENTE de ese canal con el mismo SKU y sin IMEI, de la más antigua a la más nueva.
 *  C. Cualquier otra plataforma con nombre (no INCIDENCIA… ni NP), IMEI de 15 cifras y fecha de los últimos 14 días: la fila
 *     PENDIENTE de un canal web cuya plataforma tiene el mismo nombre y el mismo SKU, sin IMEI.
 *  Una fila de destino recibe como mucho un IMEI y un IMEI se pone como mucho en una fila: si una fila del mismo grupo ya lo tiene,
 *  es «ya puesto» (también tras repetir la carga). */
public final class Traspaso {

    /** Hasta cuántos días atrás vale una fila de la hoja para B y C. */
    static final int DIAS = 14;

    public enum Via { A, B, C }

    /** Poner {@code imei} en la fila {@code idFila} del ERP. */
    public record Traslado(int idFila, String imei, Via via) {}

    /** Recuentos del traspaso. {@code casadasSinImei}: casaron por A pero su celda de IMEI no es un IMEI (ni se traspasan ni se
     *  cargan); {@code sinPareja}: B o C sin fila destino (se cargan como histórico); {@code yaPuesto}: la fila destino ya tenía ese
     *  IMEI; {@code conflicto}: casó por A pero todas las filas del pedido tienen otro IMEI. */
    public record Recuentos(int a, int b, int c, int yaPuesto, int conflicto, int casadasSinImei, int sinPareja) {}

    /** {@code historico}: las filas de la hoja que se cargan como histórico, en el orden de la hoja. */
    public record Plan(List<Traslado> traslados, List<FilaHoja> historico, Recuentos recuentos) {}

    private Traspaso() {}

    /** Una fila del ERP con su IMEI actual, que va cambiando según se reparte. */
    private static final class Destino {
        final FilaPedido fila;
        String imei;

        Destino(FilaPedido fila) {
            this.fila = fila;
            this.imei = fila.imei() == null || fila.imei().isBlank() ? null : fila.imei().strip();
        }

        boolean libre() {
            return imei == null;
        }
    }

    private static final Comparator<Destino> POR_ANTIGUEDAD = Comparator.<Destino, java.time.LocalDate>comparing(d -> d.fila.fecha(),
            Comparator.nullsLast(Comparator.naturalOrder())).thenComparingInt(d -> d.fila.id());
    private static final Comparator<Destino> POR_POSICION = Comparator.<Destino, Integer>comparing(d -> d.fila.posicion(),
            Comparator.nullsLast(Comparator.naturalOrder())).thenComparingInt(d -> d.fila.id());

    public static Plan planear(List<FilaHoja> hoja, List<FilaPedido> filasErp, LocalDate hoy) {
        List<Destino> destinos = new ArrayList<>();
        for (FilaPedido f : filasErp)
            if ("SHIPPYPRO".equals(f.origen()) && f.canal() != null) destinos.add(new Destino(f));
        destinos.sort(POR_ANTIGUEDAD);

        // A: por numero de pedido, solo Amazon y Amazon Stockroom.
        Map<String, List<Destino>> porPedido = new HashMap<>();
        for (Destino d : destinos)
            if (d.fila.canal().esAmazon() && d.fila.pedido() != null && !d.fila.pedido().isBlank())
                porPedido.computeIfAbsent(d.fila.pedido().strip(), k -> new ArrayList<>()).add(d);
        porPedido.values().forEach(l -> l.sort(POR_POSICION));

        // B y C: las pendientes por canal (B) o por canal web y nombre de plataforma (C), con su SKU; se comparan todas las del grupo
        // para el «ya puesto» y solo las libres para repartir.
        LocalDate limite = hoy.minusDays(DIAS);
        List<Traslado> traslados = new ArrayList<>();
        List<FilaHoja> historico = new ArrayList<>();
        int a = 0, b = 0, c = 0, yaPuesto = 0, conflicto = 0, sinImei = 0, sinPareja = 0;
        for (FilaHoja f : hoja) {
            String pedido = f.pedido() == null || f.pedido().isBlank() ? null : f.pedido().strip();
            List<Destino> grupoA = pedido == null ? null : porPedido.get(pedido);
            if (grupoA != null) {
                if (!f.imeiValido()) {
                    sinImei++;
                    continue;
                }
                String sku = Textos.sku(f.sku());
                if (grupoA.stream().anyMatch(d -> f.imei().equals(d.imei))) {
                    yaPuesto++;
                    continue;
                }
                Destino libre = grupoA.stream().filter(d -> d.libre() && sku != null && sku.equals(Textos.sku(d.fila.sku()))).findFirst()
                        .or(() -> grupoA.stream().filter(Destino::libre).findFirst()).orElse(null);
                if (libre == null) {
                    conflicto++;
                    continue;
                }
                libre.imei = f.imei();
                traslados.add(new Traslado(libre.fila.id(), f.imei(), Via.A));
                a++;
                continue;
            }
            Via via = null;
            if (pedido == null && f.imeiValido() && !f.fecha().isBefore(limite)) {
                String p = f.plataforma() == null ? "" : f.plataforma().strip().toUpperCase(Locale.ROOT);
                if (p.equals("AMZ")) via = Via.B;
                else if (p.equals("FNSK") || p.equals("FSNK")) via = Via.B;
            }
            if (via == null && f.imeiValido() && !f.fecha().isBefore(limite) && esWeb(f.plataforma())) via = Via.C;
            if (via == null) {
                historico.add(f);
                continue;
            }
            String sku = Textos.sku(f.sku());
            List<Destino> grupo = via == Via.B ? grupoB(destinos, f, sku) : grupoC(destinos, f, sku);
            LocalDate desde = f.fecha().minusDays(DIAS);
            if (grupo.stream().anyMatch(d -> f.imei().equals(d.imei) && (d.fila.viva() || !d.fila.fecha().isBefore(desde)))) {
                yaPuesto++;
                continue;
            }
            Destino libre = grupo.stream().filter(d -> d.fila.viva() && d.libre()).findFirst().orElse(null);
            if (libre == null) {
                sinPareja++;
                historico.add(f);
                continue;
            }
            libre.imei = f.imei();
            traslados.add(new Traslado(libre.fila.id(), f.imei(), via));
            if (via == Via.B) b++;
            else c++;
        }
        return new Plan(List.copyOf(traslados), List.copyOf(historico), new Recuentos(a, b, c, yaPuesto, conflicto, sinImei, sinPareja));
    }

    /** Las filas del canal de la plataforma (AMZ = Amazon; FNSK/FSNK = Stockroom) con el SKU de la fila de la hoja. */
    private static List<Destino> grupoB(List<Destino> destinos, FilaHoja f, String sku) {
        Canal canal = f.plataforma().strip().equalsIgnoreCase("AMZ") ? Canal.AMAZON : Canal.AMAZON_STOCKROOM;
        return destinos.stream().filter(d -> d.fila.canal() == canal && sku != null && sku.equals(Textos.sku(d.fila.sku()))).toList();
    }

    /** Las filas de un canal web cuya plataforma tiene el mismo nombre que la de la hoja, con el mismo SKU. */
    private static List<Destino> grupoC(List<Destino> destinos, FilaHoja f, String sku) {
        String nombre = Textos.nombre(f.plataforma());
        return destinos.stream().filter(d -> d.fila.canal().esWeb() && Objects.equals(nombre, Textos.nombre(d.fila.plataforma()))
                && sku != null && sku.equals(Textos.sku(d.fila.sku()))).toList();
    }

    /** ¿Es una venta web? Una plataforma con nombre que no es de Amazon, ni una incidencia, ni NP. */
    private static boolean esWeb(String plataforma) {
        if (plataforma == null || plataforma.isBlank()) return false;
        String p = plataforma.strip().toUpperCase(Locale.ROOT);
        return !(p.equals("AMZ") || p.equals("FNSK") || p.equals("FSNK") || p.equals("NP") || p.startsWith("INCIDENCIA"));
    }
}
