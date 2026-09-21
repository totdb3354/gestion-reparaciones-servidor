package com.reparaciones.servidor.idempotencia;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

/** Reintentos seguros por clave de idempotencia (spec web-formulario, tarea añadida al cierre 2026-09-19). */
class RegistroIdempotenciaTest {

    @Test void sinClaveEjecutaSiempre() {
        RegistroIdempotencia r = new RegistroIdempotencia();
        AtomicInteger llamadas = new AtomicInteger();
        Supplier<String> accion = () -> { llamadas.incrementAndGet(); return "resultado-" + llamadas.get(); };

        assertEquals("resultado-1", r.ejecutar(1, "completa", null, "peticion", accion));
        assertEquals("resultado-2", r.ejecutar(1, "completa", "   ", "peticion", accion));
        assertEquals(2, llamadas.get());
    }

    @Test void primeraVezEjecutaYGuardaElResultado() {
        RegistroIdempotencia r = new RegistroIdempotencia();
        AtomicInteger llamadas = new AtomicInteger();
        Supplier<String> accion = () -> { llamadas.incrementAndGet(); return "ok"; };

        String resultado = r.ejecutar(1, "completa", "clave-1", "peticion-a", accion);

        assertEquals("ok", resultado);
        assertEquals(1, llamadas.get());
    }

    @Test void reintentoConLaMismaPeticionNoVuelveAEjecutar() {
        RegistroIdempotencia r = new RegistroIdempotencia();
        AtomicInteger llamadas = new AtomicInteger();
        Supplier<String> accion = () -> { llamadas.incrementAndGet(); return "resultado-" + llamadas.get(); };

        String primero = r.ejecutar(1, "completa", "clave-1", "peticion-a", accion);
        String segundo = r.ejecutar(1, "completa", "clave-1", "peticion-a", accion);

        assertEquals(primero, segundo);
        assertEquals(1, llamadas.get());
    }

    @Test void reintentoDevuelveTambienUnResultadoNulo() {
        RegistroIdempotencia r = new RegistroIdempotencia();
        AtomicInteger llamadas = new AtomicInteger();
        Supplier<Void> accionVoid = () -> { llamadas.incrementAndGet(); return null; };

        assertNull(r.ejecutar(1, "editar", "clave-1", "peticion-a", accionVoid));
        assertNull(r.ejecutar(1, "editar", "clave-1", "peticion-a", accionVoid));

        assertEquals(1, llamadas.get());
    }

    @Test void claveConOtraPeticionEs422() {
        RegistroIdempotencia r = new RegistroIdempotencia();
        r.ejecutar(1, "completa", "clave-1", "peticion-a", (Supplier<String>) () -> "ok");

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> r.ejecutar(1, "completa", "clave-1", "peticion-b", (Supplier<String>) () -> "otro"));

        assertEquals(422, ex.getStatusCode().value());
        assertEquals(RegistroIdempotencia.MSG_CLAVE_REUTILIZADA, ex.getReason());
    }

    @Test void enCursoEs409() throws InterruptedException {
        RegistroIdempotencia r = new RegistroIdempotencia();
        CountDownLatch dentro = new CountDownLatch(1);
        CountDownLatch continuar = new CountDownLatch(1);
        AtomicReference<String> resultadoPrimero = new AtomicReference<>();

        Thread primero = new Thread(() -> resultadoPrimero.set(
                r.ejecutar(1, "completa", "clave-1", "peticion-a", (Supplier<String>) () -> {
                    dentro.countDown();
                    try {
                        continuar.await(5, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    return "ok";
                })));
        primero.start();
        assertTrue(dentro.await(5, TimeUnit.SECONDS), "el primer hilo no llegó a entrar en la acción");

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> r.ejecutar(1, "completa", "clave-1", "peticion-a", (Supplier<String>) () -> "otro"));
        assertEquals(409, ex.getStatusCode().value());
        assertEquals(RegistroIdempotencia.MSG_EN_CURSO, ex.getReason());

        continuar.countDown();
        primero.join(5000);
        assertEquals("ok", resultadoPrimero.get(), "el primer hilo debía completar su propia ejecución con normalidad");
    }

    @Test void siLaAccionFallaElReintentoVuelveAEjecutar() {
        RegistroIdempotencia r = new RegistroIdempotencia();
        RuntimeException fallo = new IllegalStateException("transaccion deshecha");
        AtomicInteger llamadas = new AtomicInteger();
        Supplier<String> falla = () -> { llamadas.incrementAndGet(); throw fallo; };

        IllegalStateException capturada = assertThrows(IllegalStateException.class,
                () -> r.ejecutar(1, "completa", "clave-1", "peticion-a", falla));
        assertSame(fallo, capturada);
        assertEquals(1, llamadas.get());

        String resultado = r.ejecutar(1, "completa", "clave-1", "peticion-a",
                () -> { llamadas.incrementAndGet(); return "ok"; });
        assertEquals("ok", resultado);
        assertEquals(2, llamadas.get());
    }

    @Test void lasClavesSonPorUsuarioYPorOperacion() {
        RegistroIdempotencia r = new RegistroIdempotencia();
        AtomicInteger llamadas = new AtomicInteger();
        Supplier<String> accion = () -> { llamadas.incrementAndGet(); return "resultado-" + llamadas.get(); };

        r.ejecutar(1, "completa", "clave-1", "peticion-a", accion);
        r.ejecutar(2, "completa", "clave-1", "peticion-a", accion);   // otro usuario, misma clave: ejecuta
        r.ejecutar(1, "filas", "clave-1", "peticion-a", accion);      // mismo usuario, otra operación: ejecuta

        assertEquals(3, llamadas.get());
    }

    @Test void unaEntradaCaducadaSeTrataComoNueva() {
        RelojFalso reloj = new RelojFalso(Instant.parse("2026-09-19T08:00:00Z"));
        RegistroIdempotencia r = new RegistroIdempotencia(reloj);
        AtomicInteger llamadas = new AtomicInteger();
        Supplier<String> accion = () -> { llamadas.incrementAndGet(); return "resultado-" + llamadas.get(); };

        r.ejecutar(1, "completa", "clave-1", "peticion-a", accion);
        reloj.avanzar(RegistroIdempotencia.CADUCIDAD.plusMinutes(1));
        String segundo = r.ejecutar(1, "completa", "clave-1", "peticion-a", accion);

        assertEquals(2, llamadas.get());
        assertEquals("resultado-2", segundo);
    }

    @Test void conElRegistroLlenoSeDescartaLaMasAntigua() {
        RelojFalso reloj = new RelojFalso(Instant.parse("2026-09-19T08:00:00Z"));
        RegistroIdempotencia r = new RegistroIdempotencia(reloj);

        for (int i = 0; i < RegistroIdempotencia.MAX_ENTRADAS; i++) {
            int copia = i;
            r.ejecutar(1, "completa", "clave-" + i, "peticion-" + i, (Supplier<String>) () -> "ok-" + copia);
            reloj.avanzar(Duration.ofMillis(1));
        }

        // Sigue en el registro: el reintento de la primera clave (la más antigua) no vuelve a ejecutar todavía.
        AtomicInteger llamadasPrevias = new AtomicInteger();
        r.ejecutar(1, "completa", "clave-0", "peticion-0", (Supplier<String>) () -> {
            llamadasPrevias.incrementAndGet();
            return "reejecutado";
        });
        assertEquals(0, llamadasPrevias.get());

        // Una entrada nueva llena el registro por encima del tope y descarta la más antigua (clave-0).
        r.ejecutar(1, "completa", "clave-nueva", "peticion-nueva", (Supplier<String>) () -> "ok");

        AtomicInteger llamadasTrasDescarte = new AtomicInteger();
        String resultado = r.ejecutar(1, "completa", "clave-0", "peticion-0", (Supplier<String>) () -> {
            llamadasTrasDescarte.incrementAndGet();
            return "reejecutado";
        });
        assertEquals(1, llamadasTrasDescarte.get(), "clave-0 debía haberse descartado por ser la más antigua");
        assertEquals("reejecutado", resultado);
    }

    @Test void claveDemasiadoLargaEs400() {
        RegistroIdempotencia r = new RegistroIdempotencia();
        String clave = "x".repeat(RegistroIdempotencia.MAX_LONGITUD_CLAVE + 1);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> r.ejecutar(1, "completa", clave, "peticion", (Supplier<String>) () -> "ok"));

        assertEquals(400, ex.getStatusCode().value());
        assertEquals(RegistroIdempotencia.MSG_CLAVE_INVALIDA, ex.getReason());
    }

    @Test void laClaveSeRecortaAntesDeUsarla() {
        RegistroIdempotencia r = new RegistroIdempotencia();
        AtomicInteger llamadas = new AtomicInteger();
        Supplier<String> accion = () -> { llamadas.incrementAndGet(); return "resultado-" + llamadas.get(); };

        String primero = r.ejecutar(1, "completa", "k", "peticion-a", accion);
        String segundo = r.ejecutar(1, "completa", "  k  ", "peticion-a", accion);

        assertEquals(primero, segundo);
        assertEquals(1, llamadas.get());
    }

    // ── lo de después (lookups del log): no forma parte de la escritura transaccional ──────────

    @Test void sinClaveSeEjecutanLasDosPartesEnOrden() {
        RegistroIdempotencia r = new RegistroIdempotencia();
        List<String> orden = new ArrayList<>();

        String resultado = r.ejecutar(1, "completa", null, "peticion",
                (Supplier<String>) () -> { orden.add("escritura"); return "ok"; },
                (Consumer<String>) res -> orden.add("trasEscribir:" + res));

        assertEquals("ok", resultado);
        assertEquals(List.of("escritura", "trasEscribir:ok"), orden);
    }

    @Test void elReintentoServidoDelRegistroNoEjecutaLoDeDespues() {
        RegistroIdempotencia r = new RegistroIdempotencia();
        AtomicInteger llamadasTrasEscribir = new AtomicInteger();
        Consumer<String> trasEscribir = res -> llamadasTrasEscribir.incrementAndGet();

        r.ejecutar(1, "completa", "clave-1", "peticion-a", (Supplier<String>) () -> "ok", trasEscribir);
        r.ejecutar(1, "completa", "clave-1", "peticion-a", (Supplier<String>) () -> "otro", trasEscribir);

        assertEquals(1, llamadasTrasEscribir.get());
    }

    /**
     * Si la escritura ya se comprometió (p. ej. un descuento de stock) pero lo de después falla (el log de
     * actividad), el reintento con la misma clave NO debe repetir la escritura: la entrada queda hecha antes
     * de ejecutar lo de después.
     */
    @Test void siFallaLoDeDespuesLaEscrituraNoSeRepiteAlReintentar() {
        RegistroIdempotencia r = new RegistroIdempotencia();
        AtomicInteger llamadasEscritura = new AtomicInteger();
        AtomicInteger llamadasTrasEscribir = new AtomicInteger();
        RuntimeException falloLog = new IllegalStateException("el log falló");

        Supplier<String> escritura = () -> { llamadasEscritura.incrementAndGet(); return "resultado"; };
        Consumer<String> trasEscribirQueFalla = res -> { llamadasTrasEscribir.incrementAndGet(); throw falloLog; };

        IllegalStateException capturada = assertThrows(IllegalStateException.class,
                () -> r.ejecutar(1, "completa", "clave-1", "peticion-a", escritura, trasEscribirQueFalla));
        assertSame(falloLog, capturada);
        assertEquals(1, llamadasEscritura.get());
        assertEquals(1, llamadasTrasEscribir.get());

        // Reintento con la misma clave: la escritura no se repite y lo de después tampoco se vuelve a ejecutar.
        String resultado = r.ejecutar(1, "completa", "clave-1", "peticion-a", escritura,
                (Consumer<String>) res -> llamadasTrasEscribir.incrementAndGet());
        assertEquals("resultado", resultado);
        assertEquals(1, llamadasEscritura.get());
        assertEquals(1, llamadasTrasEscribir.get());
    }

    /** Nunca se descarta una reclamación en curso por el tope de tamaño, aunque sea la más antigua. */
    @Test void elTopeNuncaDescartaUnaEntradaEnCurso() throws InterruptedException {
        RelojFalso reloj = new RelojFalso(Instant.parse("2026-09-19T08:00:00Z"));
        RegistroIdempotencia r = new RegistroIdempotencia(reloj);

        CountDownLatch dentro = new CountDownLatch(1);
        CountDownLatch continuar = new CountDownLatch(1);
        Thread enCurso = new Thread(() -> r.ejecutar(1, "completa", "clave-en-curso", "peticion-en-curso",
                (Supplier<String>) () -> {
                    dentro.countDown();
                    try {
                        continuar.await(5, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    return "ok";
                }));
        enCurso.start();
        assertTrue(dentro.await(5, TimeUnit.SECONDS), "el hilo en curso no llegó a entrar en la acción");

        // La reclamación en curso es la más antigua (se creó primero); llenamos el resto del registro.
        for (int i = 0; i < RegistroIdempotencia.MAX_ENTRADAS - 1; i++) {
            int copia = i;
            r.ejecutar(1, "completa", "clave-" + i, "peticion-" + i, (Supplier<String>) () -> "ok-" + copia);
            reloj.avanzar(Duration.ofMillis(1));
        }

        // Una entrada más por encima del tope: debería descartar la más antigua entre las HECHAS, nunca la en curso.
        r.ejecutar(1, "completa", "clave-nueva", "peticion-nueva", (Supplier<String>) () -> "ok");

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> r.ejecutar(1, "completa", "clave-en-curso", "peticion-en-curso", (Supplier<String>) () -> "otro"));
        assertEquals(409, ex.getStatusCode().value());

        continuar.countDown();
        enCurso.join(5000);
    }

    /** Reloj mutable para simular el paso del tiempo sin dormir el hilo del test. */
    private static final class RelojFalso extends Clock {
        private Instant ahora;

        RelojFalso(Instant inicial) { this.ahora = inicial; }

        void avanzar(Duration d) { ahora = ahora.plus(d); }

        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return ahora; }
    }
}
