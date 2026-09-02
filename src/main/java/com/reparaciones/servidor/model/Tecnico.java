package com.reparaciones.servidor.model;

public class Tecnico {
    private int idTec;
    private String nombre;
    private boolean activo;
    private boolean esEstadistica;

    public Tecnico() {}

    public Tecnico(int idTec, String nombre, boolean activo, boolean esEstadistica) {
        this.idTec         = idTec;
        this.nombre        = nombre;
        this.activo        = activo;
        this.esEstadistica = esEstadistica;
    }

    public int     getIdTec()         { return idTec; }
    public String  getNombre()        { return nombre; }
    public boolean isActivo()         { return activo; }
    public boolean isEsEstadistica()  { return esEstadistica; }
}
