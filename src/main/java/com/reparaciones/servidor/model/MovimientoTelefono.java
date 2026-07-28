package com.reparaciones.servidor.model;

import java.time.LocalDateTime;

public class MovimientoTelefono {
    private int           idMov;
    private String        imei;
    private String        ubicacionOrigen;
    private String        ubicacionDestino;
    private LocalDateTime fecha;
    private Integer       idUsu;
    private String        usuario;
    private String        motivo;
    private String        referencia;

    public MovimientoTelefono() {}

    public int           getIdMov()              { return idMov; }
    public void          setIdMov(int v)         { this.idMov = v; }
    public String        getImei()               { return imei; }
    public void          setImei(String v)       { this.imei = v; }
    public String        getUbicacionOrigen()    { return ubicacionOrigen; }
    public void          setUbicacionOrigen(String v) { this.ubicacionOrigen = v; }
    public String        getUbicacionDestino()   { return ubicacionDestino; }
    public void          setUbicacionDestino(String v) { this.ubicacionDestino = v; }
    public LocalDateTime getFecha()              { return fecha; }
    public void          setFecha(LocalDateTime v) { this.fecha = v; }
    public Integer       getIdUsu()              { return idUsu; }
    public void          setIdUsu(Integer v)     { this.idUsu = v; }
    public String        getUsuario()            { return usuario; }
    public void          setUsuario(String v)    { this.usuario = v; }
    public String        getMotivo()             { return motivo; }
    public void          setMotivo(String v)     { this.motivo = v; }
    public String        getReferencia()         { return referencia; }
    public void          setReferencia(String v) { this.referencia = v; }
}
