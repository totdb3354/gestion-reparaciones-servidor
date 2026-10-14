package com.reparaciones.servidor.model;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

public class CompraOtro {
    private int           idCompraOtro;
    private int           idProv;
    private String        nombreProveedor;
    private String        concepto;
    private int           cantidad;
    @Schema(nullable = true) private Integer       cantidadRecibida;
    private boolean       esUrgente;
    private LocalDateTime fechaPedido;
    @Schema(nullable = true) private LocalDateTime fechaLlegada;
    private double        precioUnidadPedido;
    private String        divisa;
    private double        precioEur;
    private String        estado;
    private LocalDateTime updatedAt;

    // 0.9.15: datos del bloque del otro, igual que CompraComponente (spec «Añadir otro» §4.4). Nulos solo en el hueco de
    // un despliegue.
    @Schema(nullable = true) private Integer       idBloque;
    @Schema(nullable = true) private LocalDateTime fechaBloque;
    @Schema(nullable = true) private String        notaBloque;
    @Schema(nullable = true) private LocalDateTime bloqueUpdatedAt;
    private boolean       bloqueAntiguo;

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

    /** Rellena los datos del bloque (los pone el MAPPER del DAO). Los tests que no los necesitan usan solo el
     *  constructor, que los deja vacíos. */
    public CompraOtro conBloque(Integer idBloque, LocalDateTime fechaBloque, String notaBloque,
                                LocalDateTime bloqueUpdatedAt, boolean bloqueAntiguo) {
        this.idBloque        = idBloque;
        this.fechaBloque     = fechaBloque;
        this.notaBloque      = notaBloque;
        this.bloqueUpdatedAt = bloqueUpdatedAt;
        this.bloqueAntiguo   = bloqueAntiguo;
        return this;
    }

    public Integer       getIdBloque()           { return idBloque; }
    public LocalDateTime getFechaBloque()        { return fechaBloque; }
    public String        getNotaBloque()         { return notaBloque; }
    public LocalDateTime getBloqueUpdatedAt()    { return bloqueUpdatedAt; }
    public boolean       isBloqueAntiguo()       { return bloqueAntiguo; }
}
