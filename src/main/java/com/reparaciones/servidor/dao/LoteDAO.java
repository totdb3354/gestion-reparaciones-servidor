package com.reparaciones.servidor.dao;

import com.reparaciones.servidor.model.Lote;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.Statement;
import java.util.List;

@Repository
public class LoteDAO {

    private final JdbcTemplate jdbc;

    public LoteDAO(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public List<Lote> getAll() {
        return jdbc.query(
            "SELECT l.ID_LOTE, l.BATCH_NUMBER, l.ID_PROV, p.NOMBRE AS PROVEEDOR, l.FECHA_IMPORT," +
            "       l.NOTA, l.UPDATED_AT, COUNT(t.IMEI) AS NUM_TELEFONOS" +
            " FROM Lote l" +
            " JOIN Proveedor p ON p.ID_PROV = l.ID_PROV" +
            " LEFT JOIN Telefono t ON t.ID_LOTE = l.ID_LOTE" +
            " GROUP BY l.ID_LOTE, l.BATCH_NUMBER, l.ID_PROV, p.NOMBRE, l.FECHA_IMPORT, l.NOTA, l.UPDATED_AT" +
            " ORDER BY l.FECHA_IMPORT DESC",
            (rs, row) -> new Lote(
                rs.getInt("ID_LOTE"), rs.getString("BATCH_NUMBER"), rs.getInt("ID_PROV"),
                rs.getString("PROVEEDOR"), rs.getTimestamp("FECHA_IMPORT").toLocalDateTime(),
                rs.getString("NOTA"), rs.getInt("NUM_TELEFONOS"),
                rs.getTimestamp("UPDATED_AT").toLocalDateTime()));
    }

    /** Devuelve el ID del lote batch+proveedor, creándolo si no existe (re-importaciones del mismo batch reutilizan el lote). */
    public int obtenerOCrear(String batchNumber, int idProv, String nota) {
        List<Integer> existente = jdbc.query(
            "SELECT ID_LOTE FROM Lote WHERE BATCH_NUMBER = ? AND ID_PROV = ?",
            (rs, row) -> rs.getInt("ID_LOTE"), batchNumber, idProv);
        if (!existente.isEmpty()) return existente.get(0);
        GeneratedKeyHolder kh = new GeneratedKeyHolder();
        jdbc.update(con -> {
            var ps = con.prepareStatement(
                "INSERT INTO Lote (BATCH_NUMBER, ID_PROV, NOTA) VALUES (?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, batchNumber);
            ps.setInt(2, idProv);
            ps.setString(3, nota);
            return ps;
        }, kh);
        return kh.getKey().intValue();
    }
}
