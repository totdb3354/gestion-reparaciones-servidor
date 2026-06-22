package com.reparaciones.servidor.dao;

import com.reparaciones.servidor.model.Tecnico;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class TecnicoDAO {

    private final JdbcTemplate jdbc;

    private static final RowMapper<Tecnico> MAPPER = (rs, row) -> new Tecnico(
            rs.getInt("ID_TEC"),
            rs.getString("NOMBRE"),
            rs.getBoolean("ACTIVO")
    );

    public TecnicoDAO(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Tecnico> getAll() {
        return jdbc.query("""
                SELECT t.ID_TEC, t.NOMBRE, t.ACTIVO FROM Tecnico t
                JOIN Usuario u ON t.ID_TEC = u.ID_TEC
                WHERE u.ROL IN ('TECNICO','SUPERTECNICO')
                ORDER BY t.NOMBRE
                """, MAPPER);
    }

    public List<Tecnico> getAllActivos() {
        return jdbc.query("""
                SELECT t.ID_TEC, t.NOMBRE, t.ACTIVO FROM Tecnico t
                JOIN Usuario u ON t.ID_TEC = u.ID_TEC
                WHERE u.ROL IN ('TECNICO','SUPERTECNICO') AND t.ACTIVO = 1
                ORDER BY t.NOMBRE
                """, MAPPER);
    }

    public void insertar(String nombre) {
        jdbc.update("INSERT INTO Tecnico (NOMBRE, ACTIVO) VALUES (?, 1)", nombre);
    }

    public void eliminar(int idTec) {
        jdbc.update("DELETE FROM Tecnico WHERE ID_TEC = ?", idTec);
    }

    public String getNombreById(int idTec) {
        return jdbc.queryForObject(
                "SELECT NOMBRE FROM Tecnico WHERE ID_TEC = ?", String.class, idTec);
    }
}
