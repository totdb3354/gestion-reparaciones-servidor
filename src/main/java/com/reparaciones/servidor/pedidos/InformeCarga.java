package com.reparaciones.servidor.pedidos;

import java.util.Map;

/** El informe de una carga del histórico: solo recuentos (ningún IMEI, pedido ni fila concreta). {@code plataformas}: las 15
 *  plataformas con más filas útiles, de más a menos (nombre → filas).
 *  <ul>
 *  <li>{@code filasLeidas}: filas de la hoja leídas (con las vacías de en medio); {@code cargadasHistorico}: filas creadas;
 *  {@code descartadasVacias}: sin Plataforma, SKU ni IMEI; {@code sinFechaHeredada} / {@code sinFechaDescartada}: sin fecha
 *  (tomó la de la fila anterior / no había ninguna); {@code imeiNoEsImei}: celdas de IMEI con algo que no son 15 cifras.</li>
 *  <li>{@code traspasadasA/B/C}: IMEI puestos en filas del ERP por cada regla; {@code yaPuesto}: la fila ya tenía ese IMEI;
 *  {@code conflicto}: la fila del pedido tenía otro; {@code casadasSinImei}: de un pedido del ERP pero sin IMEI que traspasar;
 *  {@code sinPareja}: sin fila a la que traspasar (se cargaron como histórico).</li>
 *  </ul> */
public record InformeCarga(int filasLeidas, int cargadasHistorico, int descartadasVacias, int sinFechaHeredada, int sinFechaDescartada,
                           int imeiNoEsImei, int traspasadasA, int traspasadasB, int traspasadasC, int yaPuesto, int conflicto,
                           int casadasSinImei, int sinPareja, Map<String, Integer> plataformas) {}
