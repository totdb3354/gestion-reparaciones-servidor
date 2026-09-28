package com.reparaciones.servidor.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * ¿El usuario del token sigue existiendo y puede operar? Misma condición que aplica el inicio de sesión
 * (UserDetailsServiceImpl): los administradores no tienen técnico y siempre pasan; el resto necesita su técnico
 * activo. Con esto, desactivar, borrar o cambiar el rol de alguien surte efecto sin esperar a que caduque su
 * token (spec sp7b §5.1).
 *
 * La respuesta se cachea {@link #TTL_MS}: la web sondea cada minuto y cada pestaña abierta multiplica las
 * peticiones, así que consultar en cada una sería un coste por nada. Ese tiempo es también el techo de retardo
 * aceptado entre desactivar a alguien y que deje de poder operar; por eso no hay invalidación explícita.
 *
 * Si la consulta falla, se deja pasar: una caída de la base de datos no debe expulsar al taller entero.
 */
@Component
public class EstadoUsuarioService {

    private static final Logger log = LoggerFactory.getLogger(EstadoUsuarioService.class);

    public static final long TTL_MS = 30_000L;

    private static final String SQL = """
            SELECT 1
            FROM Usuario u
            LEFT JOIN Tecnico t ON u.ID_TEC = t.ID_TEC
            WHERE u.ID_USU = ?
              AND (u.ID_TEC IS NULL OR t.ACTIVO = 1)
            """;

    private record Entrada(boolean operativo, long hasta) {}

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
        long ahora = reloj.get();
        Entrada e = cache.get(idUsu);
        if (e != null && ahora < e.hasta()) return e.operativo();
        boolean operativo;
        try {
            operativo = !jdbc.queryForList(SQL, Integer.class, idUsu).isEmpty();
        } catch (DataAccessException ex) {
            log.warn("No se pudo comprobar el estado del usuario {}: {}", idUsu, ex.toString());
            return true;
        }
        cache.put(idUsu, new Entrada(operativo, ahora + TTL_MS));
        return operativo;
    }
}
