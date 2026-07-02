package com.reparaciones.servidor.dao;

import com.reparaciones.servidor.model.Telefono;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
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

    public void insertar(String imei, String modelo, Integer idCli) {
        insertar(imei, modelo, idCli, false);
    }

    /**
     * Upsert de teléfono. Si {@code clienteExplicito} es true, fija ID_CLI al valor
     * dado (incluido NULL → deja el IMEI sin cliente); si es false, usa COALESCE
     * (un idCli null preserva el cliente actual). MODELO siempre con COALESCE.
     */
    public void insertar(String imei, String modelo, Integer idCli, boolean clienteExplicito) {
        String m = (modelo == null || modelo.isBlank()) ? null : modelo;
        String sql = clienteExplicito
                ? "INSERT INTO Telefono (IMEI, MODELO, ID_CLI) VALUES (?, ?, ?)" +
                  " ON DUPLICATE KEY UPDATE MODELO = COALESCE(?, MODELO), ID_CLI = ?"
                : "INSERT INTO Telefono (IMEI, MODELO, ID_CLI) VALUES (?, ?, ?)" +
                  " ON DUPLICATE KEY UPDATE MODELO = COALESCE(?, MODELO), ID_CLI = COALESCE(?, ID_CLI)";
        jdbc.update(sql, imei, m, idCli, m, idCli);
    }

    public void insertar(String imei, String modelo) {
        insertar(imei, modelo, null);
    }

    public void insertar(String imei) {
        insertar(imei, null);
    }

    public String getModelo(String imei) {
        List<String> result = jdbc.query(
                "SELECT MODELO FROM Telefono WHERE IMEI = ?",
                (rs, row) -> rs.getString("MODELO"), imei);
        return result.isEmpty() ? null : result.get(0);
    }

    public Integer getClienteId(String imei) {
        List<Integer> result = jdbc.query(
                "SELECT ID_CLI FROM Telefono WHERE IMEI = ?",
                (rs, row) -> (Integer) rs.getObject("ID_CLI"), imei);
        return result.isEmpty() ? null : result.get(0);
    }

    public void actualizarObservacion(String imei, String observacion, LocalDateTime updatedAt) {
        int filas = jdbc.update(
                "UPDATE Telefono SET OBSERVACION = ? WHERE IMEI = ? AND UPDATED_AT = ?",
                observacion, imei,
                Timestamp.valueOf(updatedAt.truncatedTo(ChronoUnit.SECONDS)));
        if (filas == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Dato modificado por otro usuario");
        }
    }

    public boolean tieneAsignacionesActivas(String imei) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM Reparacion" +
                " WHERE IMEI = ? AND ID_REP LIKE 'A%' AND ID_REP NOT LIKE 'AP%' AND FECHA_FIN IS NULL",
                Integer.class, imei);
        return count != null && count > 0;
    }

    public void actualizarRevisionLogistica(String imei, boolean revisado, LocalDateTime updatedAt) {
        int filas = jdbc.update(
                "UPDATE Telefono SET REVISION_LOGISTICA = ? WHERE IMEI = ? AND UPDATED_AT = ?",
                revisado ? 1 : 0, imei,
                Timestamp.valueOf(updatedAt.truncatedTo(ChronoUnit.SECONDS)));
        if (filas == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Dato modificado por otro usuario");
        }
    }

    public void actualizarCliente(String imei, Integer idCli, LocalDateTime updatedAt) {
        int filas = jdbc.update(
                "UPDATE Telefono SET ID_CLI = ? WHERE IMEI = ? AND UPDATED_AT = ?",
                idCli, imei, Timestamp.valueOf(updatedAt.truncatedTo(ChronoUnit.SECONDS)));
        if (filas == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Dato modificado por otro usuario");
        }
    }

    public void eliminar(String imei) {
        jdbc.update("DELETE FROM Telefono WHERE IMEI = ?", imei);
    }
}
