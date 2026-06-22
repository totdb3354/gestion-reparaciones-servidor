package com.reparaciones.servidor.model;

import java.time.LocalDateTime;

public class LogActividad {
    private int idLog;
    private LocalDateTime fecha;
    private String nombreUsuario;
    private String accion;
    private String detalle;
    private String motivo;

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
