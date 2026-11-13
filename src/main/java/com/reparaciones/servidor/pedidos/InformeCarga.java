package com.reparaciones.servidor.pedidos;

import java.util.Map;

/** El informe de una carga del histórico: solo recuentos (ningún IMEI, pedido ni fila concreta). {@code plataformas}: las 15
 *  plataformas con más filas útiles, de más a menos (nombre → filas).
 *  <ul>
 *  <li>{@code filasLeidas}: filas de la hoja leídas (con las vacías de en medio); {@code cargadasHistorico}: filas creadas;
 *  {@code descartadasVacias}: sin Plataforma, SKU ni IMEI; {@code sinFechaHeredada} / {@code sinFechaDescartada}: sin fecha
 *  (tomó la de la fila anterior / no había ninguna); {@code incidenciasDescartadas}: filas cuya Plataforma empieza por INCIDENCIA
 *  (sin espacios ni mayúsculas), que no se cargan ni cuentan para nada; {@code pendientesSinImeiNoCargadas}: filas de los últimos 14 días sin IMEI de 15 cifras
 *  (vacío o con un texto que no es IMEI) y sin número de pedido: son pedidos pendientes que el ERP ya tiene por ShippyPro, no se cargan
 *  ni entran en los traspasos; {@code imeiNoEsImei}: celdas de IMEI con algo que no son 15 cifras.</li>
 *  <li>{@code traspasadasA/B/C}: IMEI puestos en filas del ERP por cada regla; {@code yaPuesto}: la fila ya tenía ese IMEI;
 *  {@code conflicto}: la fila del pedido tenía otro; {@code casadasSinImei}: de un pedido del ERP pero sin IMEI que traspasar;
 *  {@code sinPareja}: sin fila a la que traspasar (se cargaron como histórico); {@code yaVendidoNsys}: candidatas de B o C
 *  cuyo IMEI NSYS da por vendido (no son de un pedido pendiente: se cargaron como histórico);
 *  {@code ventaAnterior}: su IMEI ya estaba en una fila de ShippyPro pero la fila de la hoja es de más de 7 días antes que esa fila
 *  (otra venta del mismo móvil: se cargó como histórico).</li>
 *  </ul>
 *  Cuadre: {@code filasLeidas = descartadasVacias + sinFechaDescartada + incidenciasDescartadas + pendientesSinImeiNoCargadas + cargadasHistorico + traspasadasA +
 *  traspasadasB + traspasadasC + yaPuesto + conflicto + casadasSinImei}. {@code sinPareja}, {@code yaVendidoNsys} y
 *  {@code ventaAnterior} son subconjuntos de {@code cargadasHistorico}, y {@code sinFechaHeredada} e {@code imeiNoEsImei} cuentan
 *  filas ya incluidas en otra de las sumas. */
public record InformeCarga(int filasLeidas, int cargadasHistorico, int descartadasVacias, int sinFechaHeredada, int sinFechaDescartada,
                           int incidenciasDescartadas, int pendientesSinImeiNoCargadas, int imeiNoEsImei, int traspasadasA, int traspasadasB, int traspasadasC, int yaPuesto, int conflicto,
                           int casadasSinImei, int sinPareja, int yaVendidoNsys, int ventaAnterior, Map<String, Integer> plataformas) {}
