package com.reparaciones.servidor.model;

public class Proveedor {
    private int idProv;
    private String nombre;
    private boolean activo;
    private String divisa;
    private String comentario;

    public Proveedor() {}

    public Proveedor(int idProv, String nombre, boolean activo, String divisa, String comentario) {
        this.idProv     = idProv;
        this.nombre     = nombre;
        this.activo     = activo;
        this.divisa     = divisa;
        this.comentario = comentario;
    }

    public int     getIdProv()     { return idProv; }
    public String  getNombre()     { return nombre; }
    public boolean isActivo()      { return activo; }
    public String  getDivisa()     { return divisa; }
    public String  getComentario() { return comentario; }
}
