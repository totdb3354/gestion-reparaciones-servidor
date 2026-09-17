package com.reparaciones.servidor.model;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

public class ReparacionResumen {
    private String        idRep;
    private String        imei;
    private String        nombreTecnico;
    private LocalDateTime fechaAsig;
    @Schema(nullable = true) private LocalDateTime fechaFin;
    @Schema(nullable = true) private String        tipoComponente;
    @Schema(nullable = true) private String        observaciones;
    private boolean       esIncidencia;
    private boolean       esResuelto;
    private boolean       esReutilizado;
    @Schema(nullable = true) private String        incidencia;
    @Schema(nullable = true) private String        idRepAnterior;
    private int           idTec;
    private int           esSolicitud;
    @Schema(nullable = true) private String        descripcionSolicitud;
    @Schema(nullable = true) private String        estadoSolicitud;
    @Schema(nullable = true) private String        tipoSolicitud;
    private int           stockSolicitud;
    private boolean       enCamino;
    @Schema(nullable = true) private String        tiposSolicitud;
    @Schema(nullable = true) private LocalDateTime updatedAt;
    @Schema(nullable = true) private String        modelo;
    @Schema(nullable = true) private String        comentarioAsignacion;
    @Schema(nullable = true) private String        observacionTelefono;
    private boolean       urgente;
    private boolean       esChasis;
    private boolean       porCerrar;
    private boolean       tieneAsignaciones;
    @Schema(nullable = true) private String        nombreTecnicoAsigna;
    @Schema(nullable = true) private LocalDateTime telefonoUpdatedAt;
    @Schema(nullable = true) private String        cliente;

    // Entrega a glass (spec 2026-08-28): reales en filas AG, derivados en filas A.
    @Schema(nullable = true) private LocalDateTime entregadoAt;              // AG: cuándo bajó el teléfono
    @Schema(nullable = true) private String        entregadoPorNombre;       // AG: quién lo bajó
    @Schema(nullable = true) private Integer       entregadoPor;             // AG: id de quién lo bajó (firma)
    private boolean       glassAbierta;             // A: hay AG abierta en el IMEI
    @Schema(nullable = true) private LocalDateTime glassEntregadoAt;         // A: entrega sellada en esa AG
    @Schema(nullable = true) private String        glassEntregadoPorNombre;  // A: quién la selló
    @Schema(nullable = true) private Integer       glassEntregadoPor;        // A: id de quién la selló (firma)
    @Schema(nullable = true) private String        glassTecnicoNombre;       // A: dueño actual de esa AG
    private boolean       normalAbierta;            // AG: hay reparación normal abierta en el IMEI (alguien arriba debe entregar)
    @Schema(nullable = true) private String        normalTecnicoNombre;      // AG: dueño de esa normal (la más antigua)

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
    public boolean       isEsChasis()                    { return esChasis; }
    public void          setEsChasis(boolean esChasis)   { this.esChasis = esChasis; }
    public boolean       isPorCerrar()                   { return porCerrar; }
    public void          setPorCerrar(boolean v)          { this.porCerrar = v; }
    public boolean       isTieneAsignaciones()           { return tieneAsignaciones; }
    public void          setTieneAsignaciones(boolean v) { this.tieneAsignaciones = v; }
    public String        getNombreTecnicoAsigna()             { return nombreTecnicoAsigna; }
    public void          setNombreTecnicoAsigna(String v)     { this.nombreTecnicoAsigna = v; }
    public LocalDateTime getTelefonoUpdatedAt()               { return telefonoUpdatedAt; }
    public void          setTelefonoUpdatedAt(LocalDateTime v){ this.telefonoUpdatedAt = v; }
    public String        getCliente()                         { return cliente; }
    public void          setCliente(String cliente)           { this.cliente = cliente; }
    public LocalDateTime getEntregadoAt()                          { return entregadoAt; }
    public void          setEntregadoAt(LocalDateTime v)           { this.entregadoAt = v; }
    public String        getEntregadoPorNombre()                   { return entregadoPorNombre; }
    public void          setEntregadoPorNombre(String v)           { this.entregadoPorNombre = v; }
    public Integer       getEntregadoPor()                         { return entregadoPor; }
    public void          setEntregadoPor(Integer v)                { this.entregadoPor = v; }
    public boolean       isGlassAbierta()                          { return glassAbierta; }
    public void          setGlassAbierta(boolean v)                { this.glassAbierta = v; }
    public LocalDateTime getGlassEntregadoAt()                     { return glassEntregadoAt; }
    public void          setGlassEntregadoAt(LocalDateTime v)      { this.glassEntregadoAt = v; }
    public String        getGlassEntregadoPorNombre()              { return glassEntregadoPorNombre; }
    public void          setGlassEntregadoPorNombre(String v)      { this.glassEntregadoPorNombre = v; }
    public Integer       getGlassEntregadoPor()                    { return glassEntregadoPor; }
    public void          setGlassEntregadoPor(Integer v)           { this.glassEntregadoPor = v; }
    public String        getGlassTecnicoNombre()                   { return glassTecnicoNombre; }
    public void          setGlassTecnicoNombre(String v)           { this.glassTecnicoNombre = v; }
    public boolean       isNormalAbierta()                         { return normalAbierta; }
    public void          setNormalAbierta(boolean v)               { this.normalAbierta = v; }
    public String        getNormalTecnicoNombre()                  { return normalTecnicoNombre; }
    public void          setNormalTecnicoNombre(String v)          { this.normalTecnicoNombre = v; }
}
