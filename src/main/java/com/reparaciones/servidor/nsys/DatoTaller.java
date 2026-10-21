package com.reparaciones.servidor.nsys;

/** Los datos de un móvil que el taller cambia por encima de NSYS (spec 0.9.21 «SKU del taller» §1): el color (el del
 *  maestro, como Nsys_movil.COLOR) y el grado (como lo escribe NSYS en Nsys_movil.GRADE: lleva dentro la batería nueva,
 *  «100», y el mensaje de pieza, «Ms»). */
public enum DatoTaller {
    COLOR, GRADO;

    /** Lo que dice NSYS de este dato en una fila del espejo. */
    public String deNsys(MovilNsys m) {
        return this == COLOR ? m.color() : m.grade();
    }
}
