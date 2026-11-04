package com.reparaciones.servidor.pedidos;

/** La hoja no tiene las columnas que hacen falta: la lectura se rechaza (RECHAZADA) y la píldora lo dice. */
public class FormatoHojaException extends RuntimeException {
    public FormatoHojaException(String mensaje) {
        super(mensaje);
    }
}
