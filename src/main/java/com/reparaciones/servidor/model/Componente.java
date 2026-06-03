package com.reparaciones.servidor.model;

import java.time.LocalDateTime;

public class Componente {
    private int           idCom;
    private String        tipo;
    private LocalDateTime fechaRegistro;
    private int           stock;
    private int           stockMinimo;
    private boolean       activo;
    private LocalDateTime updatedAt;
    private int           enCamino;
    private LocalDateTime ultimoPedido;
    private Integer       idComMaster;

    public Componente() {}

    public Componente(int idCom, String tipo, LocalDateTime fechaRegistro,
                      int stock, int stockMinimo, boolean activo, LocalDateTime updatedAt) {
        this.idCom         = idCom;
        this.tipo          = tipo;
        this.fechaRegistro = fechaRegistro;
        this.stock         = stock;
        this.stockMinimo   = stockMinimo;
        this.activo        = activo;
        this.updatedAt     = updatedAt;
    }

    public int           getIdCom()        { return idCom; }
    public String        getTipo()          { return tipo; }
    public LocalDateTime getFechaRegistro() { return fechaRegistro; }
    public int           getStock()         { return stock; }
    public int           getStockMinimo()   { return stockMinimo; }
    public boolean       isActivo()         { return activo; }
    public LocalDateTime getUpdatedAt()     { return updatedAt; }
    public int           getEnCamino()      { return enCamino; }
    public LocalDateTime getUltimoPedido()  { return ultimoPedido; }

    public void setEnCamino(int enCamino)             { this.enCamino    = enCamino; }
    public void setUltimoPedido(LocalDateTime fecha)  { this.ultimoPedido = fecha; }
    public Integer getIdComMaster()                   { return idComMaster; }
    public void setIdComMaster(Integer idComMaster)   { this.idComMaster = idComMaster; }
}
