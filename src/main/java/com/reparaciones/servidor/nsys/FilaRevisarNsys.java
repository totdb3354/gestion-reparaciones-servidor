package com.reparaciones.servidor.nsys;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** Una fila de «Revisar a mano» (spec 0.9.22 §3.5): un aviso del taller ({@code tipo} SIN_COLOR o SIN_DESHACER;
 *  {@code causa} BORRADA o EDITADA en SIN_DESHACER; {@code dato} COLOR o GRADO; {@code de}, {@code a} y {@code motivo} como en
 *  Taller_aviso) con el modelo del espejo (null si el IMEI ya no está), su reparación, quién la guardó y cuándo. Desde la
 *  0.9.22 pieza 4 (§6), si es un vendido o de baja que ha vuelto: el día de la venta o de la baja y desde cuándo lo tiene el
 *  taller (null en la empresa). {@code esEsim} (0.9.24): del móvil del espejo (false si el IMEI ya no está). Desde la 0.9.27, {@code ignoradoAntesPor} e {@code ignoradoAntesEn}: quién y cuándo lo ignoraron, si un trabajo nuevo
 *  lo superó (null si no). */
public record FilaRevisarNsys(int idAviso, String imeiSn, @Schema(nullable = true) String modelo,
                              @Schema(nullable = true) String modeloMaestro, @Schema(nullable = true) String capacidad,
                              String tipo, @Schema(nullable = true) String causa, String dato, @Schema(nullable = true) String de,
                              @Schema(nullable = true) String a, @Schema(nullable = true) String motivo, String idRep,
                              @Schema(nullable = true) String usuario, LocalDateTime fecha,
                              @Schema(nullable = true) LocalDate vendidoEn, @Schema(nullable = true) LocalDate bajaEn,
                              @Schema(nullable = true) LocalDateTime vueltoDesde, boolean esEsim, @Schema(nullable = true) String ignoradoAntesPor,
                              @Schema(nullable = true) LocalDateTime ignoradoAntesEn) {

    /** Un móvil en la empresa (sin vuelta), sin eSIM. */
    public FilaRevisarNsys(int idAviso, String imeiSn, String modelo, String modeloMaestro, String capacidad, String tipo,
                           String causa, String dato, String de, String a, String motivo, String idRep, String usuario,
                           LocalDateTime fecha) {
        this(idAviso, imeiSn, modelo, modeloMaestro, capacidad, tipo, causa, dato, de, a, motivo, idRep, usuario, fecha, null,
                null, null, false, null, null);
    }

    /** Sin ignorado superado (como hasta la 0.9.26). */
    public FilaRevisarNsys(int idAviso, String imeiSn, String modelo, String modeloMaestro, String capacidad, String tipo,
                           String causa, String dato, String de, String a, String motivo, String idRep, String usuario,
                           LocalDateTime fecha, LocalDate vendidoEn, LocalDate bajaEn, LocalDateTime vueltoDesde, boolean esEsim) {
        this(idAviso, imeiSn, modelo, modeloMaestro, capacidad, tipo, causa, dato, de, a, motivo, idRep, usuario, fecha, vendidoEn,
                bajaEn, vueltoDesde, esEsim, null, null);
    }
}
