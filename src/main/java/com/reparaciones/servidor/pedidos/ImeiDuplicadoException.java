package com.reparaciones.servidor.pedidos;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/** El IMEI ya esta en otra fila de ShippyPro pendiente o con «falta por enviar» (spec 2026-11-18 §5). La lanza el DAO dentro de su
 *  transaccion, antes de escribir nada. */
public class ImeiDuplicadoException extends RuntimeException {
    private static final DateTimeFormatter DD_MM = DateTimeFormatter.ofPattern("dd/MM");
    private final String pedido;
    private final LocalDate fecha;
    private final Canal canal;
    private final boolean sinAsociar;

    public ImeiDuplicadoException(String pedido, LocalDate fecha, Canal canal, boolean sinAsociar) {
        super("IMEI duplicado");
        this.pedido = pedido; this.fecha = fecha; this.canal = canal; this.sinAsociar = sinAsociar;
    }
    public String pedido() { return pedido; }
    public LocalDate fecha() { return fecha; }
    public Canal canal() { return canal; }
    public boolean sinAsociar() { return sinAsociar; }

    public String mensaje() {
        String dia = fecha.format(DD_MM);
        if (sinAsociar) return "Este IMEI ya está en una fila de " + (canal == Canal.AMAZON_STOCKROOM ? "FNSK" : "AMZ") + " del " + dia + " sin asociar";
        return "Este IMEI ya está en el pedido " + pedido + " (fila del " + dia + ")";
    }
}
