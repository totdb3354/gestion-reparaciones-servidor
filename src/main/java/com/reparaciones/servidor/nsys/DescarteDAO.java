package com.reparaciones.servidor.nsys;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

/** Acceso a Taller_descarte (spec 0.9.22 pieza 3 §3): los IMEI descartados de «No está en NSYS», uno por IMEI. Son unos pocos. */
@Repository
public class DescarteDAO {

    static final String SQL_TODOS = "SELECT d.IMEI_SN, d.ID_USU, u.NOMBRE_USUARIO, d.FECHA, d.MOTIVO FROM Taller_descarte d"
            + " LEFT JOIN Usuario u ON u.ID_USU = d.ID_USU";
    /** Descartar otra vez el mismo IMEI pone quién, cuándo y el motivo de ahora. */
    static final String SQL_DESCARTAR = "INSERT INTO Taller_descarte (IMEI_SN, ID_USU, FECHA, MOTIVO) VALUES (?, ?, ?, ?)"
            + " ON DUPLICATE KEY UPDATE ID_USU = VALUES(ID_USU), FECHA = VALUES(FECHA), MOTIVO = VALUES(MOTIVO)";
    static final String SQL_QUITAR = "DELETE FROM Taller_descarte WHERE IMEI_SN = ?";

    private final JdbcTemplate jdbc;

    public DescarteDAO(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<DescarteTaller> todos() {
        return jdbc.query(SQL_TODOS, DescarteDAO::descarte);
    }

    public void descartar(String imeiSn, int idUsu, String motivo, LocalDateTime ahora) {
        jdbc.update(SQL_DESCARTAR, imeiSn, idUsu, Timestamp.valueOf(ahora), motivo);
    }

    /** «deshacer» de «Ver descartados»: cuántos quitó (0 si no estaba descartado). */
    public int deshacer(String imeiSn) {
        return jdbc.update(SQL_QUITAR, imeiSn);
    }

    /** El repaso tras una importación: fuera los de estos IMEI (ya en NSYS), en un lote. Sin ninguno no escribe. */
    public void quitar(List<String> imeis) {
        if (imeis.isEmpty()) return;
        jdbc.batchUpdate(SQL_QUITAR, imeis.stream().map(i -> new Object[]{i}).toList());
    }

    static DescarteTaller descarte(ResultSet rs, int n) throws SQLException {
        int idUsu = rs.getInt("ID_USU");
        Integer usuario = rs.wasNull() ? null : idUsu;
        return new DescarteTaller(rs.getString("IMEI_SN"), usuario, rs.getString("NOMBRE_USUARIO"),
                rs.getTimestamp("FECHA").toLocalDateTime(), rs.getString("MOTIVO"));
    }
}
