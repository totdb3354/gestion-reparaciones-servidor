package com.reparaciones.servidor.model;

import java.time.LocalDateTime;

public class Revision {
    private int           idRevision;
    private String        imei;
    private LocalDateTime fechaCreacion;
    private String        estGrado;
    private String        estPant;
    private Integer       estIdUsu;
    private String        estUsuario;
    private LocalDateTime estFecha;
    private Integer       funBateriaPct;
    private boolean       funPantTactil;
    private boolean       funPantQuemada;
    private boolean       funPantMal;
    private boolean       funCamMancha;
    private boolean       funCamLente;
    private boolean       funAltSup;
    private boolean       funAltInf;
    private boolean       funMic;
    private boolean       funFaceId;
    private boolean       funMs;
    private String        funMsTexto;
    private boolean       funBloqueoOp;
    private String        funObservacion;
    private Integer       funIdUsu;
    private String        funUsuario;
    private LocalDateTime funFecha;

    public Revision() {}

    public int           getIdRevision()     { return idRevision; }
    public void          setIdRevision(int v) { this.idRevision = v; }
    public String        getImei()           { return imei; }
    public void          setImei(String v)   { this.imei = v; }
    public LocalDateTime getFechaCreacion()  { return fechaCreacion; }
    public void          setFechaCreacion(LocalDateTime v) { this.fechaCreacion = v; }
    public String        getEstGrado()       { return estGrado; }
    public void          setEstGrado(String v) { this.estGrado = v; }
    public String        getEstPant()        { return estPant; }
    public void          setEstPant(String v) { this.estPant = v; }
    public Integer       getEstIdUsu()       { return estIdUsu; }
    public void          setEstIdUsu(Integer v) { this.estIdUsu = v; }
    public String        getEstUsuario()     { return estUsuario; }
    public void          setEstUsuario(String v) { this.estUsuario = v; }
    public LocalDateTime getEstFecha()       { return estFecha; }
    public void          setEstFecha(LocalDateTime v) { this.estFecha = v; }
    public Integer       getFunBateriaPct()  { return funBateriaPct; }
    public void          setFunBateriaPct(Integer v) { this.funBateriaPct = v; }
    public boolean       isFunPantTactil()   { return funPantTactil; }
    public void          setFunPantTactil(boolean v) { this.funPantTactil = v; }
    public boolean       isFunPantQuemada()  { return funPantQuemada; }
    public void          setFunPantQuemada(boolean v) { this.funPantQuemada = v; }
    public boolean       isFunPantMal()      { return funPantMal; }
    public void          setFunPantMal(boolean v) { this.funPantMal = v; }
    public boolean       isFunCamMancha()    { return funCamMancha; }
    public void          setFunCamMancha(boolean v) { this.funCamMancha = v; }
    public boolean       isFunCamLente()     { return funCamLente; }
    public void          setFunCamLente(boolean v) { this.funCamLente = v; }
    public boolean       isFunAltSup()       { return funAltSup; }
    public void          setFunAltSup(boolean v) { this.funAltSup = v; }
    public boolean       isFunAltInf()       { return funAltInf; }
    public void          setFunAltInf(boolean v) { this.funAltInf = v; }
    public boolean       isFunMic()          { return funMic; }
    public void          setFunMic(boolean v) { this.funMic = v; }
    public boolean       isFunFaceId()       { return funFaceId; }
    public void          setFunFaceId(boolean v) { this.funFaceId = v; }
    public boolean       isFunMs()           { return funMs; }
    public void          setFunMs(boolean v) { this.funMs = v; }
    public String        getFunMsTexto()     { return funMsTexto; }
    public void          setFunMsTexto(String v) { this.funMsTexto = v; }
    public boolean       isFunBloqueoOp()    { return funBloqueoOp; }
    public void          setFunBloqueoOp(boolean v) { this.funBloqueoOp = v; }
    public String        getFunObservacion() { return funObservacion; }
    public void          setFunObservacion(String v) { this.funObservacion = v; }
    public Integer       getFunIdUsu()       { return funIdUsu; }
    public void          setFunIdUsu(Integer v) { this.funIdUsu = v; }
    public String        getFunUsuario()     { return funUsuario; }
    public void          setFunUsuario(String v) { this.funUsuario = v; }
    public LocalDateTime getFunFecha()       { return funFecha; }
    public void          setFunFecha(LocalDateTime v) { this.funFecha = v; }
}
