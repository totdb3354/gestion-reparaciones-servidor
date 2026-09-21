package com.reparaciones.servidor.model;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Objects;

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

    // Igualdad estructural (spec reintentos seguros): permite comparar la petición de un reintento
    // con la guardada aunque el cliente la reconstruya como una instancia distinta.
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FilaReparacion f)) return false;
        return idCom == f.idCom && cantidad == f.cantidad && reutilizado == f.reutilizado
                && esSolicitud == f.esSolicitud && enCamino == f.enCamino
                && Objects.equals(observacion, f.observacion) && Objects.equals(prefijo, f.prefijo)
                && Objects.equals(descripcionSolicitud, f.descripcionSolicitud)
                && Objects.equals(estadoSolicitud, f.estadoSolicitud);
    }

    @Override
    public int hashCode() {
        return Objects.hash(idCom, cantidad, reutilizado, observacion, prefijo, esSolicitud,
                descripcionSolicitud, estadoSolicitud, enCamino);
    }
}
