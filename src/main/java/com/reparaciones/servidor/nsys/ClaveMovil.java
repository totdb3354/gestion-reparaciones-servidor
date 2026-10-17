package com.reparaciones.servidor.nsys;

/** Un móvil en NSYS: IMEI/SN + lote. El IMEI se repite cuando vuelve a entrar; el par, nunca. */
public record ClaveMovil(String imeiSn, String batch) {}
