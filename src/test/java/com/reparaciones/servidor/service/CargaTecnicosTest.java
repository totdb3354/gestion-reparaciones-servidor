package com.reparaciones.servidor.service;

import com.reparaciones.servidor.model.ReparacionResumen;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CargaTecnicosTest {

    /** Asignación abierta con los campos que usa el cálculo. El servidor no expone setters
     *  para idRep/idTec/esSolicitud/estadoSolicitud/fechaFin: se pasan por constructor. */
    private ReparacionResumen asig(String idRep, int idTec, String cliente,
                                   boolean chasis, boolean porCerrar) {
        return asig(idRep, idTec, cliente, chasis, porCerrar, null, 0, null, 0);
    }

    private ReparacionResumen asig(String idRep, int idTec, String cliente,
                                   boolean chasis, boolean porCerrar,
                                   java.time.LocalDateTime fechaFin,
                                   int esSolicitud, String estadoSolicitud, int stockSolicitud) {
        ReparacionResumen r = new ReparacionResumen(idRep, null, null, null, fechaFin, null, null,
                false, false, null, null, idTec, esSolicitud, null, estadoSolicitud, null,
                stockSolicitud, false, null, null, null);
        r.setCliente(cliente);
        r.setEsChasis(chasis);
        r.setPorCerrar(porCerrar);
        return r;
    }

    // Fracciones sobre jornada de 9h (lunes): 4 chasis + 5 glass = 4/8 + 5/17 = 79,4%
    /** Inicio de hoy (lunes 16/11/2026) tal como lo pasa CargaAsignacionesService. */
    private static final java.time.LocalDateTime HOY = java.time.LocalDateTime.of(2026, 11, 16, 0, 0);
    private static final java.time.DayOfWeek LUNES = java.time.DayOfWeek.MONDAY;   // 9h: pct = fracción × 100

    private ReparacionResumen porCerrar(String idRep, boolean chasis, java.time.LocalDateTime marcada) {
        ReparacionResumen r = asig(idRep, 1, "WEB", chasis, true);
        r.setPorCerrarAt(marcada);
        return r;
    }

    @Test void fraccionesSobreJornadaLarga() {
        List<ReparacionResumen> abiertas = new java.util.ArrayList<>();
        for (int i = 0; i < 4; i++) abiertas.add(asig("A_c" + i, 1, "WEB", true, false));
        for (int i = 0; i < 5; i++) abiertas.add(asig("AG_g" + i, 1, "WEB", false, false));
        var dia = CargaTecnicos.calcularDia(abiertas, List.of(), java.time.DayOfWeek.MONDAY, false);
        assertEquals(0.0, dia.get(1).pctHecho(), 0.01);
        assertEquals(79.41, dia.get(1).pctPendiente(), 0.05);
        assertFalse(dia.get(1).sinJornada());
    }

    // El viernes (6h) la misma carga pesa 9/6 más: 79,4 × 1,5 = 119,1% (>100 permitido)
    @Test void escaladoPorJornadaCorta() {
        List<ReparacionResumen> abiertas = new java.util.ArrayList<>();
        for (int i = 0; i < 4; i++) abiertas.add(asig("A_c" + i, 1, "WEB", true, false));
        for (int i = 0; i < 5; i++) abiertas.add(asig("AG_g" + i, 1, "WEB", false, false));
        var dia = CargaTecnicos.calcularDia(abiertas, List.of(), java.time.DayOfWeek.FRIDAY, false);
        assertEquals(119.11, dia.get(1).pctPendiente(), 0.1);
    }

    // Hecho hoy (asignaciones cerradas) va al tramo hecho, con los mismos pesos
    @Test void hechoHoySumaEnSuTramo() {
        var cerrada = asig("A_done", 1, "WEB", true, false,
                java.time.LocalDateTime.now(), 0, null, 0);
        var dia = CargaTecnicos.calcularDia(List.of(asig("A_p", 1, "WEB", false, false)),
                List.of(cerrada), java.time.DayOfWeek.MONDAY, false);
        assertEquals(100.0 / 8, dia.get(1).pctHecho(), 0.01);        // 1 chasis = 12,5%
        assertEquals(100.0 / CargaTecnicos.TOPE_NORMALES_9H, dia.get(1).pctPendiente(), 0.01);
        assertEquals(1, dia.get(1).hecho().chasis());
        assertEquals(1, dia.get(1).pendiente().normales());
    }

    // Vista Pedidos: sin cliente no cuenta; vista Total sí
    @Test void vistaPedidosFiltraPorCliente() {
        var conCliente = asig("A_1", 1, "WEB", false, false);
        var sinCliente = asig("A_2", 1, null, false, false);
        var pedidos = CargaTecnicos.calcularDia(List.of(conCliente, sinCliente), List.of(), java.time.DayOfWeek.MONDAY, true);
        var total   = CargaTecnicos.calcularDia(List.of(conCliente, sinCliente), List.of(), java.time.DayOfWeek.MONDAY, false);
        assertEquals(100.0 / CargaTecnicos.TOPE_NORMALES_9H,     pedidos.get(1).pctPendiente(), 0.01);
        assertEquals(2 * 100.0 / CargaTecnicos.TOPE_NORMALES_9H, total.get(1).pctPendiente(), 0.01);
    }

    // Por cerrar = 8,3% del tiempo de su tipo; solicitud pendiente libera (solo abiertas); pulido fuera
    @Test void reglasDePesoSeMantienenEnFracciones() {
        var porCerrarChasis = asig("A_pc", 1, "WEB", true, true);            // 0,083 × 1/8
        var conSolicitud    = asig("A_sol", 1, "WEB", false, false,
                null, 1, "PENDIENTE", 0);
        var pulido          = asig("AP_1", 1, "WEB", false, false);
        var dia = CargaTecnicos.calcularDia(List.of(porCerrarChasis, conSolicitud, pulido),
                List.of(), java.time.DayOfWeek.MONDAY, false);
        assertEquals((1.0 / 12) * (100.0 / 8), dia.get(1).pctPendiente(), 0.01);
        assertEquals(1, dia.get(1).pendiente().enEsperaPieza());
        assertEquals(0, dia.get(1).pendiente().normales());
    }

    // Sábado/domingo: sin jornada
    @Test void finDeSemanaSinJornada() {
        var dia = CargaTecnicos.calcularDia(List.of(asig("A_1", 1, "WEB", false, false)),
                List.of(), java.time.DayOfWeek.SATURDAY, false);
        assertTrue(dia.get(1).sinJornada());
        assertEquals(0.0, dia.get(1).pctTotal(), 0.001);
    }

    // Tope de normales = 20 (decisión del usuario 2026-07-24): una normal un lunes (9h) = 5%
    @Test void unaNormalEnLunesEsUnCincoPorCiento() {
        assertEquals(20, CargaTecnicos.TOPE_NORMALES_9H);
        var dia = CargaTecnicos.calcularDia(List.of(asig("A_1", 1, "WEB", false, false)),
                List.of(), java.time.DayOfWeek.MONDAY, false);
        assertEquals(5.0, dia.get(1).pctPendiente(), 1e-9);
    }

    // ── «Por cerrar» cuenta como hecho al marcar (spec 2026-11-16-carga-por-cerrar-hecho §2) ──

    @Test void marcadaHoyYAbiertaLlevaOnceDoceavosAHecho() {
        var dia = CargaTecnicos.calcularDia(List.of(porCerrar("A_1", false, HOY.plusHours(10))),
                List.of(), LUNES, false, HOY);
        assertEquals(5.0 * 11 / 12, dia.get(1).pctHecho(), 1e-9);
        assertEquals(5.0 / 12, dia.get(1).pctPendiente(), 1e-9);
        assertEquals(1, dia.get(1).hecho().porCerrar());          // sale en los dos tramos
        assertEquals(1, dia.get(1).pendiente().porCerrar());
    }

    @Test void marcadaHoyYAbiertaDeChasisUsaElPesoDeChasis() {
        var dia = CargaTecnicos.calcularDia(List.of(porCerrar("A_1", true, HOY.plusHours(10))),
                List.of(), LUNES, false, HOY);
        assertEquals(12.5 * 11 / 12, dia.get(1).pctHecho(), 1e-9);
        assertEquals(12.5 / 12, dia.get(1).pctPendiente(), 1e-9);
    }

    @Test void marcadaHoyYCerradaHoyCuentaEntera() {
        var dia = CargaTecnicos.calcularDia(List.of(),
                List.of(porCerrar("A_1", false, HOY.plusHours(10)), porCerrar("A_2", true, HOY.plusHours(11))),
                LUNES, false, HOY);
        assertEquals(5.0 + 12.5, dia.get(1).pctHecho(), 1e-9);
        assertEquals(0.0, dia.get(1).pctPendiente(), 1e-9);
        assertEquals(2, dia.get(1).hecho().porCerrar());
    }

    @Test void marcadaOtroDiaSigueComoAntes() {
        var ayer = HOY.minusSeconds(1);
        var abierta = CargaTecnicos.calcularDia(List.of(porCerrar("A_1", false, ayer)), List.of(), LUNES, false, HOY);
        assertEquals(0.0, abierta.get(1).pctHecho(), 1e-9);
        assertEquals(5.0 / 12, abierta.get(1).pctPendiente(), 1e-9);
        var cerrada = CargaTecnicos.calcularDia(List.of(), List.of(porCerrar("A_1", false, ayer)), LUNES, false, HOY);
        assertEquals(5.0 / 12, cerrada.get(1).pctHecho(), 1e-9);
    }

    @Test void sinFechaSigueComoAntes() {
        var abierta = CargaTecnicos.calcularDia(List.of(porCerrar("A_1", false, null)), List.of(), LUNES, false, HOY);
        assertEquals(0.0, abierta.get(1).pctHecho(), 1e-9);
        assertEquals(5.0 / 12, abierta.get(1).pctPendiente(), 1e-9);
        var cerrada = CargaTecnicos.calcularDia(List.of(), List.of(porCerrar("A_1", false, null)), LUNES, false, HOY);
        assertEquals(5.0 / 12, cerrada.get(1).pctHecho(), 1e-9);
    }

    @Test void justoALaMedianocheYaEsHoy() {
        var dia = CargaTecnicos.calcularDia(List.of(), List.of(porCerrar("A_1", false, HOY)), LUNES, false, HOY);
        assertEquals(5.0, dia.get(1).pctHecho(), 1e-9);
    }

    @Test void marcadaHoyEnEsperaDePiezaNoSumaNada() {
        ReparacionResumen r = asig("A_1", 1, "WEB", false, true, null, 1, "PENDIENTE", 0);
        r.setPorCerrarAt(HOY.plusHours(10));
        var dia = CargaTecnicos.calcularDia(List.of(r), List.of(), LUNES, false, HOY);
        assertEquals(0.0, dia.get(1).pctHecho(), 1e-9);
        assertEquals(0.0, dia.get(1).pctPendiente(), 1e-9);
        assertEquals(0, dia.get(1).hecho().porCerrar());
        assertEquals(1, dia.get(1).pendiente().enEsperaPieza());
    }

    @Test void marcadaHoySinClienteNoCuentaEnPedidos() {
        ReparacionResumen r = asig("A_1", 1, null, false, true);
        r.setPorCerrarAt(HOY.plusHours(10));
        var pedidos = CargaTecnicos.calcularDia(List.of(r), List.of(), LUNES, true, HOY);
        assertNull(pedidos.get(1));
        var total = CargaTecnicos.calcularDia(List.of(r), List.of(), LUNES, false, HOY);
        assertEquals(5.0 * 11 / 12, total.get(1).pctHecho(), 1e-9);
    }

    @Test void sinCorteNadaCuentaComoMarcadoHoy() {
        var dia = CargaTecnicos.calcularDia(List.of(porCerrar("A_1", false, HOY.plusHours(10))),
                List.of(), LUNES, false);
        assertEquals(0.0, dia.get(1).pctHecho(), 1e-9);
        assertEquals(5.0 / 12, dia.get(1).pctPendiente(), 1e-9);
    }

    @Test void laGlassNoTieneMarcaPorCerrar() {
        ReparacionResumen g = asig("AG_1", 1, "WEB", false, true);   // no debería pasar, pero no cambia nada
        g.setPorCerrarAt(HOY.plusHours(10));
        assertFalse(CargaTecnicos.marcadaHoy(g, HOY));
    }

    @Test void formateaPctEntero() {
        assertEquals("79%", CargaTecnicos.formatearPct(79.41));
        assertEquals("112%", CargaTecnicos.formatearPct(112.4));
        assertEquals("0%", CargaTecnicos.formatearPct(0));
    }
}
