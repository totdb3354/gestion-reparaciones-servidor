package com.reparaciones.servidor.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Freno por cuenta de los fallos de contraseña: a partir del quinto fallo seguido, cada intento espera un poco
 * más, hasta un minuto. No bloquea la cuenta a propósito (spec sp7b §5.3, decisión D9): bloquear permitiría
 * dejar fuera a un compañero probando su contraseña, y en un taller con equipos compartidos obligaría al
 * administrador a desbloquear. Un acierto limpia el contador.
 *
 * Es complementario del límite por dirección del proxy, que el taller comparte y no se toca. En memoria y por
 * instancia, como el registro de reintentos: un reinicio pone los contadores a cero, lo que solo relaja el freno.
 */
@Component
public class IntentosFallidos {

    /** Fallos seguidos a partir de los cuales empieza la espera. */
    public static final int UMBRAL = 5;
    /** Tope de la espera. */
    public static final long ESPERA_MAX_MS = 60_000L;
    /** Cuánto crece la espera por cada fallo más allá del umbral. */
    public static final long PASO_MS = 5_000L;
    public static final String MSG_ESPERA = "Demasiados intentos fallidos. Espera unos segundos y vuelve a intentarlo.";

    private record Estado(int fallos, long ultimo) {}

    private final Map<String, Estado> porCuenta = new ConcurrentHashMap<>();
    private final Supplier<Long> reloj;

    @Autowired
    public IntentosFallidos() {
        this(System::currentTimeMillis);
    }

    /** Para tests: reloj inyectable. */
    public IntentosFallidos(Supplier<Long> reloj) {
        this.reloj = reloj;
    }

    /** @throws ResponseStatusException 429 si esta cuenta todavía está en su espera. */
    public void comprobar(String usuario) {
        if (usuario == null) return;
        Estado e = porCuenta.get(usuario);
        if (e == null || e.fallos() < UMBRAL) return;
        if (reloj.get() - e.ultimo() < esperaDe(e.fallos())) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, MSG_ESPERA);
        }
    }

    /** @return fallos seguidos de esta cuenta después de contar este. */
    public int registrarFallo(String usuario) {
        if (usuario == null) return 0;
        Estado nuevo = porCuenta.compute(usuario, (k, e) ->
                new Estado(e == null ? 1 : e.fallos() + 1, reloj.get()));
        return nuevo.fallos();
    }

    /** Tras una entrada correcta. */
    public void limpiar(String usuario) {
        if (usuario != null) porCuenta.remove(usuario);
    }

    /** Espera vigente de esta cuenta, en milisegundos (0 si no tiene). */
    public long esperaMs(String usuario) {
        if (usuario == null) return 0L;
        Estado e = porCuenta.get(usuario);
        return e == null || e.fallos() < UMBRAL ? 0L : esperaDe(e.fallos());
    }

    private static long esperaDe(int fallos) {
        long espera = (long) (fallos - UMBRAL + 1) * PASO_MS;
        return Math.min(espera, ESPERA_MAX_MS);
    }
}
