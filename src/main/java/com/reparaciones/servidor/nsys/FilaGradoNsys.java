package com.reparaciones.servidor.nsys;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** Una fila de «Cambio de grado» (spec 0.9.22 §3.3): el grado de NSYS hoy y el nuevo ({@code gradoDe} → {@code gradoA}, enteros,
 *  con el «100» y el «Ms»), el producto de NSYS hoy, si el móvil cambia también de color (primero el color), por qué, desde cuándo
 *  (su cambio de grado vigente) y la marca «Hecho» que vale, como en FilaColorNsys. Desde la 0.9.22 pieza 4 (§6), si es un vendido o de baja que ha vuelto: el
 *  día de la venta o de la baja y desde cuándo lo tiene el taller (null en la empresa). */
public record FilaGradoNsys(String imeiSn, @Schema(nullable = true) String modelo,
                            @Schema(nullable = true) String modeloMaestro, @Schema(nullable = true) String capacidad,
                            @Schema(nullable = true) String gradoDe, @Schema(nullable = true) String gradoA,
                            @Schema(nullable = true) String productoNsys, boolean conColor, List<PorQueTaller> porQue,
                            LocalDateTime desde, @Schema(nullable = true) String hechoPor,
                            @Schema(nullable = true) LocalDateTime hechoEn, @Schema(nullable = true) LocalDateTime hechoVolvio,
                            @Schema(nullable = true) LocalDate vendidoEn, @Schema(nullable = true) LocalDate bajaEn,
                            @Schema(nullable = true) LocalDateTime vueltoDesde) {

    /** Un móvil en la empresa (sin vuelta). */
    public FilaGradoNsys(String imeiSn, String modelo, String modeloMaestro, String capacidad, String gradoDe, String gradoA,
                         String productoNsys, boolean conColor, List<PorQueTaller> porQue, LocalDateTime desde, String hechoPor,
                         LocalDateTime hechoEn, LocalDateTime hechoVolvio) {
        this(imeiSn, modelo, modeloMaestro, capacidad, gradoDe, gradoA, productoNsys, conColor, porQue, desde, hechoPor, hechoEn,
                hechoVolvio, null, null, null);
    }
}
