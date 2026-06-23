package com.reparaciones.servidor.model;

import java.time.LocalDateTime;

public class Cliente {
    private int idCli;
    private String nombre;
    private boolean activo;
    private LocalDateTime updatedAt;

    public Cliente() {}

    public Cliente(int idCli, String nombre, boolean activo, LocalDateTime updatedAt) {
        this.idCli     = idCli;
        this.nombre    = nombre;
        this.activo    = activo;
        this.updatedAt = updatedAt;
    }

    public int getIdCli()              { return idCli; }
    public void setIdCli(int idCli)    { this.idCli = idCli; }
    public String getNombre()          { return nombre; }
    public void setNombre(String n)    { this.nombre = n; }
    public boolean isActivo()          { return activo; }
    public void setActivo(boolean a)   { this.activo = a; }
    public LocalDateTime getUpdatedAt(){ return updatedAt; }
    public void setUpdatedAt(LocalDateTime u) { this.updatedAt = u; }
}
