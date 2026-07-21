package com.reparaciones.servidor.model;

public class Proveedor {
    private int idProv;
    private String nombre;
    private boolean activo;
    private String divisa;
    private String comentario;
    private String tipo;

    public Proveedor() {}

    public Proveedor(int idProv, String nombre, boolean activo, String divisa, String comentario, String tipo) {
        this.idProv     = idProv;
        this.nombre     = nombre;
        this.activo     = activo;
        this.divisa     = divisa;
        this.comentario = comentario;
        this.tipo       = tipo;
    }

    public int     getIdProv()     { return idProv; }
    public String  getNombre()     { return nombre; }
    public boolean isActivo()      { return activo; }
    public String  getDivisa()     { return divisa; }
    public String  getComentario() { return comentario; }
    public String  getTipo()       { return tipo; }
}
