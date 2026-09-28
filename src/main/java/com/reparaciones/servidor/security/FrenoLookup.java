package com.reparaciones.servidor.security;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Ritmo máximo por usuario de la consulta de modelo por IMEI, que llama a un servicio externo de pago:
 * permite hasta {@link #MAX_POR_VENTANA} consultas por usuario en cada ventana de {@link #VENTANA_MS},
 * en modo ráfaga (no exige separación entre ellas dentro de la ventana). Esto deja pasar de golpe el
 * lote de consultas que dispara un lote de IMEIs a la vez, y frena solo un bucle desbocado. En memoria
 * y por instancia, igual que el registro de reintentos: si el servidor se reinicia, el contador arranca
 * de cero, lo que es inocuo para lo que protege.
 */
@Component
public class FrenoLookup {

    /** Consultas permitidas por usuario dentro de cada ventana. */
    public static final int MAX_POR_VENTANA = 30;
    /** Tamaño de la ventana deslizante, en milisegundos. */
    public static final long VENTANA_MS = 10_000L;
    public static final String MSG_DEMASIADAS = "Demasiadas consultas de IMEI seguidas. Espera un momento.";

    private final Map<Integer, Deque<Long>> marcas = new ConcurrentHashMap<>();
    private final Supplier<Long> reloj;

    public FrenoLookup() {
        this(System::currentTimeMillis);
    }

    /** Para tests: reloj inyectable. */
    public FrenoLookup(Supplier<Long> reloj) {
        this.reloj = reloj;
    }

    /**
     * @throws ResponseStatusException 429 si este usuario ya acumula {@link #MAX_POR_VENTANA} consultas
     *                                  en los últimos {@link #VENTANA_MS} ms.
     */
    public void comprobar(int idUsu) {
        long ahora = reloj.get();
        Deque<Long> ventana = marcas.computeIfAbsent(idUsu, k -> new ArrayDeque<>());
        synchronized (ventana) {
            while (!ventana.isEmpty() && ahora - ventana.peekFirst() >= VENTANA_MS) {
                ventana.pollFirst();
            }
            if (ventana.size() >= MAX_POR_VENTANA) {
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, MSG_DEMASIADAS);
            }
            ventana.addLast(ahora);
        }
    }
}
