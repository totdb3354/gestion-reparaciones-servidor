package com.reparaciones.servidor.model;

import java.time.LocalDateTime;

public class CompraOtro {
    private int           idCompraOtro;
    private int           idProv;
    private String        nombreProveedor;
    private String        concepto;
    private int           cantidad;
    private Integer       cantidadRecibida;
    private boolean       esUrgente;
    private LocalDateTime fechaPedido;
    private LocalDateTime fechaLlegada;
    private double        precioUnidadPedido;
    private String        divisa;
    private double        precioEur;
    private String        estado;
    private LocalDateTime updatedAt;

    public CompraOtro() {}

    public CompraOtro(int idCompraOtro, int idProv, String nombreProveedor, String concepto,
                      int cantidad, Integer cantidadRecibida, boolean esUrgente,
                      LocalDateTime fechaPedido, LocalDateTime fechaLlegada,
                      double precioUnidadPedido, String divisa, double precioEur,
                      String estado, LocalDateTime updatedAt) {
        this.idCompraOtro       = idCompraOtro;
        this.idProv             = idProv;
        this.nombreProveedor    = nombreProveedor;
        this.concepto           = concepto;
        this.cantidad           = cantidad;
        this.cantidadRecibida   = cantidadRecibida;
        this.esUrgente          = esUrgente;
        this.fechaPedido        = fechaPedido;
        this.fechaLlegada       = fechaLlegada;
        this.precioUnidadPedido = precioUnidadPedido;
        this.divisa             = divisa;
        this.precioEur          = precioEur;
        this.estado             = estado;
        this.updatedAt          = updatedAt;
    }

    public int           getIdCompraOtro()       { return idCompraOtro; }
    public int           getIdProv()             { return idProv; }
    public String        getNombreProveedor()    { return nombreProveedor; }
    public String        getConcepto()           { return concepto; }
    public int           getCantidad()           { return cantidad; }
    public Integer       getCantidadRecibida()   { return cantidadRecibida; }
    public boolean       isEsUrgente()           { return esUrgente; }
    public LocalDateTime getFechaPedido()        { return fechaPedido; }
    public LocalDateTime getFechaLlegada()       { return fechaLlegada; }
    public double        getPrecioUnidadPedido() { return precioUnidadPedido; }
    public String        getDivisa()             { return divisa; }
    public double        getPrecioEur()          { return precioEur; }
    public String        getEstado()             { return estado; }
    public LocalDateTime getUpdatedAt()          { return updatedAt; }
}
