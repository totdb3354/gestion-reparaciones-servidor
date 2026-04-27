package com.reparaciones.servidor.model;

public class PuntoStock {
    private String periodo;
    private String tipoComponente;
    private int    stockEstimado;
    private int    stockMinimo;

    public PuntoStock() {}

    public PuntoStock(String periodo, String tipoComponente, int stockEstimado, int stockMinimo) {
        this.periodo        = periodo;
        this.tipoComponente = tipoComponente;
        this.stockEstimado  = stockEstimado;
        this.stockMinimo    = stockMinimo;
    }

    public String getPeriodo()        { return periodo; }
    public String getTipoComponente() { return tipoComponente; }
    public int    getStockEstimado()  { return stockEstimado; }
    public int    getStockMinimo()    { return stockMinimo; }
}
