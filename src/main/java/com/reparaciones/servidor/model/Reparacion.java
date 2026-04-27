package com.reparaciones.servidor.model;

import java.time.LocalDateTime;

public class Reparacion {
    private String        idRep;
    private LocalDateTime fechaAsig;
    private LocalDateTime fechaFin;
    private String        imei;
    private int           idTec;
    private LocalDateTime updatedAt;

    public Reparacion() {}

    public Reparacion(String idRep, LocalDateTime fechaAsig, LocalDateTime fechaFin,
                      String imei, int idTec, LocalDateTime updatedAt) {
        this.idRep     = idRep;
        this.fechaAsig = fechaAsig;
        this.fechaFin  = fechaFin;
        this.imei      = imei;
        this.idTec     = idTec;
        this.updatedAt = updatedAt;
    }

    public String        getIdRep()     { return idRep; }
    public LocalDateTime getFechaAsig() { return fechaAsig; }
    public LocalDateTime getFechaFin()  { return fechaFin; }
    public String        getImei()      { return imei; }
    public int           getIdTec()     { return idTec; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
