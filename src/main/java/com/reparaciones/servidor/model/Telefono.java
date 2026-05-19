package com.reparaciones.servidor.model;

public class Telefono {
    private String imei;
    private String modelo;

    public Telefono() {}

    public Telefono(String imei) { this.imei = imei; }

    public Telefono(String imei, String modelo) {
        this.imei   = imei;
        this.modelo = modelo;
    }

    public String getImei()   { return imei; }
    public String getModelo() { return modelo; }
}
