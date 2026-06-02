package com.reparaciones.servidor.model;

public class Telefono {
    private String imei;
    private String modelo;
    private String observacion;

    public Telefono() {}

    public Telefono(String imei) { this.imei = imei; }

    public Telefono(String imei, String modelo) {
        this.imei   = imei;
        this.modelo = modelo;
    }

    public String getImei()        { return imei; }
    public String getModelo()      { return modelo; }
    public String getObservacion() { return observacion; }
    public void setObservacion(String observacion) { this.observacion = observacion; }
}
