package com.reparaciones.servidor.pedidos;

/** El libro de ventas no se puede usar: falta la pestaña «Paso 1. Meter IMEI» o una de sus cabeceras, o el fichero no se puede leer. El
 *  mensaje va tal cual a la pasada del canal (lo enseña la web). */
public class LibroVentasException extends RuntimeException {
    public LibroVentasException(String mensaje) {
        super(mensaje);
    }
}
