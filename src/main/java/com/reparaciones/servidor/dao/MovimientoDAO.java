package com.reparaciones.servidor.dao;

import com.reparaciones.servidor.model.MovimientoTelefono;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Trazabilidad append-only del ciclo de vida (F2c): un movimiento por transición
 * de ESTADO + enviar/devolución. Abrir/cerrar trabajos NO escribe movimientos
 * (spec F2c §5): ese ir-y-venir ya lo cuentan los trabajos con sus fechas.
 */
@Repository
public class MovimientoDAO {

    private final JdbcTemplate jdbc;

    public MovimientoDAO(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    /** Caja derivada de un ESTADO almacenado (vocabulario de Movimiento_telefono). */
    public static String ubicacionDe(String estado, Integer idCli) {
        if (estado == null) return null;
        return switch (estado) {
            case "RECIBIDO"    -> "ALMACEN";
            case "EN_REVISION" -> "PARA_REVISAR";
            case "BLOQUEADO"   -> "BLOQUEO";
            case "OK"          -> idCli != null ? "PEDIDOS" : "LISTOS";
            case "ENVIADO"     -> "ENVIADO";
            case "DESGUACE"    -> "DESGUACE";
            default            -> null;
        };
    }

    public void registrar(String imei, String origen, String destino, int idUsu,
                          String motivo, String referencia) {
        jdbc.update("INSERT INTO Movimiento_telefono (IMEI, UBICACION_ORIGEN, UBICACION_DESTINO, ID_USU, MOTIVO, REFERENCIA) VALUES (?, ?, ?, ?, ?, ?)",
                imei, origen, destino, idUsu, motivo, referencia);
    }

    /** Línea de vida cronológica (con nombre de usuario) para el historial de la ficha. */
    public List<MovimientoTelefono> getPorImei(String imei) {
        return jdbc.query(
                "SELECT m.ID_MOV, m.IMEI, m.UBICACION_ORIGEN, m.UBICACION_DESTINO, m.FECHA," +
                "       m.ID_USU, u.NOMBRE_USUARIO, m.MOTIVO, m.REFERENCIA" +
                " FROM Movimiento_telefono m" +
                " JOIN Usuario u ON u.ID_USU = m.ID_USU" +
                " WHERE m.IMEI = ? ORDER BY m.FECHA, m.ID_MOV",
                (rs, row) -> {
                    MovimientoTelefono mv = new MovimientoTelefono();
                    mv.setIdMov(rs.getInt("ID_MOV"));
                    mv.setImei(rs.getString("IMEI"));
                    mv.setUbicacionOrigen(rs.getString("UBICACION_ORIGEN"));
                    mv.setUbicacionDestino(rs.getString("UBICACION_DESTINO"));
                    mv.setFecha(rs.getTimestamp("FECHA").toLocalDateTime());
                    mv.setIdUsu((Integer) rs.getObject("ID_USU"));
                    mv.setUsuario(rs.getString("NOMBRE_USUARIO"));
                    mv.setMotivo(rs.getString("MOTIVO"));
                    mv.setReferencia(rs.getString("REFERENCIA"));
                    return mv;
                }, imei);
    }
}
