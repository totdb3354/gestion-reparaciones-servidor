package com.reparaciones.servidor.model;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

public class Componente {
    private int           idCom;
    private String        tipo;
    private LocalDateTime fechaRegistro;
    private int           stock;
    private int           stockMinimo;
    private boolean       activo;
    private LocalDateTime updatedAt;
    private int           enCamino;
    @Schema(nullable = true) private LocalDateTime ultimoPedido;
    @Schema(nullable = true) private Integer       idComMaster;

    // Previsión de pedidos (spec 0.9.5 §3): solo para SUPERTECNICO y ADMIN y solo en componentes activos.
    @Schema(nullable = true) private Double  consumoDiario;
    @Schema(nullable = true) private Integer pedir60;

    // Pedido automático (spec 0.9.6 §4): la marca del master; solo para SUPERTECNICO y ADMIN.
    @Schema(nullable = true) private Boolean autoPedido;

    // Cantidad en líneas pendientes del master (0.9.9): solo para SUPERTECNICO y ADMIN, como la previsión.
    @Schema(nullable = true) private Integer pendiente;

    // Proveedores que tienen la pieza (su master) en su lista (0.9.9): solo para SUPERTECNICO y ADMIN.
    @ArraySchema(arraySchema = @Schema(nullable = true), schema = @Schema(implementation = Integer.class))
    private List<Integer> proveedores;

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

    public Double  getConsumoDiario() { return consumoDiario; }
    public Integer getPedir60()       { return pedir60; }

    public void setPrevision(double consumoDiario, int pedir60) {
        this.consumoDiario = consumoDiario;
        this.pedir60       = pedir60;
    }

    public Boolean getAutoPedido()                { return autoPedido; }
    public void    setAutoPedido(Boolean autoPedido) { this.autoPedido = autoPedido; }
    public Integer       getPendiente()                        { return pendiente; }
    public void          setPendiente(Integer pendiente)       { this.pendiente = pendiente; }
    public List<Integer> getProveedores()                      { return proveedores; }
    public void          setProveedores(List<Integer> proveedores) { this.proveedores = proveedores; }
}
