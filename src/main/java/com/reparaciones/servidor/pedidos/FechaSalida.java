package com.reparaciones.servidor.pedidos;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Ultimo dia para enviar un pedido. Amazon: la fecha de «Latest ship» del estado de ShippyPro (spec 2026-11-18 §2). Webs (spec
 *  2026-11-23 «salida web 24 h»): el laborable siguiente al primer laborable desde la entrada, sin mirar el servicio de envio. Puro. */
public final class FechaSalida {

    private static final Pattern LATEST_SHIP = Pattern.compile("Latest ship:\\s*(\\d{4}-\\d{2}-\\d{2})");

    private FechaSalida() {}

    public static LocalDate de(PedidoShippyPro p, Canal canal, Set<LocalDate> festivos) {
        if (canal == null) return null;
        if (canal.esAmazon()) {
            if (p.estado() == null) return null;
            Matcher m = LATEST_SHIP.matcher(p.estado());
            if (!m.find()) return null;
            try {
                return LocalDate.parse(m.group(1));
            } catch (DateTimeParseException e) {
                return null;
            }
        }
        if (canal.esWeb()) return CalendarioLaboral.sumarLaborables(desde(canal, p.fecha().toLocalDate(), festivos), 1, festivos);
        return null;
    }

    /** Primer dia en que un pedido web ya «sale hoy»: la entrada si es laborable, si no el primer laborable siguiente. Nulo fuera de
     *  las webs o sin entrada. */
    public static LocalDate desde(Canal canal, LocalDate entrada, Set<LocalDate> festivos) {
        if (canal == null || !canal.esWeb() || entrada == null) return null;
        return CalendarioLaboral.primerLaborable(entrada, festivos);
    }
}
