package com.reparaciones.servidor.model;

import io.swagger.v3.oas.annotations.media.Schema;

public class FilaReparacion {
    public int     idCom;
    public int     cantidad;
    public boolean reutilizado;
    @Schema(nullable = true) public String  observacion;
    @Schema(nullable = true) public String  prefijo;
    public boolean esSolicitud;
    @Schema(nullable = true) public String  descripcionSolicitud;
    @Schema(nullable = true) public String  estadoSolicitud;
    public boolean enCamino;
}
