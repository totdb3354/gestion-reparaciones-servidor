package com.reparaciones.servidor.dao;

import com.reparaciones.servidor.model.LogActividad;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class LogDAO {

    private final JdbcTemplate jdbc;

    private static final RowMapper<LogActividad> MAPPER = (rs, row) -> new LogActividad(
            rs.getInt("ID_LOG"),
            rs.getTimestamp("FECHA").toLocalDateTime(),
            rs.getString("NOMBRE_USUARIO"),
            rs.getString("ACCION"),
            rs.getString("DETALLE")
    );

    public LogDAO(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void insertar(int idUsu, String accion, String detalle) {
        jdbc.update(
                "INSERT INTO Log_Actividad (ID_USU, ACCION, DETALLE) VALUES (?, ?, ?)",
                idUsu, accion, detalle);
    }

    public List<LogActividad> getAll() {
        return jdbc.query(
                "SELECT l.ID_LOG, l.FECHA, u.NOMBRE_USUARIO, l.ACCION, l.DETALLE " +
                "FROM Log_Actividad l JOIN Usuario u ON l.ID_USU = u.ID_USU " +
                "ORDER BY l.FECHA DESC",
                MAPPER);
    }
}
