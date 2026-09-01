package com.reparaciones.servidor.model;

public class ValorDificultad {
    private String clave;
    private double puntos;

    public ValorDificultad() {}
    public ValorDificultad(String clave, double puntos) { this.clave = clave; this.puntos = puntos; }

    public String getClave()  { return clave; }
    public double getPuntos() { return puntos; }
}
