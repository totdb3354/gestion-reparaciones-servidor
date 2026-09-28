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
 * Ritmo máximo por usuario de las consultas de modelo por IMEI que de verdad salen al servicio externo,
 * es decir, las de un IMEI que todavía no está en la base: permite hasta {@link #MAX_POR_VENTANA}
 * consultas por usuario en cada ventana de {@link #VENTANA_MS}, en modo ráfaga (no exige separación
 * entre ellas dentro de la ventana). Así pasa de golpe el lote de consultas externas que dispara un
 * lote de IMEIs nuevos, y solo se frena un bucle desbocado.
 *
 * Lo que protege no es el coste —el servicio es gratuito— sino dos cosas: que una llamada repetida sin
 * control lleve al proveedor a limitar o cortar el acceso, y que la latencia acumulada de muchas
 * llamadas externas seguidas empuje una respuesta por encima del tiempo máximo aceptado.
 *
 * Los IMEIs que ya están en la base no gastan cupo, porque no salen fuera. En memoria y por instancia,
 * igual que el registro de reintentos: si el servidor se reinicia, el contador arranca de cero, lo que
 * es inocuo para lo que protege.
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
