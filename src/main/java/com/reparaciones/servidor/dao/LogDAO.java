package com.reparaciones.servidor.dao;

import com.reparaciones.servidor.model.LogActividad;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

@Repository
public class LogDAO {

    static final ZoneId MADRID = ZoneId.of("Europe/Madrid");

    /** Tope del parámetro {@code limite} de GET /api/logs (spec 6 §4.5); lo comprueba LogController. */
    public static final int LIMITE_MAX = 5000;

    /** Ancho de la columna NOMBRE_USUARIO (sql/crear_bd.sql): un nombre más largo se recorta antes de insertar,
     *  para que un intento con un nombre muy largo se registre igual en vez de rechazarlo la base. */
    static final int MAX_NOMBRE_USUARIO = 50;

    private final JdbcTemplate jdbc;

    private static final RowMapper<LogActividad> MAPPER = (rs, row) -> new LogActividad(
            rs.getInt("ID_LOG"),
            rs.getTimestamp("FECHA").toLocalDateTime(),
            rs.getString("NOMBRE_USUARIO"),
            rs.getString("ACCION"),
            rs.getString("DETALLE"),
            rs.getString("MOTIVO")
    );

    public LogDAO(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void insertar(int idUsu, String accion, String detalle) {
        insertar(idUsu, accion, detalle, null);
    }

    /**
     * Guarda el nombre del usuario en la propia línea, resuelto en la misma sentencia: así la línea sigue
     * diciendo quién hizo qué cuando el usuario ya no exista (spec sp7b §5.6). Si el usuario no existe, no se
     * inserta nada: no hay actividad que registrar sin actor.
     */
    public void insertar(int idUsu, String accion, String detalle, String motivo) {
        jdbc.update(
                "INSERT INTO Log_Actividad (ID_USU, NOMBRE_USUARIO, ACCION, DETALLE, MOTIVO) " +
                "SELECT ?, NOMBRE_USUARIO, ?, ?, ? FROM Usuario WHERE ID_USU = ?",
                idUsu, accion, detalle, motivo, idUsu);
    }

    /**
     * Anota algo que no tiene usuario detrás, como un intento de entrar con un nombre que no existe: la clave
     * queda a nulo y el nombre intentado se guarda recortado al ancho de la columna (spec sp7b §5.2).
     */
    public void insertarIntento(String nombreUsuario, String accion, String detalle) {
        jdbc.update(
                "INSERT INTO Log_Actividad (ID_USU, NOMBRE_USUARIO, ACCION, DETALLE, MOTIVO) " +
                "VALUES (NULL, ?, ?, ?, NULL)",
                recortarNombre(nombreUsuario), accion, detalle);
    }

    private static String recortarNombre(String nombreUsuario) {
        if (nombreUsuario == null || nombreUsuario.length() <= MAX_NOMBRE_USUARIO) return nombreUsuario;
        return nombreUsuario.substring(0, MAX_NOMBRE_USUARIO);
    }

    /** Inicio del día {@code dia} en Madrid expresado en UTC, sin zona: así guarda FECHA la BD (sesión y JVM en UTC;
     *  mismo criterio que UrgenteAutomaticoJob.cutoffInicioDeHoyMadrid y ReparacionDAO.getEstadisticasPuntos). */
    static LocalDateTime inicioDiaUtc(LocalDate dia) {
        return dia.atStartOfDay(MADRID).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
    }

    /** Sin límite: lo que llama el JavaFX (todo el log que cumpla los filtros). */
    public List<LogActividad> getFiltered(String accion, String tecnico,
                                          LocalDate desde, LocalDate hasta) {
        return getFiltered(accion, tecnico, desde, hasta, null);
    }

    /** Filtros de igualdad de acción y usuario, días de Madrid completos (spec 6 G5: antes DATE(FECHA) en UTC dejaba
     *  lo ocurrido entre las 00:00 y las 01:59 de Madrid en el día anterior), orden estable por fecha e id y, si llega,
     *  un LIMIT (el rango 1..LIMITE_MAX lo valida el controlador). Con desde > hasta devuelve vacío. */
    public List<LogActividad> getFiltered(String accion, String tecnico,
                                          LocalDate desde, LocalDate hasta, Integer limite) {
        StringBuilder sql = new StringBuilder(
                "SELECT l.ID_LOG, l.FECHA, COALESCE(l.NOMBRE_USUARIO, u.NOMBRE_USUARIO) AS NOMBRE_USUARIO, " +
                "       l.ACCION, l.DETALLE, l.MOTIVO " +
                "FROM Log_Actividad l LEFT JOIN Usuario u ON l.ID_USU = u.ID_USU WHERE 1=1");
        List<Object> params = new ArrayList<>();

        if (accion != null && !accion.isBlank()) {
            sql.append(" AND l.ACCION = ?");
            params.add(accion);
        }
        if (tecnico != null && !tecnico.isBlank()) {
            sql.append(" AND COALESCE(l.NOMBRE_USUARIO, u.NOMBRE_USUARIO) = ?");
            params.add(tecnico);
        }
        if (desde != null) {
            sql.append(" AND l.FECHA >= ?");
            params.add(inicioDiaUtc(desde));
        }
        if (hasta != null) {
            sql.append(" AND l.FECHA < ?");
            params.add(inicioDiaUtc(hasta.plusDays(1)));
        }
        sql.append(" ORDER BY l.FECHA DESC, l.ID_LOG DESC");
        if (limite != null) {
            sql.append(" LIMIT ?");
            params.add(limite);
        }
        return jdbc.query(sql.toString(), MAPPER, params.toArray());
    }

    /** Las acciones que hay de verdad en el log, en orden alfabético (spec 6 G4): alimenta el filtro "Acción...". */
    public List<String> getAcciones() {
        return jdbc.queryForList("SELECT DISTINCT ACCION FROM Log_Actividad ORDER BY ACCION", String.class);
    }
}
