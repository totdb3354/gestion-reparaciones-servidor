package com.reparaciones.servidor.dao;

import com.reparaciones.servidor.model.SolicitudStock;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class SolicitudStockDAO {

    private final JdbcTemplate jdbc;

    private static final RowMapper<SolicitudStock> MAPPER = (rs, row) -> new SolicitudStock(
            rs.getInt("ID_SOL"),
            rs.getInt("ID_COM"),
            rs.getString("TIPO"),
            rs.getInt("ID_USU"),
            rs.getString("NOMBRE_USUARIO"),
            rs.getString("DESCRIPCION"),
            rs.getString("ESTADO"),
            rs.getTimestamp("FECHA").toLocalDateTime()
    );

    private static final String SELECT =
            "SELECT ss.ID_SOL, ss.ID_COM, c.TIPO, ss.ID_USU, u.NOMBRE_USUARIO," +
            " ss.DESCRIPCION, ss.ESTADO, ss.FECHA" +
            " FROM Solicitud_Stock ss" +
            " JOIN Componente c ON ss.ID_COM = c.ID_COM" +
            " JOIN Usuario    u ON ss.ID_USU = u.ID_USU";

    public SolicitudStockDAO(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<SolicitudStock> getSolicitudes(String estado) {
        if (estado != null) {
            return jdbc.query(SELECT + " WHERE ss.ESTADO = ? ORDER BY ss.FECHA DESC",
                    MAPPER, estado);
        }
        return jdbc.query(SELECT + " ORDER BY ss.FECHA DESC", MAPPER);
    }

    public int contarPendientes() {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM Solicitud_Stock WHERE ESTADO = 'PENDIENTE'",
                Integer.class);
    }

    public void insertar(int idCom, int idUsu, String descripcion) {
        jdbc.update(
                "INSERT INTO Solicitud_Stock (ID_COM, ID_USU, DESCRIPCION) VALUES (?, ?, ?)",
                idCom, idUsu, descripcion);
    }

    public void actualizarEstado(int idSol, String estado) {
        // Solo actúa si sigue PENDIENTE — evita doble gestión/rechazo concurrente
        jdbc.update("UPDATE Solicitud_Stock SET ESTADO = ? WHERE ID_SOL = ? AND ESTADO = 'PENDIENTE'",
                estado, idSol);
    }

    public void borrar(int idSol) {
        jdbc.update("DELETE FROM Solicitud_Stock WHERE ID_SOL = ?", idSol);
    }
}
