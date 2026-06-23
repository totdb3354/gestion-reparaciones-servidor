package com.reparaciones.servidor.model;

import java.time.LocalDateTime;

public class Telefono {
    private String imei;
    private String modelo;
    private String observacion;
    private Integer idCli;
    private LocalDateTime updatedAt;

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
    public Integer getIdCli()      { return idCli; }
    public void setIdCli(Integer idCli) { this.idCli = idCli; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
