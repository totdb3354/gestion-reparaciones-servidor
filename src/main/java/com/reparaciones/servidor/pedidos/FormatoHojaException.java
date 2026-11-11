package com.reparaciones.servidor.pedidos;

/** La hoja no tiene las columnas (o la pestaña) que hacen falta: la carga del histórico se rechaza. */
public class FormatoHojaException extends RuntimeException {
    public FormatoHojaException(String mensaje) {
        super(mensaje);
    }
}
