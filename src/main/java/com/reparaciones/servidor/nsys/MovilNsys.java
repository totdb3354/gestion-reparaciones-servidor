package com.reparaciones.servidor.nsys;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** Una fila de Nsys_movil: lo que dice NSYS de un móvil y su traducción al ERP (spec pieza 1 §4.1). */
public record MovilNsys(String imeiSn, String batch, String goods, String grade, String status, String location,
                        String supplier, LocalDate receivingDate, LocalDate soldDate,
                        String sku, String modelo, String modeloMaestro, String capacidad, String color,
                        String gradoDetalle, boolean esEsim, String ubicacion, String ubicacionUltima,
                        EstadoMovil estado, LocalDateTime vistoPrimero, LocalDateTime cambiado) {

    public ClaveMovil clave() {
        return new ClaveMovil(imeiSn, batch);
    }

    /** Lo que NSYS dijo de este móvil, como si volviera a llegar en un fichero (para retraducir con un maestro nuevo). */
    public FilaItems comoFila() {
        return new FilaItems(imeiSn, batch, goods, grade, status, location, supplier, receivingDate, soldDate);
    }

    public MovilNsys conFechas(LocalDateTime vistoPrimero, LocalDateTime cambiado) {
        return new MovilNsys(imeiSn, batch, goods, grade, status, location, supplier, receivingDate, soldDate, sku,
                modelo, modeloMaestro, capacidad, color, gradoDetalle, esEsim, ubicacion, ubicacionUltima, estado,
                vistoPrimero, cambiado);
    }

    /** El mismo lote con otra fecha de recepción (0.9.21: la del primer lote de su cadena de correcciones). */
    public MovilNsys conRecepcion(LocalDate recepcion) {
        return new MovilNsys(imeiSn, batch, goods, grade, status, location, supplier, recepcion, soldDate, sku, modelo,
                modeloMaestro, capacidad, color, gradoDetalle, esEsim, ubicacion, ubicacionUltima, estado, vistoPrimero,
                cambiado);
    }

    /** Igual salvo las fechas de seguimiento (VISTO_PRIMERO y CAMBIADO). */
    public boolean mismoContenido(MovilNsys otro) {
        return otro != null && conFechas(null, null).equals(otro.conFechas(null, null));
    }
}
