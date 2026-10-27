package com.reparaciones.servidor.nsys;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** Una fila de «Cambio de color» (spec 0.9.22 §3.2): el color de NSYS hoy y el nuevo como los escribe NSYS ({@code colorDe} →
 *  {@code colorA}; sin producto, el color nuevo es el del maestro), el producto de hoy y su grado (el que lleva la plantilla), el
 *  producto a poner ({@code ponerProducto}: null, «búscalo a mano»; {@code compruebalo}: sale del nombre del maestro tal cual), si
 *  el móvil está también en «Cambio de grado», por qué y desde cuándo (su cambio de color vigente) y la marca «Hecho» que vale
 *  ({@code hechoPor}, {@code hechoEn}; {@code hechoVolvio}: la importación vio NSYS igual y la fila cuenta otra vez). Desde la
 *  0.9.22 pieza 4 (§6), si es un vendido o de baja que ha vuelto: el día de la venta o de la baja y desde cuándo lo tiene el
 *  taller (null en la empresa). Horas de Madrid. */
public record FilaColorNsys(String imeiSn, @Schema(nullable = true) String modelo,
                            @Schema(nullable = true) String modeloMaestro, @Schema(nullable = true) String capacidad,
                            @Schema(nullable = true) String colorDe, @Schema(nullable = true) String colorA,
                            @Schema(nullable = true) String productoNsys, @Schema(nullable = true) String gradoNsys,
                            @Schema(nullable = true) String ponerProducto, boolean compruebalo, boolean tambienGrado,
                            List<PorQueTaller> porQue, LocalDateTime desde, @Schema(nullable = true) String hechoPor,
                            @Schema(nullable = true) LocalDateTime hechoEn, @Schema(nullable = true) LocalDateTime hechoVolvio,
                            @Schema(nullable = true) LocalDate vendidoEn, @Schema(nullable = true) LocalDate bajaEn,
                            @Schema(nullable = true) LocalDateTime vueltoDesde) {

    /** Un móvil en la empresa (sin vuelta). */
    public FilaColorNsys(String imeiSn, String modelo, String modeloMaestro, String capacidad, String colorDe, String colorA,
                         String productoNsys, String gradoNsys, String ponerProducto, boolean compruebalo, boolean tambienGrado,
                         List<PorQueTaller> porQue, LocalDateTime desde, String hechoPor, LocalDateTime hechoEn,
                         LocalDateTime hechoVolvio) {
        this(imeiSn, modelo, modeloMaestro, capacidad, colorDe, colorA, productoNsys, gradoNsys, ponerProducto, compruebalo,
                tambienGrado, porQue, desde, hechoPor, hechoEn, hechoVolvio, null, null, null);
    }
}
