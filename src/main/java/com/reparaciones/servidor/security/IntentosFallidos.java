package com.reparaciones.servidor.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;
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
 *
 * La cuenta se identifica en minúsculas y sin espacios en los extremos, porque la base compara los nombres de
 * usuario sin distinguir mayúsculas: así "Ana" y "ana" comparten el mismo contador.
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

    /** Tope de cuentas distintas que el freno recuerda a la vez, como en RegistroIdempotencia.MAX_ENTRADAS. */
    public static final int MAX_ENTRADAS = 10_000;
    /** Tope del contador de fallos: muchísimo más de lo que hace falta para llegar a ESPERA_MAX_MS, solo para
     *  que la multiplicación de esperaDe() nunca pueda desbordar aunque alguien insista sin parar. */
    static final int FALLOS_MAX = 1_000_000;

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
        String clave = normalizar(usuario);
        if (clave == null) return;
        Estado e = porCuenta.get(clave);
        if (e == null || e.fallos() < UMBRAL) return;
        if (reloj.get() - e.ultimo() < esperaDe(e.fallos())) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, MSG_ESPERA);
        }
    }

    /** @return fallos seguidos de esta cuenta después de contar este. */
    public int registrarFallo(String usuario) {
        String clave = normalizar(usuario);
        if (clave == null) return 0;
        Estado nuevo = porCuenta.compute(clave, (k, e) ->
                new Estado(e == null ? 1 : Math.min(e.fallos() + 1, FALLOS_MAX), reloj.get()));
        purgarSiHaceFalta(clave);
        return nuevo.fallos();
    }

    /** Tras una entrada correcta. */
    public void limpiar(String usuario) {
        String clave = normalizar(usuario);
        if (clave != null) porCuenta.remove(clave);
    }

    /** Espera vigente de esta cuenta, en milisegundos (0 si no tiene). */
    public long esperaMs(String usuario) {
        String clave = normalizar(usuario);
        if (clave == null) return 0L;
        Estado e = porCuenta.get(clave);
        return e == null || e.fallos() < UMBRAL ? 0L : esperaDe(e.fallos());
    }

    /** Minúsculas y sin espacios en los extremos: la clave con la que el freno identifica la cuenta. El nombre
     *  tal como se escribió no cambia; solo se normaliza esta clave interna. */
    private static String normalizar(String usuario) {
        return usuario == null ? null : usuario.trim().toLowerCase(Locale.ROOT);
    }

    private static long esperaDe(int fallos) {
        long espera = (long) (fallos - UMBRAL + 1) * PASO_MS;
        return Math.min(espera, ESPERA_MAX_MS);
    }

    /**
     * Al superar el tope, descarta la cuenta menos reciente (por su último fallo) para que el mapa no crezca sin
     * límite: mismo patrón que RegistroIdempotencia.purgarSiHaceFalta, pero sin caducidad propia, porque aquí una
     * entrada solo desaparece al acertar o, si hace falta sitio, por ser la más antigua.
     */
    private void purgarSiHaceFalta(String clavePropia) {
        if (porCuenta.size() <= MAX_ENTRADAS) return;
        String masAntigua = null;
        long masAntiguoInstante = Long.MAX_VALUE;
        for (Map.Entry<String, Estado> e : porCuenta.entrySet()) {
            if (e.getKey().equals(clavePropia)) continue;
            if (e.getValue().ultimo() < masAntiguoInstante) {
                masAntiguoInstante = e.getValue().ultimo();
                masAntigua = e.getKey();
            }
        }
        if (masAntigua != null) porCuenta.remove(masAntigua);
    }
}
