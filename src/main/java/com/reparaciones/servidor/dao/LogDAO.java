package com.reparaciones.servidor.dao;

import com.reparaciones.servidor.model.LogActividad;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Repository
public class LogDAO {

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

    public List<LogActividad> getFiltered(String accion, String tecnico,
                                          LocalDate desde, LocalDate hasta) {
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
            sql.append(" AND DATE(l.FECHA) >= ?");
            params.add(desde.toString());
        }
        if (hasta != null) {
            sql.append(" AND DATE(l.FECHA) <= ?");
            params.add(hasta.toString());
        }
        sql.append(" ORDER BY l.FECHA DESC");
        return jdbc.query(sql.toString(), MAPPER, params.toArray());
    }
}
