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

    public void insertar(int idUsu, String accion, String detalle, String motivo) {
        jdbc.update(
                "INSERT INTO Log_Actividad (ID_USU, ACCION, DETALLE, MOTIVO) VALUES (?, ?, ?, ?)",
                idUsu, accion, detalle, motivo);
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
                "SELECT l.ID_LOG, l.FECHA, u.NOMBRE_USUARIO, l.ACCION, l.DETALLE, l.MOTIVO " +
                "FROM Log_Actividad l JOIN Usuario u ON l.ID_USU = u.ID_USU WHERE 1=1");
        List<Object> params = new ArrayList<>();

        if (accion != null && !accion.isBlank()) {
            sql.append(" AND l.ACCION = ?");
            params.add(accion);
        }
        if (tecnico != null && !tecnico.isBlank()) {
            sql.append(" AND u.NOMBRE_USUARIO = ?");
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
