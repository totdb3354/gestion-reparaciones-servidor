package com.reparaciones.servidor.pedidos;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Las líneas del Simulador (spec pieza 2 §4.3). Sin Order id, sin SKU o sin fecha, no cuentan. No lee precios, IVA, forma de
 *  pago ni nada de facturación. */
public final class LectorSimulador {

    public static final List<String> OBLIGATORIAS = List.of("Order id", "Linea facturación", "Fecha", "Cliente", "SKU",
            "Cantidad", "IMEI");
    public static final String CLAVE = "Order id";

    private LectorSimulador() {}

    public static List<FilaSimulador> filas(TablaHoja t) {
        List<FilaSimulador> lineas = new ArrayList<>();
        for (TablaHoja.Fila f : t.filas()) {
            String orderId = TablaHoja.texto(t.celda(f, "Order id"));
            String sku = TablaHoja.texto(t.celda(f, "SKU"));
            LocalDateTime fecha = TablaHoja.fecha(t.celda(f, "Fecha"));
            if (orderId == null || sku == null || fecha == null) continue;
            String linea = TablaHoja.texto(t.celda(f, "Linea facturación"));
            lineas.add(new FilaSimulador(f.numero(), orderId, linea == null ? "" : linea.toUpperCase(Locale.ROOT),
                    fecha.toLocalDate(), TablaHoja.texto(t.celda(f, "Cliente")), sku, cantidad(TablaHoja.texto(t.celda(f, "Cantidad"))),
                    Textos.imei(t.celda(f, "IMEI"))));
        }
        return lineas;
    }

    private static int cantidad(String texto) {
        if (texto == null) return 1;
        try {
            return new BigDecimal(texto).intValue();
        } catch (NumberFormatException e) {
            return 1;
        }
    }
}
