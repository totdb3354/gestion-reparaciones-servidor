package com.reparaciones.servidor.model;

/** Hechos crudos de un IMEI existente para que el importador clasifique duplicados. */
public class VerificacionImei {
    private String imei;
    private boolean existe;
    private String estado;         // null = histórico
    private int trabajosAbiertos;  // asignaciones sin FECHA_FIN (A%, AG%, AP%)
    private String modelo;

    public VerificacionImei() {}

    public VerificacionImei(String imei, boolean existe, String estado, int trabajosAbiertos, String modelo) {
        this.imei = imei; this.existe = existe; this.estado = estado;
        this.trabajosAbiertos = trabajosAbiertos; this.modelo = modelo;
    }

    public String getImei()          { return imei; }
    public boolean isExiste()        { return existe; }
    public String getEstado()        { return estado; }
    public int getTrabajosAbiertos() { return trabajosAbiertos; }
    public String getModelo()        { return modelo; }
}
