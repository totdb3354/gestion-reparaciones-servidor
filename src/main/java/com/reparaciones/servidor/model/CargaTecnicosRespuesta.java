package com.reparaciones.servidor.model;

import java.util.List;

/**
 * Carga diaria por técnico en los dos alcances a la vez (spec 3a §5): la ventana del cliente
 * alterna Pedidos|Total sin volver al servidor, así que se mandan juntos.
 */
public record CargaTecnicosRespuesta(List<FilaCarga> pedidos, List<FilaCarga> total) {

    /** Una fila: el técnico y sus porcentajes del día ya escalados a la jornada de hoy. */
    public record FilaCarga(int idTec, String nombre, double pctHecho, double pctPendiente,
                            DesgloseDto hecho, DesgloseDto pendiente, boolean sinJornada) {}

    /** Recuento por tipo de trabajo dentro de un tramo (hecho o pendiente). */
    public record DesgloseDto(int normales, int chasis, int porCerrar, int glass, int enEsperaPieza) {}
}
