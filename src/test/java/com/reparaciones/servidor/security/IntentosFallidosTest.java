package com.reparaciones.servidor.security;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Freno por cuenta con espera creciente, sin bloquear (spec sp7b §5.3 y decisión D9). */
class IntentosFallidosTest {

    private final AtomicLong ahora = new AtomicLong(1_000_000L);
    private final IntentosFallidos intentos = new IntentosFallidos(ahora::get);

    private void fallar(int veces) {
        for (int i = 0; i < veces; i++) intentos.registrarFallo("ana");
    }

    @Test void sinFallosNoHayEspera() {
        assertDoesNotThrow(() -> intentos.comprobar("ana"));
    }

    @Test void hastaElUmbralNoHayEspera() {
        fallar(IntentosFallidos.UMBRAL - 1);
        assertDoesNotThrow(() -> intentos.comprobar("ana"));
    }

    @Test void enElUmbralHayEspera() {
        fallar(IntentosFallidos.UMBRAL);
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> intentos.comprobar("ana"));
        assertEquals(429, ex.getStatusCode().value());
        assertEquals(IntentosFallidos.MSG_ESPERA, ex.getReason());
    }

    @Test void laEsperaCreceConCadaFallo() {
        fallar(IntentosFallidos.UMBRAL);
        long primera = intentos.esperaMs("ana");
        intentos.registrarFallo("ana");
        assertEquals(true, intentos.esperaMs("ana") > primera);
    }

    @Test void laEsperaTieneTope() {
        fallar(IntentosFallidos.UMBRAL + 50);
        assertEquals(IntentosFallidos.ESPERA_MAX_MS, intentos.esperaMs("ana"));
    }

    @Test void pasadaLaEsperaVuelveAPasar() {
        fallar(IntentosFallidos.UMBRAL);
        ahora.addAndGet(intentos.esperaMs("ana"));
        assertDoesNotThrow(() -> intentos.comprobar("ana"));
    }

    @Test void unaEntradaCorrectaLimpiaElContador() {
        fallar(IntentosFallidos.UMBRAL);
        intentos.limpiar("ana");
        assertDoesNotThrow(() -> intentos.comprobar("ana"));
    }

    @Test void elFrenoEsPorCuentaYNoAfectaAOtras() {
        fallar(IntentosFallidos.UMBRAL);
        assertDoesNotThrow(() -> intentos.comprobar("bruno"));
    }

    @Test void registrarFalloDevuelveElNumeroDeFallosSeguidos() {
        assertEquals(1, intentos.registrarFallo("ana"));
        assertEquals(2, intentos.registrarFallo("ana"));
    }

    @Test void elNombreNuloNoRompe() {
        assertDoesNotThrow(() -> intentos.comprobar(null));
        assertEquals(0, intentos.registrarFallo(null));
    }

    @Test void elFrenoPorCuentaIgnoraMayusculas() {
        for (int i = 0; i < IntentosFallidos.UMBRAL; i++) intentos.registrarFallo("Ana");
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> intentos.comprobar("ANA"));
        assertEquals(429, ex.getStatusCode().value());
    }

    @Test void unAciertoConOtraVarianteDeMayusculasLimpiaElContadorComun() {
        for (int i = 0; i < IntentosFallidos.UMBRAL; i++) intentos.registrarFallo("ana");
        intentos.limpiar("ANA");
        assertDoesNotThrow(() -> intentos.comprobar("ana"));
    }

    @Test void conElRegistroLlenoSeDescartaLaCuentaMenosReciente() {
        for (int i = 0; i < IntentosFallidos.MAX_ENTRADAS; i++) {
            for (int f = 0; f < IntentosFallidos.UMBRAL; f++) intentos.registrarFallo("cuenta-" + i);
            ahora.incrementAndGet();
        }
        // Sigue en el mapa: es la primera cuenta, la menos reciente, pero el mapa aún no ha superado el tope.
        // esperaMs no depende del reloj (solo de si la cuenta sigue teniendo fallos registrados).
        assertEquals(true, intentos.esperaMs("cuenta-0") > 0L);

        // Una cuenta nueva satura el mapa por encima del tope y descarta la menos reciente (cuenta-0).
        for (int f = 0; f < IntentosFallidos.UMBRAL; f++) intentos.registrarFallo("cuenta-nueva");

        assertEquals(0L, intentos.esperaMs("cuenta-0"), "cuenta-0 debía haberse descartado por ser la menos reciente");
    }
}
