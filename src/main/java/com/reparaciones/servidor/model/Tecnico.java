package com.reparaciones.servidor.model;

public class Tecnico {
    private int idTec;
    private String nombre;
    private boolean activo;

    public Tecnico() {}

    public Tecnico(int idTec, String nombre, boolean activo) {
        this.idTec  = idTec;
        this.nombre = nombre;
        this.activo = activo;
    }

    public int     getIdTec()  { return idTec; }
    public String  getNombre() { return nombre; }
    public boolean isActivo()  { return activo; }
}
