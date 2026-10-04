package com.reparaciones.servidor.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * ¿El usuario del token sigue existiendo y puede operar? Misma condición que aplica el inicio de sesión
 * (UserDetailsServiceImpl): los administradores no tienen técnico y siempre pasan; el resto necesita su técnico
 * activo. Con esto, desactivar o borrar a alguien surte efecto sin esperar a que caduque su token (spec sp7b §5.1).
 * El rol que lleva el token no se vuelve a comprobar aquí: un cambio de rol no tiene efecto hasta que la sesión
 * caduque.
 *
 * También dice si el usuario tiene la contraseña marcada como temporal (spec sp7b §5.4), reutilizando la misma
 * consulta y la misma caché.
 *
 * La respuesta se cachea {@link #TTL_MS}: la web sondea cada minuto y cada pestaña abierta multiplica las
 * peticiones, así que consultar en cada una sería un coste por nada. Ese tiempo es también el techo de retardo
 * aceptado si un cambio no avisa. Los cambios que pasan por la aplicación sí avisan: cambiar la propia
 * contraseña, y activar, desactivar, eliminar o entregar una contraseña temporal desde la administración de
 * usuarios llaman a {@link #invalidar(int)}, que retira la entrada al momento en vez de esperar a que caduque.
 *
 * Si la consulta falla, se deja pasar: una caída de la base de datos no debe expulsar al taller entero.
 */
@Component
public class EstadoUsuarioService {

    private static final Logger log = LoggerFactory.getLogger(EstadoUsuarioService.class);

    public static final long TTL_MS = 30_000L;

    private static final String SQL = """
            SELECT u.PASSWORD_TEMPORAL
            FROM Usuario u
            LEFT JOIN Tecnico t ON u.ID_TEC = t.ID_TEC
            WHERE u.ID_USU = ?
              AND (u.ID_TEC IS NULL OR t.ACTIVO = 1)
            """;

    private record Entrada(boolean operativo, boolean passwordTemporal, long hasta) {}

    private final Map<Integer, Entrada> cache = new ConcurrentHashMap<>();
    private final JdbcTemplate jdbc;
    private final Supplier<Long> reloj;

    @Autowired
    public EstadoUsuarioService(JdbcTemplate jdbc) {
        this(jdbc, System::currentTimeMillis);
    }

    /** Para tests: reloj inyectable. */
    public EstadoUsuarioService(JdbcTemplate jdbc, Supplier<Long> reloj) {
        this.jdbc  = jdbc;
        this.reloj = reloj;
    }

    public boolean estaOperativo(int idUsu) {
        return entrada(idUsu).operativo();
    }

    public boolean tienePasswordTemporal(int idUsu) {
        return entrada(idUsu).passwordTemporal();
    }

    /** Retira la entrada cacheada de un usuario, para que un cambio recién escrito en él (su contraseña, su
     *  estado activo, su borrado) surta efecto en su siguiente petición sin esperar hasta {@link #TTL_MS}. Se
     *  llama después de la escritura. */
    public void invalidar(int idUsu) {
        cache.remove(idUsu);
    }

    private Entrada entrada(int idUsu) {
        long ahora = reloj.get();
        Entrada e = cache.get(idUsu);
        if (e != null && ahora < e.hasta()) return e;
        Entrada nueva;
        try {
            List<Boolean> filas = jdbc.queryForList(SQL, Boolean.class, idUsu);
            boolean operativo = !filas.isEmpty();
            boolean passwordTemporal = operativo && Boolean.TRUE.equals(filas.get(0));
            nueva = new Entrada(operativo, passwordTemporal, ahora + TTL_MS);
        } catch (DataAccessException ex) {
            log.warn("No se pudo comprobar el estado del usuario {}: {}", idUsu, ex.toString());
            return new Entrada(true, false, ahora);
        }
        cache.put(idUsu, nueva);
        return nueva;
    }
}
