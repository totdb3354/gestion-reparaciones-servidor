package com.reparaciones.servidor.model;

/** Punto del gráfico de estadísticas por puntos de dificultad (spec 2026-09-01). */
public class PuntoEstadisticaPuntos {
    private String nombreTecnico;
    private String periodo;
    private double puntos;
    private double puntosNormales;
    private double puntosGlass;
    private double puntosPulidos;
    private int    nNormales;
    private int    nGlass;
    private int    nPulidos;
    private int    nSinPiezas;

    public PuntoEstadisticaPuntos() {}

    public PuntoEstadisticaPuntos(String nombreTecnico, String periodo, double puntos,
                                  double puntosNormales, double puntosGlass, double puntosPulidos,
                                  int nNormales, int nGlass, int nPulidos, int nSinPiezas) {
        this.nombreTecnico = nombreTecnico;
        this.periodo = periodo;
        this.puntos = puntos;
        this.puntosNormales = puntosNormales;
        this.puntosGlass = puntosGlass;
        this.puntosPulidos = puntosPulidos;
        this.nNormales = nNormales;
        this.nGlass = nGlass;
        this.nPulidos = nPulidos;
        this.nSinPiezas = nSinPiezas;
    }

    public String getNombreTecnico()  { return nombreTecnico; }
    public String getPeriodo()        { return periodo; }
    public double getPuntos()         { return puntos; }
    public double getPuntosNormales() { return puntosNormales; }
    public double getPuntosGlass()    { return puntosGlass; }
    public double getPuntosPulidos()  { return puntosPulidos; }
    public int    getnNormales()      { return nNormales; }
    public int    getnGlass()         { return nGlass; }
    public int    getnPulidos()       { return nPulidos; }
    public int    getnSinPiezas()     { return nSinPiezas; }
}
