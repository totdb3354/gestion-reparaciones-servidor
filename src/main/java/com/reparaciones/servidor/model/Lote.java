package com.reparaciones.servidor.model;

import java.time.LocalDateTime;

public class Lote {
    private int idLote;
    private String batchNumber;
    private int idProv;
    private String proveedor;
    private LocalDateTime fechaImport;
    private String nota;
    private int numTelefonos;
    private LocalDateTime updatedAt;

    public Lote() {}

    public Lote(int idLote, String batchNumber, int idProv, String proveedor,
                LocalDateTime fechaImport, String nota, int numTelefonos, LocalDateTime updatedAt) {
        this.idLote = idLote; this.batchNumber = batchNumber; this.idProv = idProv;
        this.proveedor = proveedor; this.fechaImport = fechaImport; this.nota = nota;
        this.numTelefonos = numTelefonos; this.updatedAt = updatedAt;
    }

    public int getIdLote()               { return idLote; }
    public String getBatchNumber()       { return batchNumber; }
    public int getIdProv()               { return idProv; }
    public String getProveedor()         { return proveedor; }
    public LocalDateTime getFechaImport(){ return fechaImport; }
    public String getNota()              { return nota; }
    public int getNumTelefonos()         { return numTelefonos; }
    public LocalDateTime getUpdatedAt()  { return updatedAt; }
}
