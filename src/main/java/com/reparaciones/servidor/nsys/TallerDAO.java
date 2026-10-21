package com.reparaciones.servidor.nsys;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** Acceso a Taller_cambio (spec 0.9.21 §4): lo del taller por encima de NSYS. Las horas son de Madrid, como las del espejo:
 *  las pone el servidor, nunca la base de datos. */
@Repository
public class TallerDAO {

    static final String COLUMNAS = "c.ID_CAMBIO, c.IMEI_SN, c.DATO, c.VALOR,"
            + " CASE WHEN a.ID_CAMBIO IS NULL THEN c.VALOR_NSYS ELSE a.VALOR END AS VALOR_ANTES, c.VALOR_NSYS, c.ORIGEN,"
            + " c.ID_REP, c.ID_USU, u.NOMBRE_USUARIO, c.MOTIVO, c.FECHA, c.ESTADO, c.CERRADO_EN, c.VALOR_CIERRE,"
            + " c.ID_CAMBIO_ANTERIOR";
    static final String DESDE = " FROM Taller_cambio c LEFT JOIN Taller_cambio a ON a.ID_CAMBIO = c.ID_CAMBIO_ANTERIOR"
            + " LEFT JOIN Usuario u ON u.ID_USU = c.ID_USU";
    static final String ORDEN = " ORDER BY c.FECHA, c.ID_CAMBIO";
    /** El candado del móvil: con dos correcciones a la vez del mismo IMEI, la segunda espera a la primera y la última gana
     *  sin dejar dos VIGENTE del mismo dato (spec §3). */
    static final String SQL_BLOQUEAR = "SELECT IMEI_SN FROM Nsys_movil WHERE IMEI_SN = ? FOR UPDATE";
    static final String SQL_VIGENTE = "SELECT ID_CAMBIO FROM Taller_cambio WHERE IMEI_SN = ? AND DATO = ? AND ESTADO = 'VIGENTE'";
    static final String SQL_SUSTITUIR = "UPDATE Taller_cambio SET ESTADO = 'SUSTITUIDO', CERRADO_EN = ?"
            + " WHERE ID_CAMBIO = ? AND ESTADO = 'VIGENTE'";
    static final String SQL_INSERTAR = "INSERT INTO Taller_cambio (IMEI_SN, DATO, VALOR, VALOR_NSYS, ORIGEN, ID_USU, MOTIVO,"
            + " FECHA, ESTADO, CERRADO_EN, ID_CAMBIO_ANTERIOR) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
    static final String SQL_CERRAR = "UPDATE Taller_cambio SET ESTADO = ?, CERRADO_EN = ?, VALOR_CIERRE = ?"
            + " WHERE ID_CAMBIO = ? AND ESTADO = 'VIGENTE'";

    private final JdbcTemplate jdbc;

    public TallerDAO(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Los cambios VIGENTES de todo el taller, del más antiguo al más reciente. Son unas decenas. */
    public List<CambioTaller> vigentes() {
        return jdbc.query("SELECT " + COLUMNAS + DESDE + " WHERE c.ESTADO = 'VIGENTE'" + ORDEN, TallerDAO::cambio);
    }

    /** Los VIGENTES de estos IMEI, en tandas de {@value NsysDAO#TANDA_IN}, del más antiguo al más reciente en cada tanda. */
    public List<CambioTaller> vigentes(Collection<String> imeis) {
        List<CambioTaller> cambios = new ArrayList<>();
        for (List<String> tanda : NsysDAO.trozos(List.copyOf(imeis), NsysDAO.TANDA_IN))
            cambios.addAll(jdbc.query("SELECT " + COLUMNAS + DESDE + " WHERE c.ESTADO = 'VIGENTE' AND c.IMEI_SN IN ("
                    + NsysDAO.marcas(tanda.size()) + ")" + ORDEN, TallerDAO::cambio, tanda.toArray()));
        return cambios;
    }

    /** Todos los cambios de un IMEI, de cualquier estado, para el rastro. */
    public List<CambioTaller> deImei(String imeiSn) {
        return jdbc.query("SELECT " + COLUMNAS + DESDE + " WHERE c.IMEI_SN = ?" + ORDEN, TallerDAO::cambio, imeiSn);
    }

    /** Un cambio que entra: VIGENTE, o QUITADO si vuelve a lo de NSYS. */
    public record Nuevo(DatoTaller dato, String valor, String valorNsys, CambioTaller.Estado estado) {}

    /** Guarda unos cambios a mano de un móvil, todo o nada: el VIGENTE anterior de cada dato, si lo hay, pasa a SUSTITUIDO
     *  y el nuevo apunta a él (ID_CAMBIO_ANTERIOR, que la pieza 2 usará para deshacer). Un QUITADO nace cerrado. */
    @Transactional
    public void guardar(String imeiSn, List<Nuevo> nuevos, int idUsu, String motivo, LocalDateTime ahora) {
        jdbc.queryForList(SQL_BLOQUEAR, String.class, imeiSn);
        Timestamp t = Timestamp.valueOf(ahora);
        for (Nuevo n : nuevos) {
            List<Integer> vigentes = jdbc.queryForList(SQL_VIGENTE, Integer.class, imeiSn, n.dato().name());
            for (Integer id : vigentes) jdbc.update(SQL_SUSTITUIR, t, id);
            Integer anterior = vigentes.stream().max(Integer::compare).orElse(null);
            boolean cerrado = n.estado() != CambioTaller.Estado.VIGENTE;
            jdbc.update(SQL_INSERTAR, imeiSn, n.dato().name(), n.valor(), n.valorNsys(), CambioTaller.MANO, idUsu, motivo, t,
                    n.estado().name(), cerrado ? t : null, anterior);
        }
    }

    /** El cierre automático de un VIGENTE (PASADO o MANDA_NSYS). 0 si ya no lo era: lo sustituyó una corrección a la vez. */
    public int cerrar(int idCambio, CambioTaller.Estado estado, String valorCierre, LocalDateTime ahora) {
        return jdbc.update(SQL_CERRAR, estado.name(), Timestamp.valueOf(ahora), valorCierre, idCambio);
    }

    static CambioTaller cambio(ResultSet rs, int n) throws SQLException {
        return new CambioTaller(rs.getInt("ID_CAMBIO"), rs.getString("IMEI_SN"), DatoTaller.valueOf(rs.getString("DATO")),
                rs.getString("VALOR"), rs.getString("VALOR_ANTES"), rs.getString("VALOR_NSYS"), rs.getString("ORIGEN"),
                rs.getString("ID_REP"), entero(rs, "ID_USU"), rs.getString("NOMBRE_USUARIO"), rs.getString("MOTIVO"),
                rs.getTimestamp("FECHA").toLocalDateTime(), CambioTaller.Estado.valueOf(rs.getString("ESTADO")),
                fechaHora(rs.getTimestamp("CERRADO_EN")), rs.getString("VALOR_CIERRE"), entero(rs, "ID_CAMBIO_ANTERIOR"));
    }

    private static Integer entero(ResultSet rs, String columna) throws SQLException {
        int valor = rs.getInt(columna);
        return rs.wasNull() ? null : valor;
    }

    private static LocalDateTime fechaHora(Timestamp ts) {
        return ts == null ? null : ts.toLocalDateTime();
    }
}
