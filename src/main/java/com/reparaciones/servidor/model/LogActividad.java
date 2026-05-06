package com.reparaciones.servidor.model;

import java.time.LocalDateTime;

public class LogActividad {
    private int idLog;
    private LocalDateTime fecha;
    private String nombreUsuario;
    private String accion;
    private String detalle;

    public LogActividad() {}

    public LogActividad(int idLog, LocalDateTime fecha, String nombreUsuario, String accion, String detalle) {
        this.idLog         = idLog;
        this.fecha         = fecha;
        this.nombreUsuario = nombreUsuario;
        this.accion        = accion;
        this.detalle       = detalle;
    }

    public int           getIdLog()         { return idLog; }
    public LocalDateTime getFecha()         { return fecha; }
    public String        getNombreUsuario() { return nombreUsuario; }
    public String        getAccion()        { return accion; }
    public String        getDetalle()       { return detalle; }
}
