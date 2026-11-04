package com.reparaciones.servidor.pedidos;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Las ventas con IMEI de PEDIDOS 2026 (spec pieza 2 §4.2 y ajuste 4). Una fila es venta con la regla de Deduccion_IMEIs.gs:
 *  Plataforma no vacía, ni NP, ni con INCIDENCIA o CANCEL. Sin IMEI de 15 cifras, sin SKU o sin fecha, no cuenta. */
public final class LectorPedidos2026 {

    public static final List<String> OBLIGATORIAS = List.of("Fecha", "Plataforma", "SKU", "IMEI");
    public static final FormatoHoja FORMATO = new FormatoHoja(OBLIGATORIAS, List.of("Pedido Amazon"), "Plataforma",
            LectorPedidos2026::esVenta);

    private LectorPedidos2026() {}

    public static List<FilaPedidos2026> filas(TablaHoja t) {
        List<FilaPedidos2026> ventas = new ArrayList<>();
        for (TablaHoja.Fila f : t.filas()) {
            String plataforma = TablaHoja.texto(t.celda(f, "Plataforma"));
            if (!esVenta(plataforma)) continue;
            String imei = Textos.imei(t.celda(f, "IMEI"));
            String sku = TablaHoja.texto(t.celda(f, "SKU"));
            LocalDateTime fecha = TablaHoja.fecha(t.celda(f, "Fecha"));
            if (imei == null || sku == null || fecha == null) continue;
            ventas.add(new FilaPedidos2026(f.numero(), fecha, plataforma, sku, imei,
                    TablaHoja.texto(t.celda(f, "Pedido Amazon"))));
        }
        return ventas;
    }

    static boolean esVenta(String plataforma) {
        if (plataforma == null) return false;
        String p = plataforma.strip().toUpperCase(Locale.ROOT);
        return !p.isEmpty() && !p.equals("NP") && !p.contains("INCIDENCIA") && !p.contains("CANCEL");
    }
}
