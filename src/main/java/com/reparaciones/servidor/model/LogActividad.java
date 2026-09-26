package com.reparaciones.servidor.model;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

public class LogActividad {
    private int idLog;
    private LocalDateTime fecha;
    private String nombreUsuario;
    private String accion;
    /** TEXT nulable: la mayoría de filas traen motivo null y algunas detalle null (spec 6 §4.6). */
    @Schema(nullable = true) private String detalle;
    @Schema(nullable = true) private String motivo;

    public LogActividad() {}

    public LogActividad(int idLog, LocalDateTime fecha, String nombreUsuario,
                        String accion, String detalle, String motivo) {
        this.idLog         = idLog;
        this.fecha         = fecha;
        this.nombreUsuario = nombreUsuario;
        this.accion        = accion;
        this.detalle       = detalle;
        this.motivo        = motivo;
    }

    public int           getIdLog()         { return idLog; }
    public LocalDateTime getFecha()         { return fecha; }
    public String        getNombreUsuario() { return nombreUsuario; }
    public String        getAccion()        { return accion; }
    public String        getDetalle()       { return detalle; }
    public String        getMotivo()        { return motivo; }
}
