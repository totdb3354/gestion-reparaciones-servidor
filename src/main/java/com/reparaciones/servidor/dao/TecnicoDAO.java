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
            rs.getBoolean("ACTIVO"),
            rs.getBoolean("ES_ESTADISTICA"),
            rs.getBoolean("ES_GLASS")
    );

    public TecnicoDAO(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Tecnico> getAll() {
        return jdbc.query("""
                SELECT t.ID_TEC, t.NOMBRE, t.ACTIVO, t.ES_ESTADISTICA, t.ES_GLASS FROM Tecnico t
                JOIN Usuario u ON t.ID_TEC = u.ID_TEC
                WHERE u.ROL IN ('TECNICO','SUPERTECNICO')
                ORDER BY t.NOMBRE
                """, MAPPER);
    }

    public List<Tecnico> getAllActivos() {
        return jdbc.query("""
                SELECT t.ID_TEC, t.NOMBRE, t.ACTIVO, t.ES_ESTADISTICA, t.ES_GLASS FROM Tecnico t
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

    /** Habilita/deshabilita al técnico para la glass automática del modal de asignación
     *  (spec 2026-09-05-glass-prediccion). Devuelve filas tocadas: 0 = el técnico no existe. */
    public int setGlass(int idTec, boolean habilitado) {
        return jdbc.update("UPDATE Tecnico SET ES_GLASS = ? WHERE ID_TEC = ?", habilitado, idTec);
    }

    public String getNombreById(int idTec) {
        return jdbc.queryForObject(
                "SELECT NOMBRE FROM Tecnico WHERE ID_TEC = ?", String.class, idTec);
    }
}
