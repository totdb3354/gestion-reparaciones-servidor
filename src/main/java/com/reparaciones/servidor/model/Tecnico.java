package com.reparaciones.servidor.model;

public class Tecnico {
    private int idTec;
    private String nombre;
    private boolean activo;
    private boolean esEstadistica;
    private boolean esGlass;   // habilitado para la glass automática (spec 2026-09-05-glass-prediccion)

    public Tecnico() {}

    public Tecnico(int idTec, String nombre, boolean activo, boolean esEstadistica, boolean esGlass) {
        this.idTec         = idTec;
        this.nombre        = nombre;
        this.activo        = activo;
        this.esEstadistica = esEstadistica;
        this.esGlass       = esGlass;
    }

    public int     getIdTec()         { return idTec; }
    public String  getNombre()        { return nombre; }
    public boolean isActivo()         { return activo; }
    public boolean isEsEstadistica()  { return esEstadistica; }
    public boolean isEsGlass()        { return esGlass; }
}
