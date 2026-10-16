package com.reparaciones.servidor.nsys;

import java.time.LocalDateTime;

/** Una fila de Nsys_evento. En UBICACION, {@code de} y {@code a} son ubicaciones del ERP (o la caja de NSYS si no se
 *  traduce) y {@code aNsys} la caja de NSYS de destino tal cual. */
public record EventoNsys(String imeiSn, String batch, LocalDateTime fecha, boolean soloDia, TipoEvento tipo,
                         String de, String a, String aNsys) {}
