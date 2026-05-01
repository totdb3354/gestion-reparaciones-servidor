package com.reparaciones.servidor.model;

public class PuntoEstadistica {
    private String nombreTecnico;
    private String periodo;
    private int    cantidad;

    public PuntoEstadistica() {}

    public PuntoEstadistica(String nombreTecnico, String periodo, int cantidad) {
        this.nombreTecnico = nombreTecnico;
        this.periodo       = periodo;
        this.cantidad      = cantidad;
    }

    public String getNombreTecnico() { return nombreTecnico; }
    public String getPeriodo()       { return periodo; }
    public int    getCantidad()      { return cantidad; }
}
