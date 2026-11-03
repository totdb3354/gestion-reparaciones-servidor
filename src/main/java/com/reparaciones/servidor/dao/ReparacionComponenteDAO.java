package com.reparaciones.servidor.dao;

import com.reparaciones.servidor.model.FilaReparacion;
import com.reparaciones.servidor.model.SolicitudResumen;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public class ReparacionComponenteDAO {

    private final JdbcTemplate jdbc;

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

    public List<FilaReparacion> getSolicitudesPorAsignacion(String idAsignacion) {
        return jdbc.query(
                "SELECT rc.ID_COM, rc.CANTIDAD, rc.ES_REUTILIZADO, rc.OBSERVACIONES," +
                " rc.ES_SOLICITUD, rc.DESCRIPCION_SOLICITUD, rc.ESTADO_SOLICITUD," +
                " CASE WHEN EXISTS(SELECT 1 FROM Compra_componente cc" +
                "  WHERE cc.ID_COM = rc.ID_COM AND cc.ESTADO = 'en_camino') THEN 1 ELSE 0 END AS EN_CAMINO" +
                " FROM Reparacion_componente rc WHERE rc.ID_REP = ? AND rc.ES_SOLICITUD = 1",
                (rs, row) -> {
                    FilaReparacion f = new FilaReparacion();
                    f.idCom                = rs.getInt("ID_COM");
                    f.cantidad             = rs.getInt("CANTIDAD");
                    f.reutilizado          = rs.getBoolean("ES_REUTILIZADO");
                    f.observacion          = rs.getString("OBSERVACIONES");
                    f.esSolicitud          = rs.getBoolean("ES_SOLICITUD");
                    f.descripcionSolicitud = rs.getString("DESCRIPCION_SOLICITUD");
                    f.estadoSolicitud      = rs.getString("ESTADO_SOLICITUD");
                    f.enCamino             = rs.getBoolean("EN_CAMINO");
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

    /** ID_COM de una solicitud urgente, o null si no existe o no tiene componente. El lote de pedidos lo usa para
     *  casar la solicitud con su línea (sub-proyecto 4b). */
    public Integer getIdComDeSolicitud(int idRc) {
        List<Integer> filas = jdbc.query("SELECT ID_COM FROM Reparacion_componente WHERE ID_RC = ?",
                (rs, row) -> rs.getObject("ID_COM", Integer.class), idRc);
        return filas.isEmpty() ? null : filas.get(0);
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
