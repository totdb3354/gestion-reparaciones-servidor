package com.reparaciones.servidor.nsys;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

/** Acceso a Taller_vuelta_ignorada (spec 0.9.27 §3): los vendidos o de baja ignorados en «Por pasar a NSYS», uno por IMEI. Son
 *  unos pocos. */
@Repository
public class VueltaIgnoradaDAO {

    static final String SQL_TODOS = "SELECT i.IMEI_SN, i.ID_USU, u.NOMBRE_USUARIO, i.FECHA FROM Taller_vuelta_ignorada i"
            + " LEFT JOIN Usuario u ON u.ID_USU = i.ID_USU";
    /** Ignorar otra vez el mismo IMEI pone quién y cuándo de ahora. */
    static final String SQL_IGNORAR = "INSERT INTO Taller_vuelta_ignorada (IMEI_SN, ID_USU, FECHA) VALUES (?, ?, ?)"
            + " ON DUPLICATE KEY UPDATE ID_USU = VALUES(ID_USU), FECHA = VALUES(FECHA)";
    static final String SQL_QUITAR = "DELETE FROM Taller_vuelta_ignorada WHERE IMEI_SN = ?";

    private final JdbcTemplate jdbc;

    public VueltaIgnoradaDAO(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<VueltaIgnorada> todos() {
        return jdbc.query(SQL_TODOS, VueltaIgnoradaDAO::ignorado);
    }

    public void ignorar(String imeiSn, int idUsu, LocalDateTime ahora) {
        jdbc.update(SQL_IGNORAR, imeiSn, idUsu, Timestamp.valueOf(ahora));
    }

    /** «deshacer» de «Ver ignorados»: cuántos quitó (0 si no estaba ignorado). */
    public int deshacer(String imeiSn) {
        return jdbc.update(SQL_QUITAR, imeiSn);
    }

    /** El repaso tras una importación: fuera los de estos IMEI (otra vez en la empresa), en un lote. Sin ninguno no escribe. */
    public void quitar(List<String> imeis) {
        if (imeis.isEmpty()) return;
        jdbc.batchUpdate(SQL_QUITAR, imeis.stream().map(i -> new Object[]{i}).toList());
    }

    static VueltaIgnorada ignorado(ResultSet rs, int n) throws SQLException {
        int idUsu = rs.getInt("ID_USU");
        Integer usuario = rs.wasNull() ? null : idUsu;
        return new VueltaIgnorada(rs.getString("IMEI_SN"), usuario, rs.getString("NOMBRE_USUARIO"),
                rs.getTimestamp("FECHA").toLocalDateTime());
    }
}
