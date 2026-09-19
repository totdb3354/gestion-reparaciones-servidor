package com.reparaciones.servidor.idempotencia;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * Reintentos seguros: recuerda, por usuario y clave, el resultado de una escritura ya hecha para devolverlo
 * en vez de repetirla. En memoria, con caducidad y tope de tamaño. Sin clave no interviene.
 */
@Component
public class RegistroIdempotencia {

    public static final String   CABECERA           = "Idempotency-Key";
    public static final Duration CADUCIDAD          = Duration.ofHours(24);
    public static final int      MAX_ENTRADAS       = 10_000;
    public static final int      MAX_LONGITUD_CLAVE = 100;
    public static final String   MSG_EN_CURSO          = "La operación ya se está procesando";
    public static final String   MSG_CLAVE_REUTILIZADA = "La clave de idempotencia ya se usó con otra petición";
    public static final String   MSG_CLAVE_INVALIDA    = "Clave de idempotencia no válida";

    private final Clock reloj;
    private final Map<Id, Entrada> registro = new ConcurrentHashMap<>();

    /** Reloj del sistema: el que usa Spring al construir el bean. */
    public RegistroIdempotencia() {
        this(Clock.systemDefaultZone());
    }

    /** Paquete: para que los tests avancen el tiempo sin dormir el hilo. */
    RegistroIdempotencia(Clock reloj) {
        this.reloj = reloj;
    }

    /**
     * @param idUsuario usuario del token (las claves de un usuario no valen para otro)
     * @param operacion nombre fijo de la operación ("completa", "filas", "agotar", "editar")
     * @param clave     valor de la cabecera; null o en blanco = ejecutar sin registrar nada
     * @param peticion  todo lo que define la petición (ruta + cuerpo); se compara con equals()
     * @param accion    la escritura; su valor de retorno es la respuesta (null si el método es void)
     */
    public <T> T ejecutar(int idUsuario, String operacion, String clave, Object peticion, Supplier<T> accion) {
        if (clave == null || clave.isBlank()) {
            return accion.get();
        }
        if (clave.length() > MAX_LONGITUD_CLAVE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, MSG_CLAVE_INVALIDA);
        }

        Id id = new Id(idUsuario, operacion, clave);
        AtomicBoolean propietario = new AtomicBoolean(false);
        AtomicReference<Entrada> existenteRef = new AtomicReference<>();

        registro.compute(id, (k, actual) -> {
            if (actual == null || actual.caducada(reloj)) {
                propietario.set(true);
                return Entrada.enCurso(reloj.instant());
            }
            existenteRef.set(actual);
            return actual;
        });

        if (!propietario.get()) {
            Entrada actual = existenteRef.get();
            if (actual.enCurso) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, MSG_EN_CURSO);
            }
            if (Objects.equals(actual.peticion, peticion)) {
                @SuppressWarnings("unchecked")
                T resultado = (T) actual.resultado;
                return resultado;
            }
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, MSG_CLAVE_REUTILIZADA);
        }

        purgarSiHaceFalta(id);
        try {
            T resultado = accion.get();
            registro.put(id, Entrada.hecha(peticion, resultado, reloj.instant()));
            return resultado;
        } catch (RuntimeException | Error e) {
            // La transacción se deshizo: el reintento con la misma clave debe poder ejecutarse de nuevo.
            registro.remove(id);
            throw e;
        }
    }

    /** Al insertar con el registro lleno, purga las caducadas y, si sigue lleno, descarta la más antigua. */
    private void purgarSiHaceFalta(Id clavePropia) {
        if (registro.size() <= MAX_ENTRADAS) return;

        registro.entrySet().removeIf(e -> !e.getKey().equals(clavePropia) && e.getValue().caducada(reloj));
        if (registro.size() <= MAX_ENTRADAS) return;

        Id masAntigua = null;
        Instant masAntiguoInstante = null;
        for (Map.Entry<Id, Entrada> e : registro.entrySet()) {
            if (e.getKey().equals(clavePropia)) continue;
            if (masAntiguoInstante == null || e.getValue().creado.isBefore(masAntiguoInstante)) {
                masAntiguoInstante = e.getValue().creado;
                masAntigua = e.getKey();
            }
        }
        if (masAntigua != null) registro.remove(masAntigua);
    }

    private record Id(int idUsuario, String operacion, String clave) {}

    private static final class Entrada {
        final boolean enCurso;
        final Object  peticion;   // null mientras está en curso
        final Object  resultado;  // null mientras está en curso (o si la acción es void)
        final Instant creado;     // instante de creación (en curso) o de finalización (hecha)

        private Entrada(boolean enCurso, Object peticion, Object resultado, Instant creado) {
            this.enCurso  = enCurso;
            this.peticion = peticion;
            this.resultado = resultado;
            this.creado   = creado;
        }

        static Entrada enCurso(Instant ahora) {
            return new Entrada(true, null, null, ahora);
        }

        static Entrada hecha(Object peticion, Object resultado, Instant ahora) {
            return new Entrada(false, peticion, resultado, ahora);
        }

        /** Las entradas en curso no caducan mientras su hilo sigue vivo; solo caducan las ya hechas. */
        boolean caducada(Clock reloj) {
            return !enCurso && Duration.between(creado, reloj.instant()).compareTo(CADUCIDAD) > 0;
        }
    }
}
