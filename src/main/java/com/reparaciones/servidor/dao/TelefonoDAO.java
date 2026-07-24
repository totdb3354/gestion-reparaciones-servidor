package com.reparaciones.servidor.dao;

import com.reparaciones.servidor.model.Telefono;
import com.reparaciones.servidor.model.TelefonoInventario;
import com.reparaciones.servidor.model.VerificacionImei;
import com.reparaciones.servidor.service.UbicacionDerivador;
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

    public void actualizarAtributos(String imei, String modelo, Integer storageGb, String color,
                                    String gradoProveedor, String gradoPropio, Boolean esEsim,
                                    LocalDateTime updatedAt) {
        int filas = jdbc.update(
            "UPDATE Telefono SET MODELO = ?, STORAGE_GB = ?, COLOR = ?, GRADO_PROVEEDOR = ?, GRADO_PROPIO = ?," +
            " ES_ESIM = COALESCE(?, ES_ESIM)" +
            " WHERE IMEI = ? AND UPDATED_AT = ?",
            modelo, storageGb, color, gradoProveedor, gradoPropio, esEsim, imei,
            Timestamp.valueOf(updatedAt.truncatedTo(ChronoUnit.SECONDS)));
        if (filas == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Dato modificado por otro usuario");
        }
    }

    public void eliminar(String imei) {
        jdbc.update("DELETE FROM Telefono WHERE IMEI = ?", imei);
    }

    public List<TelefonoInventario> getInventario() {
        String sql =
            "SELECT t.IMEI, t.MODELO, t.STORAGE_GB, t.COLOR, t.GRADO_PROVEEDOR, t.GRADO_PROPIO, t.ES_ESIM," +
            "       t.ESTADO, t.ES_DEVOLUCION, t.OBSERVACION, t.REVISION_LOGISTICA, t.UPDATED_AT," +
            "       t.ID_CLI, c.NOMBRE AS CLIENTE, t.ID_LOTE, l.BATCH_NUMBER, l.FECHA_IMPORT, p.NOMBRE AS PROVEEDOR," +
            "       COALESCE(w.PUL_ABIERTOS,0) PUL_ABIERTOS, COALESCE(w.GLASS_ABIERTOS,0) GLASS_ABIERTOS," +
            "       COALESCE(w.NORMAL_ABIERTOS,0) NORMAL_ABIERTOS, COALESCE(w.REP_HECHAS,0) REP_HECHAS," +
            "       COALESCE(w.GLASS_HECHAS,0) GLASS_HECHAS, COALESCE(w.PUL_HECHOS,0) PUL_HECHOS," +
            "       w.ULTIMO_TRABAJO," +
            "       COALESCE(i.INC_ABIERTAS,0) INC_ABIERTAS, COALESCE(s.SOL_PENDIENTES,0) SOL_PENDIENTES" +
            "       , rv.FECHA_CREACION AS REV_DESDE, rv.EST_FECHA, ue.NOMBRE_USUARIO AS EST_USUARIO," +
            "       rv.FUN_FECHA, uf.NOMBRE_USUARIO AS FUN_USUARIO, rv.FUN_BATERIA_PCT," +
            "       (rv.ID_REVISION IS NOT NULL AND EXISTS (SELECT 1 FROM Reparacion rr" +
            "           WHERE rr.IMEI = t.IMEI AND rr.FECHA_FIN IS NOT NULL" +
            "             AND rr.FECHA_FIN >= rv.FECHA_CREACION)) AS REP_TRAS_REVISION" +
            " FROM Telefono t" +
            " LEFT JOIN Cliente c   ON c.ID_CLI  = t.ID_CLI" +
            " LEFT JOIN Lote l      ON l.ID_LOTE = t.ID_LOTE" +
            " LEFT JOIN Proveedor p ON p.ID_PROV = l.ID_PROV" +
            " LEFT JOIN Revision rv ON rv.ID_REVISION = (SELECT MAX(r0.ID_REVISION) FROM Revision r0 WHERE r0.IMEI = t.IMEI)" +
            " LEFT JOIN Usuario ue ON ue.ID_USU = rv.EST_ID_USU" +
            " LEFT JOIN Usuario uf ON uf.ID_USU = rv.FUN_ID_USU" +
            " LEFT JOIN (SELECT r.IMEI," +
            "        SUM(r.ID_REP LIKE 'AP%' AND r.FECHA_FIN IS NULL) AS PUL_ABIERTOS," +
            "        SUM(r.ID_REP LIKE 'AG%' AND r.FECHA_FIN IS NULL) AS GLASS_ABIERTOS," +
            "        SUM(r.ID_REP LIKE 'A%' AND r.ID_REP NOT LIKE 'AP%' AND r.ID_REP NOT LIKE 'AG%'" +
            "            AND r.FECHA_FIN IS NULL) AS NORMAL_ABIERTOS," +
            "        SUM(r.ID_REP LIKE 'R%') AS REP_HECHAS," +
            "        SUM(r.ID_REP LIKE 'G%') AS GLASS_HECHAS," +
            "        SUM(r.ID_REP LIKE 'P%') AS PUL_HECHOS," +
            "        MAX(COALESCE(r.FECHA_FIN, r.FECHA_ASIG)) AS ULTIMO_TRABAJO" +
            "    FROM Reparacion r GROUP BY r.IMEI) w ON w.IMEI = t.IMEI" +
            " LEFT JOIN (SELECT r2.IMEI, COUNT(*) AS INC_ABIERTAS" +
            "    FROM Reparacion_componente rc JOIN Reparacion r2 ON r2.ID_REP = rc.ID_REP" +
            "    WHERE rc.ES_INCIDENCIA AND NOT rc.ES_RESUELTO GROUP BY r2.IMEI) i ON i.IMEI = t.IMEI" +
            " LEFT JOIN (SELECT r3.IMEI, COUNT(*) AS SOL_PENDIENTES" +
            "    FROM Reparacion_componente rc2 JOIN Reparacion r3 ON r3.ID_REP = rc2.ID_REP" +
            "    WHERE rc2.ES_SOLICITUD AND rc2.ESTADO_SOLICITUD = 'PENDIENTE' AND r3.FECHA_FIN IS NULL" +
            "    GROUP BY r3.IMEI) s ON s.IMEI = t.IMEI";
        return jdbc.query(sql, (rs, row) -> {
            var inv = new TelefonoInventario();
            inv.setImei(rs.getString("IMEI"));
            inv.setModelo(rs.getString("MODELO"));
            inv.setStorageGb((Integer) rs.getObject("STORAGE_GB"));
            inv.setColor(rs.getString("COLOR"));
            inv.setGradoProveedor(rs.getString("GRADO_PROVEEDOR"));
            inv.setGradoPropio(rs.getString("GRADO_PROPIO"));
            inv.setEsEsim(rs.getBoolean("ES_ESIM"));
            inv.setEstado(rs.getString("ESTADO"));
            inv.setEsDevolucion(rs.getBoolean("ES_DEVOLUCION"));
            inv.setObservacion(rs.getString("OBSERVACION"));
            inv.setRevisionLogistica(rs.getBoolean("REVISION_LOGISTICA"));
            inv.setTelefonoUpdatedAt(rs.getTimestamp("UPDATED_AT").toLocalDateTime());
            inv.setIdCli((Integer) rs.getObject("ID_CLI"));
            inv.setCliente(rs.getString("CLIENTE"));
            inv.setIdLote((Integer) rs.getObject("ID_LOTE"));
            inv.setBatchNumber(rs.getString("BATCH_NUMBER"));
            inv.setProveedor(rs.getString("PROVEEDOR"));
            inv.setPulAbiertos(rs.getInt("PUL_ABIERTOS"));
            inv.setGlassAbiertos(rs.getInt("GLASS_ABIERTOS"));
            inv.setNormalAbiertos(rs.getInt("NORMAL_ABIERTOS"));
            inv.setRepHechas(rs.getInt("REP_HECHAS"));
            inv.setGlassHechas(rs.getInt("GLASS_HECHAS"));
            inv.setPulHechos(rs.getInt("PUL_HECHOS"));
            inv.setIncAbiertas(rs.getInt("INC_ABIERTAS"));
            inv.setSolicitudesPendientes(rs.getInt("SOL_PENDIENTES"));
            Timestamp ultimoTrabajo = rs.getTimestamp("ULTIMO_TRABAJO");
            Timestamp fechaImport   = rs.getTimestamp("FECHA_IMPORT");
            LocalDateTime ultima = null;
            if (ultimoTrabajo != null) ultima = ultimoTrabajo.toLocalDateTime();
            if (fechaImport != null && (ultima == null || fechaImport.toLocalDateTime().isAfter(ultima)))
                ultima = fechaImport.toLocalDateTime();
            inv.setUltimaActividad(ultima);
            Timestamp revDesde = rs.getTimestamp("REV_DESDE");
            inv.setRevDesde(revDesde == null ? null : revDesde.toLocalDateTime());
            Timestamp estFecha = rs.getTimestamp("EST_FECHA");
            inv.setEstFecha(estFecha == null ? null : estFecha.toLocalDateTime());
            inv.setEstUsuario(rs.getString("EST_USUARIO"));
            Timestamp funFecha = rs.getTimestamp("FUN_FECHA");
            inv.setFunFecha(funFecha == null ? null : funFecha.toLocalDateTime());
            inv.setFunUsuario(rs.getString("FUN_USUARIO"));
            inv.setFunBateriaPct((Integer) rs.getObject("FUN_BATERIA_PCT"));
            var d = UbicacionDerivador.derivar(
                    inv.getEstado(), inv.getPulAbiertos(), inv.getGlassAbiertos(),
                    inv.getNormalAbiertos(), inv.getIdCli(),
                    inv.getEstFecha() != null && inv.getFunFecha() != null,
                    rs.getBoolean("REP_TRAS_REVISION"));
            inv.setEstadoEfectivo(d.estadoEfectivo());
            inv.setUbicacion(d.ubicacion());
            inv.setSubUbicaciones(d.subUbicaciones());
            return inv;
        });
    }

    /** Alta/re-entrada de un teléfono de lote: fija lote, atributos del fichero y ESTADO=RECIBIDO. */
    public void upsertImportacion(String imei, String modelo, Integer idLote, Integer storageGb,
                                  String color, String gradoProveedor, boolean esEsim,
                                  java.math.BigDecimal precioCompra, String divisa,
                                  java.math.BigDecimal precioCompraEur) {
        jdbc.update(
            "INSERT INTO Telefono (IMEI, MODELO, ID_LOTE, ESTADO, STORAGE_GB, COLOR, GRADO_PROVEEDOR, ES_ESIM," +
            "                      PRECIO_COMPRA, DIVISA, PRECIO_COMPRA_EUR)" +
            " VALUES (?, ?, ?, 'RECIBIDO', ?, ?, ?, ?, ?, ?, ?)" +
            " ON DUPLICATE KEY UPDATE MODELO = COALESCE(?, MODELO), ID_LOTE = ?, ESTADO = 'RECIBIDO'," +
            "  STORAGE_GB = ?, COLOR = ?, GRADO_PROVEEDOR = ?, ES_ESIM = ?, PRECIO_COMPRA = ?, DIVISA = ?, PRECIO_COMPRA_EUR = ?",
            imei, modelo, idLote, storageGb, color, gradoProveedor, esEsim, precioCompra, divisa, precioCompraEur,
            modelo, idLote, storageGb, color, gradoProveedor, esEsim, precioCompra, divisa, precioCompraEur);
    }

    public List<VerificacionImei> verificar(List<String> imeis) {
        if (imeis == null || imeis.isEmpty()) return List.of();
        String placeholders = String.join(",", java.util.Collections.nCopies(imeis.size(), "?"));
        String sql =
            "SELECT t.IMEI, t.ESTADO, t.MODELO, COALESCE(w.ABIERTOS,0) AS ABIERTOS" +
            " FROM Telefono t" +
            " LEFT JOIN (SELECT IMEI, COUNT(*) AS ABIERTOS FROM Reparacion" +
            "            WHERE ID_REP LIKE 'A%' AND FECHA_FIN IS NULL GROUP BY IMEI) w ON w.IMEI = t.IMEI" +
            " WHERE t.IMEI IN (" + placeholders + ")";
        return jdbc.query(sql, (rs, row) -> new VerificacionImei(
                rs.getString("IMEI"), true, rs.getString("ESTADO"),
                rs.getInt("ABIERTOS"), rs.getString("MODELO")),
            imeis.toArray());
    }
}
