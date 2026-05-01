package com.reparaciones.servidor.model;

public class Telefono {
    private String imei;

    public Telefono() {}

    public Telefono(String imei) {
        this.imei = imei;
    }

    public String getImei() { return imei; }
}
