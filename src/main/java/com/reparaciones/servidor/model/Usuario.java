package com.reparaciones.servidor.model;

public class Usuario {
    private int idUsu;
    private String nombreUsuario;
    private String rol;
    private Integer idTec;
    private String nombreTecnico;
    private boolean activo;

    public Usuario() {}

    public Usuario(int idUsu, String nombreUsuario, String rol,
                   Integer idTec, String nombreTecnico, boolean activo) {
        this.idUsu         = idUsu;
        this.nombreUsuario = nombreUsuario;
        this.rol           = rol;
        this.idTec         = idTec;
        this.nombreTecnico = nombreTecnico;
        this.activo        = activo;
    }

    public int     getIdUsu()         { return idUsu; }
    public String  getNombreUsuario() { return nombreUsuario; }
    public String  getRol()           { return rol; }
    public Integer getIdTec()         { return idTec; }
    public String  getNombreTecnico() { return nombreTecnico; }
    public boolean isActivo()         { return activo; }
}
