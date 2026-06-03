package com.reparaciones.servidor.model;

import java.time.LocalDateTime;

public class ReparacionResumen {
    private String        idRep;
    private String        imei;
    private String        nombreTecnico;
    private LocalDateTime fechaAsig;
    private LocalDateTime fechaFin;
    private String        tipoComponente;
    private String        observaciones;
    private boolean       esIncidencia;
    private boolean       esResuelto;
    private boolean       esReutilizado;
    private String        incidencia;
    private String        idRepAnterior;
    private int           idTec;
    private int           esSolicitud;
    private String        descripcionSolicitud;
    private String        estadoSolicitud;
    private String        tipoSolicitud;
    private int           stockSolicitud;
    private boolean       enCamino;
    private String        tiposSolicitud;
    private LocalDateTime updatedAt;
    private String        modelo;
    private String        comentarioAsignacion;
    private String        observacionTelefono;
    private boolean       urgente;

    public ReparacionResumen() {}

    public ReparacionResumen(String idRep, String imei, String nombreTecnico,
                              LocalDateTime fechaAsig, LocalDateTime fechaFin,
                              String tipoComponente, String observaciones,
                              boolean esIncidencia, boolean esResuelto,
                              String incidencia, String idRepAnterior, int idTec,
                              int esSolicitud, String descripcionSolicitud,
                              String estadoSolicitud, String tipoSolicitud, int stockSolicitud,
                              boolean enCamino, String tiposSolicitud, LocalDateTime updatedAt,
                              String modelo) {
        this.idRep                = idRep;
        this.imei                 = imei;
        this.nombreTecnico        = nombreTecnico;
        this.fechaAsig            = fechaAsig;
        this.fechaFin             = fechaFin;
        this.tipoComponente       = tipoComponente;
        this.observaciones        = observaciones;
        this.esIncidencia         = esIncidencia;
        this.esResuelto           = esResuelto;
        this.incidencia           = incidencia;
        this.idRepAnterior        = idRepAnterior;
        this.idTec                = idTec;
        this.esSolicitud          = esSolicitud;
        this.descripcionSolicitud = descripcionSolicitud;
        this.estadoSolicitud      = estadoSolicitud;
        this.tipoSolicitud        = tipoSolicitud;
        this.stockSolicitud       = stockSolicitud;
        this.enCamino             = enCamino;
        this.tiposSolicitud       = tiposSolicitud;
        this.updatedAt            = updatedAt;
        this.modelo               = modelo;
    }

    public String        getIdRep()                { return idRep; }
    public String        getImei()                 { return imei; }
    public String        getNombreTecnico()        { return nombreTecnico; }
    public LocalDateTime getFechaAsig()            { return fechaAsig; }
    public LocalDateTime getFechaFin()             { return fechaFin; }
    public String        getTipoComponente()       { return tipoComponente; }
    public String        getObservaciones()        { return observaciones; }
    public boolean       isEsIncidencia()          { return esIncidencia; }
    public boolean       isEsResuelto()            { return esResuelto; }
    public boolean       isEsReutilizado()         { return esReutilizado; }
    public void          setEsReutilizado(boolean v) { this.esReutilizado = v; }
    public String        getIncidencia()           { return incidencia; }
    public String        getIdRepAnterior()        { return idRepAnterior; }
    public int           getIdTec()                { return idTec; }
    public int           getEsSolicitud()          { return esSolicitud; }
    public String        getDescripcionSolicitud() { return descripcionSolicitud; }
    public String        getEstadoSolicitud()      { return estadoSolicitud; }
    public String        getTipoSolicitud()        { return tipoSolicitud; }
    public int           getStockSolicitud()       { return stockSolicitud; }
    public boolean       isEnCamino()             { return enCamino; }
    public String        getTiposSolicitud()      { return tiposSolicitud; }
    public LocalDateTime getUpdatedAt()            { return updatedAt; }
    public String        getModelo()               { return modelo; }
    public String        getComentarioAsignacion()       { return comentarioAsignacion; }
    public void          setComentarioAsignacion(String c) { this.comentarioAsignacion = c; }
    public String        getObservacionTelefono()        { return observacionTelefono; }
    public void          setObservacionTelefono(String o)  { this.observacionTelefono = o; }
    public boolean       isUrgente()                     { return urgente; }
    public void          setUrgente(boolean urgente)     { this.urgente = urgente; }
}
