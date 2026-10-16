package com.reparaciones.servidor.nsys;

import java.time.LocalDate;

/** Una fila del fichero Items de NSYS, solo con las columnas que usa el ERP (sin costes ni precios). */
public record FilaItems(String imeiSn, String batch, String goods, String grade, String status, String location,
                        String supplier, LocalDate receivingDate, LocalDate soldDate) {

    public ClaveMovil clave() {
        return new ClaveMovil(imeiSn, batch);
    }
}
