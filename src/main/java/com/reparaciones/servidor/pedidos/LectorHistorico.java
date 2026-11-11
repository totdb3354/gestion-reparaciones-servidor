package com.reparaciones.servidor.pedidos;

import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** Las filas útiles de la hoja de pedidos (spec «Pedidos como la hoja» §8), por el nombre de la cabecera. Es puro: no toca la red
 *  ni la base de datos. Una fila entra si tiene algo en Plataforma, SKU o IMEI; la fecha que falta es la de la última fila con
 *  fecha; el IMEI llega como número (15 cifras) o como texto, y un error o un número imposible se guarda vacío. */
public final class LectorHistorico {

    public static final FormatoHoja FORMATO = new FormatoHoja(List.of("Fecha", "Plataforma", "SKU", "IMEI"),
            List.of("Pedido Amazon", "Fecha compra"));

    private static final Pattern QUINCE_CIFRAS = Pattern.compile("\\d{15}");
    private static final Pattern ERROR_DE_CELDA = Pattern.compile("#(N/A|REF!|VALUE!|DIV/0!|NAME\\?|NUM!|NULL!|ERROR!)",
            Pattern.CASE_INSENSITIVE);
    /** Un IMEI numérico con más cifras que esto no es un IMEI (ni el número de nadie): se guarda vacío. */
    private static final int MAX_CIFRAS = 18;

    private LectorHistorico() {}

    /** Una fila útil de la hoja. {@code imei} es el texto de la celda (null si está vacía, es un error o un número imposible). */
    public record FilaHoja(int numero, LocalDate fecha, String plataforma, String sku, String imei, String pedido,
                           LocalDate fechaCompra) {
        /** ¿Es un IMEI de 15 cifras? */
        public boolean imeiValido() {
            return imei != null && QUINCE_CIFRAS.matcher(imei).matches();
        }
    }

    /** Lo leído y los recuentos de lo que no entró. {@code plataformas}: filas útiles por plataforma (sin las de plataforma vacía). */
    public record Lectura(List<FilaHoja> filas, int leidas, int descartadasVacias, int sinFechaHeredada, int sinFechaDescartada,
                          int imeiNoImei, Map<String, Integer> plataformas) {}

    public static Lectura leer(TablaHoja t) {
        List<FilaHoja> filas = new ArrayList<>();
        Map<String, Integer> plataformas = new HashMap<>();
        int vacias = 0, heredadas = 0, descartadas = 0, noImei = 0;
        LocalDate ultimaFecha = null;
        for (TablaHoja.Fila f : t.filas()) {
            String plataforma = TablaHoja.texto(t.celda(f, "Plataforma"));
            String sku = TablaHoja.texto(t.celda(f, "SKU"));
            JsonNode celdaImei = t.celda(f, "IMEI");
            Imei imei = imei(celdaImei);
            var fechaCelda = TablaHoja.fecha(t.celda(f, "Fecha"));
            LocalDate fecha = fechaCelda == null ? null : fechaCelda.toLocalDate();
            if (fecha != null) ultimaFecha = fecha;
            // «Util»: algo en Plataforma, SKU o IMEI (una celda de IMEI con error o imposible cuenta como algo).
            boolean hayAlgo = plataforma != null || sku != null || imei.texto() != null || imei.vaciada();
            if (!hayAlgo) {
                vacias++;
                continue;
            }
            if (fecha == null) {
                if (ultimaFecha == null) {
                    descartadas++;
                    continue;
                }
                fecha = ultimaFecha;
                heredadas++;
            }
            if (imei.vaciada() || (imei.texto() != null && !QUINCE_CIFRAS.matcher(imei.texto()).matches())) noImei++;
            if (plataforma != null) plataformas.merge(plataforma, 1, Integer::sum);
            var compra = TablaHoja.fecha(t.celda(f, "Fecha compra"));
            filas.add(new FilaHoja(f.numero(), fecha, plataforma == null ? "" : plataforma, sku, imei.texto(),
                    TablaHoja.texto(t.celda(f, "Pedido Amazon")), compra == null ? null : compra.toLocalDate()));
        }
        return new Lectura(filas, t.filas().size(), vacias, heredadas, descartadas, noImei, plataformas);
    }

    /** El texto de una celda de IMEI; {@code vaciada}: había algo que no se puede guardar (error de celda o número imposible). */
    record Imei(String texto, boolean vaciada) {}

    static Imei imei(JsonNode v) {
        if (v == null || v.isNull() || v.isMissingNode()) return new Imei(null, false);
        if (v.isNumber()) {
            BigDecimal d = v.decimalValue();
            if (d.signum() <= 0 || d.stripTrailingZeros().scale() > 0) return new Imei(null, true);
            String cifras = d.toBigInteger().toString();
            return cifras.length() > MAX_CIFRAS ? new Imei(null, true) : new Imei(cifras, false);
        }
        String s = v.asText().strip();
        if (s.isEmpty()) return new Imei(null, false);
        if (ERROR_DE_CELDA.matcher(s).matches()) return new Imei(null, true);
        return new Imei(s, false);
    }
}
