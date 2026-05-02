package com.reparaciones.servidor.dao;

import com.reparaciones.servidor.model.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Timestamp;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.WeekFields;
import java.util.*;

@Repository
public class ReparacionDAO {

    private final JdbcTemplate jdbc;

    private static final DateTimeFormatter FMT_ID = DateTimeFormatter.ofPattern("yyyyMMdd");

    private static final RowMapper<Reparacion> REP_MAPPER = (rs, row) -> {
        Timestamp fin = rs.getTimestamp("FECHA_FIN");
        return new Reparacion(
                rs.getString("ID_REP"),
                rs.getTimestamp("FECHA_ASIG").toLocalDateTime(),
                fin != null ? fin.toLocalDateTime() : null,
                rs.getString("IMEI"),
                rs.getInt("ID_TEC"),
                rs.getTimestamp("UPDATED_AT").toLocalDateTime()
        );
    };

    private static final String HISTORIAL_SELECT =
            "SELECT r.ID_REP, r.IMEI, t.NOMBRE AS NOMBRE_TEC," +
            " r.FECHA_ASIG, r.FECHA_FIN," +
            " c.TIPO AS TIPO_COM, rc.OBSERVACIONES," +
            " COALESCE(rc.ES_INCIDENCIA, 0) AS ES_INCIDENCIA," +
            " COALESCE(rc.ES_RESUELTO, 0) AS ES_RESUELTO," +
            " rc.INCIDENCIA, r.ID_REP_ANTERIOR, r.ID_TEC," +
            " 0 AS ES_SOLICITUD, NULL AS DESC_SOL," +
            " r.UPDATED_AT" +
            " FROM Reparacion r" +
            " JOIN Tecnico t ON r.ID_TEC = t.ID_TEC" +
            " LEFT JOIN Reparacion_componente rc ON r.ID_REP = rc.ID_REP" +
            " LEFT JOIN Componente c ON rc.ID_COM = c.ID_COM" +
            " WHERE r.ID_REP LIKE 'R%'";

    private static final String ASIGNACION_SELECT =
            "SELECT r.ID_REP, r.IMEI, t.NOMBRE AS NOMBRE_TEC," +
            " r.FECHA_ASIG, r.FECHA_FIN," +
            " NULL AS TIPO_COM, NULL AS OBSERVACIONES," +
            " (CASE WHEN r.ID_REP_ANTERIOR IS NOT NULL THEN 1 ELSE 0 END) AS ES_INCIDENCIA, 0 AS ES_RESUELTO, NULL AS INCIDENCIA," +
            " r.ID_REP_ANTERIOR, r.ID_TEC," +
            " COUNT(rc.ID_RC) AS ES_SOLICITUD, NULL AS DESC_SOL," +
            " r.UPDATED_AT" +
            " FROM Reparacion r" +
            " JOIN Tecnico t ON r.ID_TEC = t.ID_TEC" +
            " LEFT JOIN Reparacion_componente rc ON r.ID_REP = rc.ID_REP AND rc.ES_SOLICITUD = 1" +
            " WHERE r.ID_REP LIKE 'A%' AND r.FECHA_FIN IS NULL";

    private static final RowMapper<ReparacionResumen> RESUMEN_MAPPER = (rs, row) -> {
        Timestamp fin = rs.getTimestamp("FECHA_FIN");
        return new ReparacionResumen(
                rs.getString("ID_REP"),
                rs.getString("IMEI"),
                rs.getString("NOMBRE_TEC"),
                rs.getTimestamp("FECHA_ASIG").toLocalDateTime(),
                fin != null ? fin.toLocalDateTime() : null,
                rs.getString("TIPO_COM"),
                rs.getString("OBSERVACIONES"),
                rs.getBoolean("ES_INCIDENCIA"),
                rs.getBoolean("ES_RESUELTO"),
                rs.getString("INCIDENCIA"),
                rs.getString("ID_REP_ANTERIOR"),
                rs.getInt("ID_TEC"),
                rs.getInt("ES_SOLICITUD"),
                rs.getString("DESC_SOL"),
                rs.getTimestamp("UPDATED_AT").toLocalDateTime()
        );
    };

    public ReparacionDAO(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ── Lectura ───────────────────────────────────────────────────────────────

    public List<Reparacion> getAll() {
        return jdbc.query(
                "SELECT ID_REP, FECHA_ASIG, FECHA_FIN, IMEI, ID_TEC, UPDATED_AT" +
                " FROM Reparacion ORDER BY ID_REP",
                REP_MAPPER);
    }

    public List<Reparacion> getByImei(String imei) {
        return jdbc.query(
                "SELECT ID_REP, FECHA_ASIG, FECHA_FIN, IMEI, ID_TEC, UPDATED_AT" +
                " FROM Reparacion WHERE IMEI = ? ORDER BY ID_REP",
                REP_MAPPER, imei);
    }

    public int countByImei(String imei) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM Reparacion WHERE IMEI = ?", Integer.class, imei);
    }

    private static final String ORDER_HISTORIAL =
            " ORDER BY r.FECHA_ASIG DESC, CAST(SUBSTRING_INDEX(r.ID_REP,'_',-1) AS UNSIGNED) DESC";

    public List<ReparacionResumen> getHistorial(Integer idTecFilter) {
        if (idTecFilter != null) {
            return jdbc.query(HISTORIAL_SELECT + " AND r.ID_TEC = ?" + ORDER_HISTORIAL,
                    RESUMEN_MAPPER, idTecFilter);
        }
        return jdbc.query(HISTORIAL_SELECT + ORDER_HISTORIAL, RESUMEN_MAPPER);
    }

    public List<ReparacionResumen> getHistorialPorImei(String imei) {
        return jdbc.query(HISTORIAL_SELECT + " AND r.IMEI = ?" + ORDER_HISTORIAL,
                RESUMEN_MAPPER, imei);
    }

    public List<ReparacionResumen> getAsignaciones(Integer idTecFilter) {
        String sql = ASIGNACION_SELECT;
        String groupBy = " GROUP BY r.ID_REP, r.IMEI, t.NOMBRE, r.FECHA_ASIG, r.FECHA_FIN," +
                         " r.ID_REP_ANTERIOR, r.ID_TEC, r.UPDATED_AT ORDER BY r.FECHA_ASIG ASC";
        if (idTecFilter != null) {
            return jdbc.query(sql + " AND r.ID_TEC = ?" + groupBy, RESUMEN_MAPPER, idTecFilter);
        }
        return jdbc.query(sql + groupBy, RESUMEN_MAPPER);
    }

    public Optional<ReparacionResumen> getAsignacionById(String idRep) {
        String sql = ASIGNACION_SELECT + " AND r.ID_REP = ?" +
                " GROUP BY r.ID_REP, r.IMEI, t.NOMBRE, r.FECHA_ASIG, r.FECHA_FIN," +
                " r.ID_REP_ANTERIOR, r.ID_TEC, r.UPDATED_AT";
        List<ReparacionResumen> result = jdbc.query(sql, RESUMEN_MAPPER, idRep);
        return result.isEmpty() ? Optional.empty() : Optional.of(result.get(0));
    }

    public record DetalleEdicion(String imei, int idTec, int idCom,
                                  boolean esReutilizado, String observacion, int cantidad,
                                  LocalDateTime updatedAt) {}

    public DetalleEdicion getDetalleEdicion(String idRep) {
        return jdbc.queryForObject(
                "SELECT r.IMEI, r.ID_TEC, rc.ID_COM, rc.ES_REUTILIZADO, rc.OBSERVACIONES, rc.CANTIDAD, rc.UPDATED_AT" +
                " FROM Reparacion r JOIN Reparacion_componente rc ON r.ID_REP = rc.ID_REP" +
                " WHERE r.ID_REP = ?",
                (rs, row) -> new DetalleEdicion(
                        rs.getString("IMEI"),
                        rs.getInt("ID_TEC"),
                        rs.getInt("ID_COM"),
                        rs.getBoolean("ES_REUTILIZADO"),
                        rs.getString("OBSERVACIONES"),
                        rs.getInt("CANTIDAD"),
                        rs.getTimestamp("UPDATED_AT").toLocalDateTime()),
                idRep);
    }

    public String getReferenciadora(String idRep) {
        List<String> result = jdbc.query(
                "SELECT ID_REP FROM Reparacion WHERE ID_REP_ANTERIOR = ?",
                (rs, row) -> rs.getString(1), idRep);
        return result.isEmpty() ? null : result.get(0);
    }

    public Set<Integer> getIdComsYaReparados(String imei, String idRepExcluir) {
        List<Integer> list = jdbc.query(
                "SELECT DISTINCT rc.ID_COM FROM Reparacion r" +
                " JOIN Reparacion_componente rc ON r.ID_REP = rc.ID_REP" +
                " WHERE r.IMEI = ? AND r.ID_REP LIKE 'R%' AND r.ID_REP != ?" +
                " AND rc.ID_COM IS NOT NULL",
                (rs, row) -> rs.getInt(1), imei, idRepExcluir);
        return new HashSet<>(list);
    }

    public String getIncidenciaActivaPorImei(String imei) {
        List<String> result = jdbc.query(
                "SELECT r.ID_REP FROM Reparacion r" +
                " JOIN Reparacion_componente rc ON r.ID_REP = rc.ID_REP" +
                " WHERE r.IMEI = ? AND r.ID_REP LIKE 'R%'" +
                " AND rc.ES_INCIDENCIA = 1 AND rc.ES_RESUELTO = 0 LIMIT 1",
                (rs, row) -> rs.getString(1), imei);
        return result.isEmpty() ? null : result.get(0);
    }

    public boolean existeAsignacionParaTecnico(String imei, int idTec) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM Reparacion WHERE IMEI = ? AND ID_TEC = ?" +
                " AND ID_REP LIKE 'A%' AND FECHA_FIN IS NULL",
                Integer.class, imei, idTec);
        return count != null && count > 0;
    }

    public List<PuntoEstadistica> getEstadisticasPorTecnico(
            String granularidad, LocalDate desde, LocalDate hasta) {
        record Fila(String tecnico, LocalDate fecha) {}
        List<Fila> filas = jdbc.query(
                "SELECT t.NOMBRE, DATE(r.FECHA_FIN) AS FD" +
                " FROM Reparacion r JOIN Tecnico t ON r.ID_TEC = t.ID_TEC" +
                " WHERE r.ID_REP LIKE 'R%' AND r.FECHA_FIN IS NOT NULL" +
                " AND DATE(r.FECHA_FIN) BETWEEN ? AND ?",
                (rs, row) -> new Fila(rs.getString("NOMBRE"),
                        rs.getDate("FD").toLocalDate()),
                desde, hasta);

        Map<String, Map<String, Integer>> mapa = new LinkedHashMap<>();
        for (Fila f : filas) {
            String periodo = formatearPeriodo(inicioPeriodo(f.fecha(), granularidad), granularidad);
            mapa.computeIfAbsent(f.tecnico(), k -> new LinkedHashMap<>())
                .merge(periodo, 1, Integer::sum);
        }
        List<PuntoEstadistica> resultado = new ArrayList<>();
        mapa.forEach((tec, periodos) ->
            periodos.forEach((p, c) -> resultado.add(new PuntoEstadistica(tec, p, c))));
        resultado.sort(Comparator.comparing(PuntoEstadistica::getPeriodo)
                                  .thenComparing(PuntoEstadistica::getNombreTecnico));
        return resultado;
    }

    // ── Escritura ─────────────────────────────────────────────────────────────

    @Transactional
    public String insertar(String imei, int idTec, LocalDateTime fechaAsig, LocalDateTime fechaFin) {
        ensureTelefono(imei);
        String idRep = nextId("R");
        jdbc.update("INSERT INTO Reparacion (ID_REP, IMEI, ID_TEC, FECHA_ASIG, FECHA_FIN) VALUES (?,?,?,?,?)",
                idRep, imei, idTec, fechaAsig, fechaFin);
        return idRep;
    }

    @Transactional
    public String insertarAsignacion(String imei, int idTec) {
        ensureTelefono(imei);
        String idRep = nextId("A");
        jdbc.update("INSERT INTO Reparacion (ID_REP, IMEI, ID_TEC, FECHA_ASIG) VALUES (?,?,?,NOW())",
                idRep, imei, idTec);
        return idRep;
    }

    @Transactional
    public void insertarCompleta(List<FilaReparacion> filas, String imei, int idTec,
                                  String idRepAnterior, String idAsignacion) {
        if (idAsignacion != null) {
            // Bloquear la fila para que un DELETE concurrente (eliminarAsignacion) espere
            Integer existe = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM Reparacion WHERE ID_REP = ? AND FECHA_FIN IS NULL FOR UPDATE",
                    Integer.class, idAsignacion);
            if (existe == null || existe == 0) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "La asignación ya fue eliminada o completada por otro usuario");
            }
        }
        ensureTelefono(imei);
        boolean creoReparacion = false;
        Set<Integer> idComsUsados = new java.util.HashSet<>();
        for (FilaReparacion fila : filas) {
            if (!fila.esSolicitud) {
                String idRep = nextId("R");
                jdbc.update(
                        "INSERT INTO Reparacion (ID_REP, IMEI, ID_TEC, ID_REP_ANTERIOR, FECHA_ASIG, FECHA_FIN)" +
                        " VALUES (?,?,?,?,NOW(),NOW())",
                        idRep, imei, idTec, idRepAnterior);
                jdbc.update(
                        "INSERT INTO Reparacion_componente" +
                        " (ID_REP, ID_COM, ES_REUTILIZADO, OBSERVACIONES, ES_SOLICITUD, CANTIDAD)" +
                        " VALUES (?,?,?,?,0,?)",
                        idRep, fila.idCom, fila.reutilizado, fila.observacion, fila.cantidad);
                if (!fila.reutilizado) {
                    jdbc.update("UPDATE Componente SET STOCK = STOCK - ? WHERE ID_COM = ?",
                            fila.cantidad, fila.idCom);
                }
                creoReparacion = true;
                idComsUsados.add(fila.idCom);
            } else if (idAsignacion != null) {
                jdbc.update(
                        "INSERT INTO Reparacion_componente" +
                        " (ID_REP, ID_COM, ES_SOLICITUD, DESCRIPCION_SOLICITUD, ESTADO_SOLICITUD, CANTIDAD)" +
                        " VALUES (?,?,1,?,'PENDIENTE',?)",
                        idAsignacion, fila.idCom, fila.descripcionSolicitud, fila.cantidad);
            }
        }
        if (idAsignacion != null && creoReparacion) {
            // Solicitudes bloqueantes: PENDIENTE cuyo idCom no fue usado en esta llamada.
            // Solicitudes de componente ya usado (o warnings acompañantes) no bloquean el cierre.
            List<Integer> pendientes = jdbc.query(
                    "SELECT COALESCE(ID_COM, 0) FROM Reparacion_componente" +
                    " WHERE ID_REP = ? AND ES_SOLICITUD = 1 AND ESTADO_SOLICITUD = 'PENDIENTE'",
                    (rs, row) -> rs.getInt(1), idAsignacion);
            long bloqueantes = pendientes.stream()
                    .filter(idCom -> idCom == 0 || !idComsUsados.contains(idCom))
                    .count();
            if (bloqueantes == 0) {
                jdbc.update("UPDATE Reparacion SET FECHA_FIN = NOW() WHERE ID_REP = ?", idAsignacion);
                List<String> prevs = jdbc.query(
                        "SELECT ID_REP_ANTERIOR FROM Reparacion" +
                        " WHERE ID_REP = ? AND ID_REP_ANTERIOR IS NOT NULL",
                        (rs, row) -> rs.getString(1), idAsignacion);
                if (!prevs.isEmpty()) {
                    jdbc.update(
                            "UPDATE Reparacion_componente SET ES_RESUELTO = 1" +
                            " WHERE ID_REP = ? AND ES_INCIDENCIA = 1 AND ES_RESUELTO = 0",
                            prevs.get(0));
                }
            }
        }
    }

    public void completar(String idRep) {
        jdbc.update("UPDATE Reparacion SET FECHA_FIN = NOW() WHERE ID_REP = ?", idRep);
    }

    public void actualizarTecnico(String idRep, int idTec, LocalDateTime updatedAt) {
        LocalDateTime enBd = jdbc.queryForObject(
                "SELECT UPDATED_AT FROM Reparacion WHERE ID_REP = ?",
                (rs, row) -> rs.getTimestamp(1).toLocalDateTime().truncatedTo(ChronoUnit.SECONDS),
                idRep);
        if (!updatedAt.truncatedTo(ChronoUnit.SECONDS).equals(enBd)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Dato modificado por otro usuario");
        }
        jdbc.update("UPDATE Reparacion SET ID_TEC = ? WHERE ID_REP = ?", idTec, idRep);
    }

    @Transactional
    public void editarReparacion(String idRep, int idComNuevo, boolean esReutilizadoNuevo,
                                  String observacionNueva, int nNuevas, LocalDateTime updatedAt) {
        record RcRow(int idCom, boolean esReutilizado, int cantidad, LocalDateTime updatedAt) {}
        RcRow vieja = jdbc.queryForObject(
                "SELECT ID_COM, ES_REUTILIZADO, CANTIDAD, UPDATED_AT" +
                " FROM Reparacion_componente WHERE ID_REP = ?",
                (rs, row) -> new RcRow(rs.getInt("ID_COM"),
                        rs.getBoolean("ES_REUTILIZADO"), rs.getInt("CANTIDAD"),
                        rs.getTimestamp("UPDATED_AT").toLocalDateTime()),
                idRep);
        if (vieja == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Reparación no encontrada: " + idRep);
        }
        if (!updatedAt.truncatedTo(ChronoUnit.SECONDS)
                .equals(vieja.updatedAt().truncatedTo(ChronoUnit.SECONDS))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Dato modificado por otro usuario");
        }
        if (!vieja.esReutilizado()) {
            jdbc.update("UPDATE Componente SET STOCK = STOCK + ? WHERE ID_COM = ?",
                    vieja.cantidad(), vieja.idCom());
        }
        jdbc.update(
                "UPDATE Reparacion_componente SET ID_COM=?, ES_REUTILIZADO=?, OBSERVACIONES=?, CANTIDAD=?" +
                " WHERE ID_REP=?",
                idComNuevo, esReutilizadoNuevo, observacionNueva, nNuevas, idRep);
        if (!esReutilizadoNuevo) {
            jdbc.update("UPDATE Componente SET STOCK = STOCK - ? WHERE ID_COM = ?",
                    nNuevas, idComNuevo);
        }
    }

    @Transactional
    public void marcarIncidenciaYAsignar(String idRep, String comentario, String imei, int idTec) {
        jdbc.update(
                "UPDATE Reparacion_componente SET ES_INCIDENCIA = 1, INCIDENCIA = ? WHERE ID_REP = ?",
                comentario, idRep);
        ensureTelefono(imei);
        String idAsig = nextId("A");
        jdbc.update("INSERT INTO Reparacion (ID_REP, IMEI, ID_TEC, ID_REP_ANTERIOR, FECHA_ASIG) VALUES (?,?,?,?,NOW())",
                idAsig, imei, idTec, idRep);
    }

    @Transactional
    public void borrarIncidenciaPorImei(String imei) {
        jdbc.update(
                "UPDATE Reparacion_componente rc" +
                " JOIN Reparacion r ON rc.ID_REP = r.ID_REP" +
                " SET rc.ES_INCIDENCIA = 0, rc.INCIDENCIA = NULL" +
                " WHERE r.IMEI = ? AND r.ID_REP LIKE 'R%'" +
                " AND rc.ES_INCIDENCIA = 1 AND rc.ES_RESUELTO = 0",
                imei);
        List<String> asigs = jdbc.query(
                "SELECT ID_REP FROM Reparacion WHERE IMEI = ? AND ID_REP LIKE 'A%' AND FECHA_FIN IS NULL",
                (rs, row) -> rs.getString(1), imei);
        for (String idAsig : asigs) {
            jdbc.update("DELETE FROM Reparacion_componente WHERE ID_REP = ?", idAsig);
            jdbc.update("DELETE FROM Reparacion WHERE ID_REP = ?", idAsig);
        }
    }

    @Transactional
    public void eliminarAsignacion(String idAsig) {
        String imei = jdbc.queryForObject(
                "SELECT IMEI FROM Reparacion WHERE ID_REP = ?", String.class, idAsig);
        jdbc.update("DELETE FROM Reparacion_componente WHERE ID_REP = ?", idAsig);
        jdbc.update("DELETE FROM Reparacion WHERE ID_REP = ?", idAsig);
        deleteIfLastReparacion(imei);
    }

    @Transactional
    public void eliminar(String idRep) {
        record RcRow(int idCom, boolean esReutilizado, int cantidad) {}
        List<RcRow> rows = jdbc.query(
                "SELECT ID_COM, ES_REUTILIZADO, CANTIDAD FROM Reparacion_componente WHERE ID_REP = ?",
                (rs, row) -> new RcRow(rs.getInt("ID_COM"),
                        rs.getBoolean("ES_REUTILIZADO"), rs.getInt("CANTIDAD")),
                idRep);
        String imei = jdbc.queryForObject(
                "SELECT IMEI FROM Reparacion WHERE ID_REP = ?", String.class, idRep);

        // Si esta R* resolvía una incidencia y no quedan otras que la resuelvan, revertir
        List<String> prevs = jdbc.query(
                "SELECT ID_REP_ANTERIOR FROM Reparacion WHERE ID_REP = ? AND ID_REP_ANTERIOR IS NOT NULL",
                (rs, row) -> rs.getString(1), idRep);
        if (!prevs.isEmpty()) {
            String idRepOrig = prevs.get(0);
            Integer restantes = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM Reparacion WHERE ID_REP_ANTERIOR = ? AND ID_REP LIKE 'R%' AND ID_REP != ?",
                    Integer.class, idRepOrig, idRep);
            if (restantes != null && restantes == 0) {
                jdbc.update(
                        "UPDATE Reparacion_componente SET ES_RESUELTO = 0" +
                        " WHERE ID_REP = ? AND ES_INCIDENCIA = 1",
                        idRepOrig);
                // Reabrir la A* si existe cerrada
                jdbc.update(
                        "UPDATE Reparacion SET FECHA_FIN = NULL" +
                        " WHERE ID_REP_ANTERIOR = ? AND ID_REP LIKE 'A%' AND FECHA_FIN IS NOT NULL",
                        idRepOrig);
            }
        }

        for (RcRow rc : rows) {
            if (!rc.esReutilizado() && rc.idCom() > 0) {
                jdbc.update("UPDATE Componente SET STOCK = STOCK + ? WHERE ID_COM = ?",
                        rc.cantidad(), rc.idCom());
            }
        }
        jdbc.update("DELETE FROM Reparacion_componente WHERE ID_REP = ?", idRep);
        jdbc.update("DELETE FROM Reparacion WHERE ID_REP = ?", idRep);
        deleteIfLastReparacion(imei);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private String nextId(String prefijo) {
        String hoy  = LocalDate.now().format(FMT_ID);
        String like = prefijo + hoy + "_%";
        // FOR UPDATE serializa lecturas concurrentes dentro de la transacción del caller:
        // dos threads leen el mismo MAX sin esto → mismo ID → PK violation
        Integer n = jdbc.queryForObject(
                "SELECT COALESCE(MAX(CAST(SUBSTRING_INDEX(ID_REP,'_',-1) AS UNSIGNED)),0)+1" +
                " FROM Reparacion WHERE ID_REP LIKE ? FOR UPDATE",
                Integer.class, like);
        return prefijo + hoy + "_" + (n != null ? n : 1);
    }

    private void ensureTelefono(String imei) {
        jdbc.update("INSERT IGNORE INTO Telefono (IMEI) VALUES (?)", imei);
    }

    private void deleteIfLastReparacion(String imei) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM Reparacion WHERE IMEI = ?", Integer.class, imei);
        if (count != null && count == 0) {
            jdbc.update("DELETE FROM Telefono WHERE IMEI = ?", imei);
        }
    }

    private LocalDate inicioPeriodo(LocalDate fecha, String g) {
        return switch (g) {
            case "dia"    -> fecha;
            case "semana" -> fecha.with(DayOfWeek.MONDAY);
            case "mes"    -> fecha.withDayOfMonth(1);
            case "ano"    -> fecha.withDayOfYear(1);
            default       -> fecha.withDayOfMonth(1);
        };
    }

    private String formatearPeriodo(LocalDate fecha, String g) {
        return switch (g) {
            case "dia"    -> fecha.toString();
            case "semana" -> fecha.getYear() + "-W"
                    + String.format("%02d", fecha.get(WeekFields.ISO.weekOfWeekBasedYear()));
            case "mes"    -> fecha.getYear() + "-" + String.format("%02d", fecha.getMonthValue());
            case "ano"    -> String.valueOf(fecha.getYear());
            default       -> fecha.toString();
        };
    }
}
