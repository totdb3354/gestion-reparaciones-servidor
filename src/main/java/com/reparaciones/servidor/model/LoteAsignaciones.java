package com.reparaciones.servidor.model;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/** Guardado por lotes del modal "Asignar trabajos" (spec 3b §4.2). Anidados: springdoc los publica como
 *  LoteAsignacionesPeticion, LoteAsignacionesTelefonoDelLote, etc. */
public final class LoteAsignaciones {
    private LoteAsignaciones() {}

    public record Peticion(List<TelefonoDelLote> telefonos, List<AsignacionDelLote> asignaciones) {}

    /** Un teléfono por IMEI. modelo vacío/null = conservar el de BD (COALESCE). */
    public record TelefonoDelLote(String imei, @Schema(nullable = true) String modelo,
                                  @Schema(nullable = true) Integer idCli, boolean clienteExplicito) {}

    /** categoria: "R" reparación, "G" glass, "P" pulido. esChasis solo cuenta en "R". */
    public record AsignacionDelLote(String imei, String categoria, int idTec,
                                    @Schema(nullable = true) String comentario, boolean esChasis) {}

    public record Respuesta(List<Creada> creadas, List<Conflicto> conflictos) {}

    public record Creada(String idRep, String imei, int idTec, String categoria) {}

    public record Conflicto(String imei, int idTec, String nombreTecnico, String categoria) {}
}
