package com.reparaciones.servidor.pedidos;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Ultimo dia para enviar un pedido (spec 2026-11-18 §2). Amazon: la fecha de «Latest ship» del estado de ShippyPro. Webs: el dia de
 *  entrada mas 2 dias laborables si el envio es de 24/48 h, o 3 en cualquier otro caso. Puro. */
public final class FechaSalida {

    private static final Pattern LATEST_SHIP = Pattern.compile("Latest ship:\\s*(\\d{4}-\\d{2}-\\d{2})");
    static final int DIAS_48 = 2;
    static final int DIAS_72 = 3;

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
        if (canal.esWeb()) {
            int dias = p.servicio() != null && p.servicio().contains("24/48") ? DIAS_48 : DIAS_72;
            return CalendarioLaboral.sumarLaborables(p.fecha().toLocalDate(), dias, festivos);
        }
        return null;
    }
}
