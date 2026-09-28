package com.reparaciones.servidor.security;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Ritmo máximo por usuario de la consulta de modelo por IMEI, que llama a un servicio externo de pago. Hasta ahora
 * el ritmo lo ponía solo el cliente (spec sp7b §4.4). En memoria y por instancia, igual que el registro de
 * reintentos: si el servidor se reinicia, el contador arranca de cero, lo que es inocuo para lo que protege.
 */
@Component
public class FrenoLookup {

    /** Separación mínima entre dos consultas del mismo usuario. */
    public static final long INTERVALO_MIN_MS = 1_000L;
    public static final String MSG_DEMASIADAS = "Demasiadas consultas de IMEI seguidas. Espera un momento.";

    private final Map<Integer, Long> ultima = new ConcurrentHashMap<>();
    private final Supplier<Long> reloj;

    public FrenoLookup() {
        this(System::currentTimeMillis);
    }

    /** Para tests: reloj inyectable. */
    public FrenoLookup(Supplier<Long> reloj) {
        this.reloj = reloj;
    }

    /** @throws ResponseStatusException 429 si este usuario consultó hace menos de {@link #INTERVALO_MIN_MS}. */
    public void comprobar(int idUsu) {
        long ahora = reloj.get();
        Long previa = ultima.put(idUsu, ahora);
        if (previa != null && ahora - previa < INTERVALO_MIN_MS) {
            ultima.put(idUsu, previa);   // un rechazo no desplaza la ventana
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, MSG_DEMASIADAS);
        }
    }
}
