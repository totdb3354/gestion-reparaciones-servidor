package com.reparaciones.servidor.dao;

import com.reparaciones.servidor.model.FilaReparacion;
import com.reparaciones.servidor.model.ReparacionComponente;
import com.reparaciones.servidor.model.SolicitudResumen;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public class ReparacionComponenteDAO {

    private final JdbcTemplate jdbc;

    private static final RowMapper<ReparacionComponente> MAPPER = (rs, row) -> new ReparacionComponente(
            rs.getInt("ID_RC"),
            rs.getString("ID_REP"),
            rs.getInt("ID_COM"),
            rs.getBoolean("ES_REUTILIZADO"),
            rs.getBoolean("ES_INCIDENCIA"),
            rs.getBoolean("ES_RESUELTO"),
            rs.getString("INCIDENCIA"),
            rs.getString("OBSERVACIONES"),
            rs.getBoolean("ES_SOLICITUD"),
            rs.getString("DESCRIPCION_SOLICITUD"),
            rs.getString("ESTADO_SOLICITUD")
    );

    private static final RowMapper<SolicitudResumen> SOL_MAPPER = (rs, row) -> new SolicitudResumen(
            rs.getInt("ID_RC"),
            rs.getString("ID_REP"),
            rs.getString("IMEI"),
            rs.getString("NOMBRE_TEC"),
            rs.getInt("ID_COM"),
            rs.getString("TIPO_COM"),
            rs.getString("DESCRIPCION_SOLICITUD"),
            rs.getString("ESTADO_SOLICITUD"),
            rs.getTimestamp("FECHA_ASIG").toLocalDateTime()
    );

    private static final String SOL_SELECT =
            "SELECT rc.ID_RC, rc.ID_REP, r.IMEI, t.NOMBRE AS NOMBRE_TEC," +
            " COALESCE(rc.ID_COM, 0) AS ID_COM, c.TIPO AS TIPO_COM," +
            " rc.DESCRIPCION_SOLICITUD, rc.ESTADO_SOLICITUD, r.FECHA_ASIG" +
            " FROM Reparacion_componente rc" +
            " JOIN Reparacion r ON rc.ID_REP = r.ID_REP" +
            " JOIN Tecnico t ON r.ID_TEC = t.ID_TEC" +
            " LEFT JOIN Componente c ON rc.ID_COM = c.ID_COM" +
            " WHERE rc.ES_SOLICITUD = 1";

    public ReparacionComponenteDAO(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<ReparacionComponente> getByReparacion(String idRep) {
        return jdbc.query(
                "SELECT ID_RC, ID_REP, ID_COM, ES_REUTILIZADO, ES_INCIDENCIA, ES_RESUELTO," +
                " INCIDENCIA, OBSERVACIONES, ES_SOLICITUD, DESCRIPCION_SOLICITUD, ESTADO_SOLICITUD" +
                " FROM Reparacion_componente WHERE ID_REP = ?",
                MAPPER, idRep);
    }

    public List<FilaReparacion> getSolicitudesPorAsignacion(String idAsignacion) {
        return jdbc.query(
                "SELECT ID_COM, CANTIDAD, ES_REUTILIZADO, OBSERVACIONES," +
                " ES_SOLICITUD, DESCRIPCION_SOLICITUD, ESTADO_SOLICITUD" +
                " FROM Reparacion_componente WHERE ID_REP = ? AND ES_SOLICITUD = 1",
                (rs, row) -> {
                    FilaReparacion f = new FilaReparacion();
                    f.idCom                = rs.getInt("ID_COM");
                    f.cantidad             = rs.getInt("CANTIDAD");
                    f.reutilizado          = rs.getBoolean("ES_REUTILIZADO");
                    f.observacion          = rs.getString("OBSERVACIONES");
                    f.esSolicitud          = rs.getBoolean("ES_SOLICITUD");
                    f.descripcionSolicitud = rs.getString("DESCRIPCION_SOLICITUD");
                    f.estadoSolicitud      = rs.getString("ESTADO_SOLICITUD");
                    return f;
                },
                idAsignacion);
    }

    public List<SolicitudResumen> getSolicitudes(String estado) {
        if (estado != null) {
            return jdbc.query(SOL_SELECT + " AND rc.ESTADO_SOLICITUD = ? ORDER BY r.FECHA_ASIG DESC",
                    SOL_MAPPER, estado);
        }
        return jdbc.query(SOL_SELECT + " ORDER BY r.FECHA_ASIG DESC", SOL_MAPPER);
    }

    public int contarSolicitudesPendientes() {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM Reparacion_componente" +
                " WHERE ES_SOLICITUD = 1 AND ESTADO_SOLICITUD = 'PENDIENTE'",
                Integer.class);
    }

    @Transactional
    public void insertar(String idRep, Integer idCom, boolean esReutilizado,
                          boolean esIncidencia, boolean esResuelto, String incidencia,
                          String observaciones, boolean esSolicitud,
                          String descripcionSolicitud, int cantidad) {
        jdbc.update(
                "INSERT INTO Reparacion_componente" +
                " (ID_REP, ID_COM, ES_REUTILIZADO, ES_INCIDENCIA, ES_RESUELTO," +
                " INCIDENCIA, OBSERVACIONES, ES_SOLICITUD, DESCRIPCION_SOLICITUD, CANTIDAD)" +
                " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                idRep, idCom, esReutilizado, esIncidencia, esResuelto,
                incidencia, observaciones, esSolicitud, descripcionSolicitud, cantidad);
        if (!esReutilizado && !esSolicitud && idCom != null) {
            jdbc.update("UPDATE Componente SET STOCK = STOCK - ? WHERE ID_COM = ?", cantidad, idCom);
        }
    }

    @Transactional
    public void eliminar(String idRep, int idCom) {
        record RcRow(boolean esReutilizado, boolean esSolicitud, int cantidad) {}
        RcRow rc = jdbc.queryForObject(
                "SELECT ES_REUTILIZADO, ES_SOLICITUD, CANTIDAD" +
                " FROM Reparacion_componente WHERE ID_REP = ? AND ID_COM = ?",
                (rs, row) -> new RcRow(rs.getBoolean("ES_REUTILIZADO"),
                        rs.getBoolean("ES_SOLICITUD"), rs.getInt("CANTIDAD")),
                idRep, idCom);
        jdbc.update("DELETE FROM Reparacion_componente WHERE ID_REP = ? AND ID_COM = ?", idRep, idCom);
        if (rc != null && !rc.esReutilizado() && !rc.esSolicitud()) {
            jdbc.update("UPDATE Componente SET STOCK = STOCK + ? WHERE ID_COM = ?", rc.cantidad(), idCom);
        }
    }

    @Transactional
    public void marcarIncidencia(String idRep, String comentario) {
        jdbc.update(
                "UPDATE Reparacion_componente SET ES_INCIDENCIA = 1, INCIDENCIA = ? WHERE ID_REP = ?",
                comentario, idRep);
    }

    @Transactional
    public void borrarIncidencia(String idRep) {
        jdbc.update(
                "UPDATE Reparacion_componente SET ES_INCIDENCIA = 0, INCIDENCIA = NULL WHERE ID_REP = ?",
                idRep);
        List<String> asigs = jdbc.query(
                "SELECT ID_REP FROM Reparacion WHERE ID_REP_ANTERIOR = ? AND ID_REP LIKE 'A%' AND FECHA_FIN IS NULL",
                (rs, row) -> rs.getString(1), idRep);
        for (String idAsig : asigs) {
            jdbc.update("DELETE FROM Reparacion_componente WHERE ID_REP = ?", idAsig);
            jdbc.update("DELETE FROM Reparacion WHERE ID_REP = ?", idAsig);
        }
    }

    public void actualizarEstadoSolicitud(int idRc, String estado) {
        if ("PENDIENTE".equalsIgnoreCase(estado)) {
            // Al recuperar también restauramos ES_SOLICITUD por si otro admin limpió antes
            jdbc.update("UPDATE Reparacion_componente SET ESTADO_SOLICITUD = 'PENDIENTE', ES_SOLICITUD = 1 WHERE ID_RC = ?",
                    idRc);
        } else if ("GESTIONADA".equalsIgnoreCase(estado)) {
            // Solo actúa si no está ya gestionada — evita doble pedido concurrente
            jdbc.update("UPDATE Reparacion_componente SET ESTADO_SOLICITUD = 'GESTIONADA' WHERE ID_RC = ? AND ESTADO_SOLICITUD != 'GESTIONADA'",
                    idRc);
        } else if ("RECHAZADA".equalsIgnoreCase(estado)) {
            // Solo actúa si sigue PENDIENTE — evita sobreescribir una solicitud ya gestionada
            jdbc.update("UPDATE Reparacion_componente SET ESTADO_SOLICITUD = 'RECHAZADA' WHERE ID_RC = ? AND ESTADO_SOLICITUD = 'PENDIENTE'",
                    idRc);
        } else {
            jdbc.update("UPDATE Reparacion_componente SET ESTADO_SOLICITUD = ? WHERE ID_RC = ?",
                    estado, idRc);
        }
    }

    public void limpiarSolicitud(int idRc) {
        // Solo actúa si sigue RECHAZADA — evita borrar una solicitud recuperada concurrentemente
        jdbc.update("UPDATE Reparacion_componente SET ES_SOLICITUD = 0 WHERE ID_RC = ? AND ESTADO_SOLICITUD = 'RECHAZADA'",
                idRc);
    }
}
