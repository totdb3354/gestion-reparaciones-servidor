package com.reparaciones.servidor.dao;

import com.reparaciones.servidor.model.ValorDificultad;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Repository
public class DificultadPuntosDAO {

    private final JdbcTemplate jdbc;

    public DificultadPuntosDAO(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public Map<String, Double> getValores() {
        Map<String, Double> out = new HashMap<>();
        jdbc.query("SELECT CLAVE, PUNTOS FROM Dificultad_puntos",
                rs -> { out.put(rs.getString("CLAVE"), rs.getDouble("PUNTOS")); });
        return out;
    }

    public List<ValorDificultad> getAll() {
        return jdbc.query("SELECT CLAVE, PUNTOS FROM Dificultad_puntos ORDER BY CLAVE",
                (rs, row) -> new ValorDificultad(rs.getString("CLAVE"), rs.getDouble("PUNTOS")));
    }

    /** @return filas afectadas (0 si la clave no existe — el controller lo convierte en 422). */
    public int actualizar(String clave, double puntos) {
        return jdbc.update("UPDATE Dificultad_puntos SET PUNTOS = ? WHERE CLAVE = ?", puntos, clave);
    }
}
