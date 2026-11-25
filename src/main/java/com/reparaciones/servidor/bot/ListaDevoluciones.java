package com.reparaciones.servidor.bot;

import java.util.List;

/** GET /api/bot/devoluciones (spec 2026-11-24-bot-devoluciones §6.1): los IMEI a devolver, agrupados por la caja de NSYS de destino,
 *  en orden de caja y de IMEI. motivo: IMEI_CORREGIDO (la reserva de IMEI añadira RESERVA_LIBERADA). */
public record ListaDevoluciones(List<Grupo> grupos) {
    public record Grupo(String destino, List<ImeiADevolver> imeis) {}
    public record ImeiADevolver(String imei, String motivo) {}
}
