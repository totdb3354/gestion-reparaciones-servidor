package com.reparaciones.servidor.dao;

import com.reparaciones.servidor.model.Telefono;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class TelefonoDAO {

    private final JdbcTemplate jdbc;

    public TelefonoDAO(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Telefono> getAll() {
        return jdbc.query(
                "SELECT IMEI FROM Telefono",
                (rs, row) -> new Telefono(rs.getString("IMEI")));
    }

    public boolean exists(String imei) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM Telefono WHERE IMEI = ?",
                Integer.class, imei);
        return count != null && count > 0;
    }

    public void insertar(String imei) {
        jdbc.update(
                "INSERT INTO Telefono (IMEI) SELECT ? WHERE NOT EXISTS (SELECT 1 FROM Telefono WHERE IMEI = ?)",
                imei, imei);
    }

    public void eliminar(String imei) {
        jdbc.update("DELETE FROM Telefono WHERE IMEI = ?", imei);
    }
}
