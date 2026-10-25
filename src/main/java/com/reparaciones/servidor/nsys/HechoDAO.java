package com.reparaciones.servidor.nsys;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

/** Acceso a Taller_hecho (spec 0.9.22 §3.6): las marcas «Hecho» de «Por pasar a NSYS», una por IMEI y tarjeta. Son unas
 *  decenas. */
@Repository
public class HechoDAO {

    static final String SQL_TODAS = "SELECT h.IMEI_SN, h.TARJETA, h.ID_USU, u.NOMBRE_USUARIO, h.FECHA, h.VOLVIO FROM Taller_hecho h"
            + " LEFT JOIN Usuario u ON u.ID_USU = h.ID_USU";
    /** Marcar otra vez una fila ya marcada pone quién y cuándo de ahora y quita el «volvió». */
    static final String SQL_MARCAR = "INSERT INTO Taller_hecho (IMEI_SN, TARJETA, ID_USU, FECHA, VOLVIO) VALUES (?, ?, ?, ?, NULL)"
            + " ON DUPLICATE KEY UPDATE ID_USU = VALUES(ID_USU), FECHA = VALUES(FECHA), VOLVIO = NULL";
    static final String SQL_DESMARCAR = "DELETE FROM Taller_hecho WHERE IMEI_SN = ? AND TARJETA = ?";
    static final String SQL_VOLVIO = "UPDATE Taller_hecho SET VOLVIO = ? WHERE IMEI_SN = ? AND TARJETA = ? AND VOLVIO IS NULL";

    private final JdbcTemplate jdbc;

    public HechoDAO(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<HechoTaller> todas() {
        return jdbc.query(SQL_TODAS, HechoDAO::hecho);
    }

    /** «Hecho» o «Marcar todo»: marca estos IMEI en la tarjeta, por lotes de mil (NsysDAO#trozos). */
    @Transactional
    public void marcar(TarjetaHecho tarjeta, Collection<String> imeis, int idUsu, LocalDateTime ahora) {
        Timestamp t = Timestamp.valueOf(ahora);
        List<Object[]> filas = imeis.stream().map(imei -> new Object[]{imei, tarjeta.name(), idUsu, t}).toList();
        for (List<Object[]> lote : NsysDAO.trozos(filas)) jdbc.batchUpdate(SQL_MARCAR, lote);
    }

    /** «deshacer»: quita la marca. */
    public int desmarcar(TarjetaHecho tarjeta, String imeiSn) {
        return jdbc.update(SQL_DESMARCAR, imeiSn, tarjeta.name());
    }

    /** El repaso tras una importación (spec §3.6): unas marcas se borran y otras «vuelven», todo o nada. Sin nada que hacer no
     *  escribe. */
    @Transactional
    public void repasar(List<HechoTaller> borrar, List<HechoTaller> vuelven, LocalDateTime ahora) {
        if (!borrar.isEmpty())
            jdbc.batchUpdate(SQL_DESMARCAR, borrar.stream().map(h -> new Object[]{h.imeiSn(), h.tarjeta().name()}).toList());
        Timestamp t = Timestamp.valueOf(ahora);
        if (!vuelven.isEmpty())
            jdbc.batchUpdate(SQL_VOLVIO, vuelven.stream().map(h -> new Object[]{t, h.imeiSn(), h.tarjeta().name()}).toList());
    }

    static HechoTaller hecho(ResultSet rs, int n) throws SQLException {
        int idUsu = rs.getInt("ID_USU");
        Integer usuario = rs.wasNull() ? null : idUsu;
        Timestamp volvio = rs.getTimestamp("VOLVIO");
        return new HechoTaller(rs.getString("IMEI_SN"), TarjetaHecho.valueOf(rs.getString("TARJETA")), usuario,
                rs.getString("NOMBRE_USUARIO"), rs.getTimestamp("FECHA").toLocalDateTime(),
                volvio == null ? null : volvio.toLocalDateTime());
    }
}
