package com.reparaciones.servidor.nsys;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Una fila del fichero Items de NSYS, solo con las columnas que usa el ERP. Desde la 0.9.22 lleva también el coste («Total
 *  cost», a dos decimales; null si no viene), que se guarda aparte (NsysDAO#guardarCostes) solo para la plantilla de NSYS. Sin
 *  precios de venta. */
public record FilaItems(String imeiSn, String batch, String goods, String grade, String status, String location,
                        String supplier, LocalDate receivingDate, LocalDate soldDate, BigDecimal coste) {

    /** Sin coste: las filas rehechas desde el espejo (MovilNsys#comoFila) y las pruebas que no lo necesitan. */
    public FilaItems(String imeiSn, String batch, String goods, String grade, String status, String location,
                     String supplier, LocalDate receivingDate, LocalDate soldDate) {
        this(imeiSn, batch, goods, grade, status, location, supplier, receivingDate, soldDate, null);
    }

    public ClaveMovil clave() {
        return new ClaveMovil(imeiSn, batch);
    }
}
