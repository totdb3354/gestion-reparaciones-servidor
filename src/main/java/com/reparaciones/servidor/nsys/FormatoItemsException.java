package com.reparaciones.servidor.nsys;

/** El fichero no tiene la forma esperada (no es .xlsx, falta la hoja o faltan columnas): se rechaza sin aplicar nada y
 *  se conservan los datos anteriores (spec pieza 1 §5.1). */
public class FormatoItemsException extends RuntimeException {

    public FormatoItemsException(String mensaje) {
        super(mensaje);
    }
}
