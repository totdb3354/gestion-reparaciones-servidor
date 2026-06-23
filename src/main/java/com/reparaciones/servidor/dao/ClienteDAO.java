package com.reparaciones.servidor.dao;

import com.reparaciones.servidor.model.Cliente;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Repository
public class ClienteDAO {

    private final JdbcTemplate jdbc;

    private static final RowMapper<Cliente> MAPPER = (rs, row) -> new Cliente(
            rs.getInt("ID_CLI"),
            rs.getString("NOMBRE"),
            rs.getBoolean("ACTIVO"),
            rs.getTimestamp("UPDATED_AT").toLocalDateTime());

    public ClienteDAO(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public List<Cliente> getAll() {
        return jdbc.query(
                "SELECT ID_CLI, NOMBRE, ACTIVO, UPDATED_AT FROM Cliente ORDER BY NOMBRE", MAPPER);
    }

    public List<Cliente> getActivos() {
        return jdbc.query(
                "SELECT ID_CLI, NOMBRE, ACTIVO, UPDATED_AT FROM Cliente WHERE ACTIVO = 1 ORDER BY NOMBRE",
                MAPPER);
    }

    public void insertar(String nombre) {
        jdbc.update("INSERT INTO Cliente (NOMBRE, ACTIVO) VALUES (?, 1)", nombre);
    }

    public void editar(int idCli, String nombre, LocalDateTime updatedAt) {
        int filas = jdbc.update(
                "UPDATE Cliente SET NOMBRE = ? WHERE ID_CLI = ? AND UPDATED_AT = ?",
                nombre, idCli, Timestamp.valueOf(updatedAt.truncatedTo(ChronoUnit.SECONDS)));
        if (filas == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Dato modificado por otro usuario");
        }
    }

    public void setActivo(int idCli, boolean activo, LocalDateTime updatedAt) {
        int filas = jdbc.update(
                "UPDATE Cliente SET ACTIVO = ? WHERE ID_CLI = ? AND UPDATED_AT = ?",
                activo ? 1 : 0, idCli, Timestamp.valueOf(updatedAt.truncatedTo(ChronoUnit.SECONDS)));
        if (filas == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Dato modificado por otro usuario");
        }
    }

    public boolean tieneTelefonos(int idCli) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM Telefono WHERE ID_CLI = ?", Integer.class, idCli);
        return count != null && count > 0;
    }

    public String getNombreById(int idCli) {
        return jdbc.queryForObject(
                "SELECT NOMBRE FROM Cliente WHERE ID_CLI = ?", String.class, idCli);
    }
}
