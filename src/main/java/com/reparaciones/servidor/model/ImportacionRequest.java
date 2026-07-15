package com.reparaciones.servidor.model;

import java.math.BigDecimal;
import java.util.List;

/** Alta en bloque del importador. El servidor es agnóstico del parser (spec §3). */
public record ImportacionRequest(List<LoteImport> lotes) {

    public record LoteImport(String batchNumber, int idProv, String nota, List<TelefonoImport> telefonos) {}

    public record TelefonoImport(String imei, String modelo, Integer storageGb, String color,
                                 String gradoProveedor, BigDecimal precioCompra, String divisa,
                                 BigDecimal precioCompraEur, boolean esEsim) {}

    public record Respuesta(int lotes, int telefonos, List<String> conflictosOmitidos) {}
}
