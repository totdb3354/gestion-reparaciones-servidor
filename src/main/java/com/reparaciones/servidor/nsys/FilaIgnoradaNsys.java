package com.reparaciones.servidor.nsys;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** Un móvil ignorado de «Por pasar a NSYS» (spec 0.9.27 §4.2), para «Ver ignorados»: lo de su fila para su descripción (como las
 *  demás filas), su vuelta (el día de la venta o de la baja y desde cuándo lo tiene el taller), quién ({@code ignoradoPor}: su
 *  nombre, null si ya no existe) y cuándo lo ignoró (hora de Madrid), y en qué tarjetas saldría sin el ignorado
 *  ({@code tarjetas}: COLOR, GRADO y REVISAR, en ese orden). */
public record FilaIgnoradaNsys(String imeiSn, @Schema(nullable = true) String modelo, @Schema(nullable = true) String modeloMaestro,
                               @Schema(nullable = true) String capacidad, boolean esEsim, @Schema(nullable = true) LocalDate vendidoEn,
                               @Schema(nullable = true) LocalDate bajaEn, LocalDateTime vueltoDesde,
                               @Schema(nullable = true) String ignoradoPor, LocalDateTime ignoradoEn, List<String> tarjetas) {

    /** La misma fila con estas tarjetas. */
    FilaIgnoradaNsys conTarjetas(List<String> otras) {
        return new FilaIgnoradaNsys(imeiSn, modelo, modeloMaestro, capacidad, esEsim, vendidoEn, bajaEn, vueltoDesde, ignoradoPor,
                ignoradoEn, List.copyOf(otras));
    }
}
