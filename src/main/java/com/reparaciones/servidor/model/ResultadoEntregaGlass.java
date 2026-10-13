package com.reparaciones.servidor.model;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Respuesta de entregar o deshacer la entrega y de marcar o deshacer la llegada (spec 2026-10-13 §4.1). Al entregar o marcar la
 * llegada: {@code abrir} dice qué pasó con el «Abrir», {@code idRepAbrir} es el creado y {@code tecnicoAbrir} a quién se apuntó.
 * Al deshacer: {@code abrir}, {@code idRepAbrir} y {@code tecnicoAbrir} nulos e {@code idRepBorrado} el «Abrir» borrado (nulo si
 * no había).
 */
public record ResultadoEntregaGlass(@Schema(nullable = true) EstadoAbrir abrir,
                                    @Schema(nullable = true) String idRepAbrir,
                                    @Schema(nullable = true) String tecnicoAbrir,
                                    @Schema(nullable = true) String idRepBorrado) {

    public static ResultadoEntregaGlass creado(String idRepAbrir, String tecnico) {
        return new ResultadoEntregaGlass(EstadoAbrir.CREADO, idRepAbrir, tecnico, null);
    }

    public static ResultadoEntregaGlass sinCrear(EstadoAbrir motivo, String tecnico) {
        return new ResultadoEntregaGlass(motivo, null, tecnico, null);
    }

    public static ResultadoEntregaGlass deshecho(String idRepBorrado) {
        return new ResultadoEntregaGlass(null, null, null, idRepBorrado);
    }
}
