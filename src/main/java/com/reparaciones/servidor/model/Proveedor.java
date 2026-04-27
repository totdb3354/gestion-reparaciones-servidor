package com.reparaciones.servidor.model;

public class Proveedor {
    private int idProv;
    private String nombre;
    private boolean activo;
    private String divisa;

    public Proveedor() {}

    public Proveedor(int idProv, String nombre, boolean activo, String divisa) {
        this.idProv  = idProv;
        this.nombre  = nombre;
        this.activo  = activo;
        this.divisa  = divisa;
    }

    public int     getIdProv() { return idProv; }
    public String  getNombre() { return nombre; }
    public boolean isActivo()  { return activo; }
    public String  getDivisa() { return divisa; }
}
