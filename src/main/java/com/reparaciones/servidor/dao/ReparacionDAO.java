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
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.WeekFields;
import java.util.*;

@Repository
public class ReparacionDAO {

    private final JdbcTemplate jdbc;
    private final BorradorDAO  borradorDao;
    private final MovimientoDAO movimientoDao;

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
            " COALESCE(rc.ES_REUTILIZADO, 0) AS ES_REUTILIZADO," +
            " rc.INCIDENCIA, r.ID_REP_ANTERIOR, r.ID_TEC," +
            " 0 AS ES_SOLICITUD, NULL AS DESC_SOL," +
            " NULL AS ESTADO_SOL, NULL AS TIPO_SOL, 0 AS STOCK_SOL, 0 AS EN_CAMINO_SOL, NULL AS TIPOS_SOL," +
            " r.UPDATED_AT, tel.MODELO, NULL AS COMENTARIO_ASIGNACION," +
            " tel.OBSERVACION AS OBSERVACION_TELEFONO," +
            " tel.UPDATED_AT AS TELEFONO_UPDATED_AT," +
            " ta.NOMBRE AS NOMBRE_TEC_ASIGNA," +
            " (SELECT COUNT(*) FROM Reparacion r2" +
            "  WHERE r2.IMEI = r.IMEI AND r2.ID_REP LIKE 'A%'" +
            "  AND r2.FECHA_FIN IS NULL) AS TIENE_ASIGNACIONES," +
            " cli.NOMBRE AS CLIENTE" +
            " FROM Reparacion r" +
            " JOIN Tecnico t ON r.ID_TEC = t.ID_TEC" +
            " LEFT JOIN Tecnico ta ON r.ID_TEC_ASIGNA = ta.ID_TEC" +
            " LEFT JOIN Reparacion_componente rc ON r.ID_REP = rc.ID_REP" +
            " LEFT JOIN Componente c ON rc.ID_COM = c.ID_COM" +
            " LEFT JOIN Telefono tel ON r.IMEI = tel.IMEI" +
            " LEFT JOIN Cliente cli ON tel.ID_CLI = cli.ID_CLI" +
            " WHERE r.ID_REP LIKE 'R%'";

    private static final String ASIGNACION_SELECT =
            "SELECT r.ID_REP, r.IMEI, t.NOMBRE AS NOMBRE_TEC," +
            " r.FECHA_ASIG, r.FECHA_FIN," +
            " NULL AS TIPO_COM, NULL AS OBSERVACIONES," +
            " (CASE WHEN r.ID_REP_ANTERIOR IS NOT NULL THEN 1 ELSE 0 END) AS ES_INCIDENCIA, 0 AS ES_RESUELTO, 0 AS ES_REUTILIZADO, NULL AS INCIDENCIA," +
            " r.ID_REP_ANTERIOR, r.ID_TEC," +
            " COUNT(rc.ID_RC) AS ES_SOLICITUD, NULL AS DESC_SOL," +
            " (SELECT rc2.ESTADO_SOLICITUD FROM Reparacion_componente rc2" +
            "  WHERE rc2.ID_REP = r.ID_REP AND rc2.ES_SOLICITUD = 1" +
            "  ORDER BY CASE rc2.ESTADO_SOLICITUD WHEN 'PENDIENTE' THEN 0 WHEN 'RECHAZADA' THEN 1 ELSE 2 END LIMIT 1) AS ESTADO_SOL," +
            " (SELECT c2.TIPO FROM Reparacion_componente rc2" +
            "  JOIN Componente c2 ON rc2.ID_COM = c2.ID_COM" +
            "  WHERE rc2.ID_REP = r.ID_REP AND rc2.ES_SOLICITUD = 1" +
            "  ORDER BY CASE rc2.ESTADO_SOLICITUD WHEN 'PENDIENTE' THEN 0 WHEN 'RECHAZADA' THEN 1 ELSE 2 END LIMIT 1) AS TIPO_SOL," +
            " (SELECT COALESCE(master2.STOCK, c2.STOCK)" +
            "  FROM Reparacion_componente rc2" +
            "  JOIN Componente c2 ON rc2.ID_COM = c2.ID_COM" +
            "  LEFT JOIN Componente master2 ON c2.ID_COM_MASTER = master2.ID_COM" +
            "  WHERE rc2.ID_REP = r.ID_REP AND rc2.ES_SOLICITUD = 1" +
            "  ORDER BY CASE rc2.ESTADO_SOLICITUD WHEN 'PENDIENTE' THEN 0 WHEN 'RECHAZADA' THEN 1 ELSE 2 END LIMIT 1) AS STOCK_SOL," +
            " (SELECT CASE WHEN COUNT(*) > 0 THEN 1 ELSE 0 END FROM Compra_componente cc" +
            "  WHERE cc.ESTADO = 'en_camino' AND cc.ID_COM IN (" +
            "   SELECT rc2.ID_COM FROM Reparacion_componente rc2" +
            "   WHERE rc2.ID_REP = r.ID_REP AND rc2.ES_SOLICITUD = 1 AND rc2.ESTADO_SOLICITUD != 'RECHAZADA')) AS EN_CAMINO_SOL," +
            " (SELECT GROUP_CONCAT(c2.TIPO ORDER BY c2.TIPO SEPARATOR ', ')" +
            "  FROM Reparacion_componente rc2 JOIN Componente c2 ON rc2.ID_COM = c2.ID_COM" +
            "  WHERE rc2.ID_REP = r.ID_REP AND rc2.ES_SOLICITUD = 1 AND rc2.ESTADO_SOLICITUD != 'RECHAZADA') AS TIPOS_SOL," +
            " r.UPDATED_AT, tel.MODELO, r.COMENTARIO_ASIGNACION," +
            " tel.OBSERVACION AS OBSERVACION_TELEFONO, tel.UPDATED_AT AS TELEFONO_UPDATED_AT, r.URGENTE, r.ES_CHASIS, r.POR_CERRAR," +
            // Entrega a glass, derivada de la AG abierta más antigua del mismo IMEI (spec 2026-08-28 §4)
            " (SELECT COUNT(*) FROM Reparacion g" +
            "  WHERE g.IMEI = r.IMEI AND g.ID_REP LIKE 'AG%' AND g.FECHA_FIN IS NULL) AS GLASS_ABIERTAS," +
            " (SELECT g.ENTREGADO_AT FROM Reparacion g" +
            "  WHERE g.IMEI = r.IMEI AND g.ID_REP LIKE 'AG%' AND g.FECHA_FIN IS NULL" +
            "  ORDER BY g.FECHA_ASIG ASC LIMIT 1) AS GLASS_ENTREGADO_AT," +
            " (SELECT tg.NOMBRE FROM Reparacion g LEFT JOIN Tecnico tg ON g.ENTREGADO_POR = tg.ID_TEC" +
            "  WHERE g.IMEI = r.IMEI AND g.ID_REP LIKE 'AG%' AND g.FECHA_FIN IS NULL" +
            "  ORDER BY g.FECHA_ASIG ASC LIMIT 1) AS GLASS_ENTREGADO_POR_NOMBRE," +
            " (SELECT tg.NOMBRE FROM Reparacion g JOIN Tecnico tg ON g.ID_TEC = tg.ID_TEC" +
            "  WHERE g.IMEI = r.IMEI AND g.ID_REP LIKE 'AG%' AND g.FECHA_FIN IS NULL" +
            "  ORDER BY g.FECHA_ASIG ASC LIMIT 1) AS GLASS_TECNICO_NOMBRE," +
            " ta.NOMBRE AS NOMBRE_TEC_ASIGNA," +
            " cli.NOMBRE AS CLIENTE" +
            " FROM Reparacion r" +
            " JOIN Tecnico t ON r.ID_TEC = t.ID_TEC" +
            " LEFT JOIN Tecnico ta ON r.ID_TEC_ASIGNA = ta.ID_TEC" +
            " LEFT JOIN Reparacion_componente rc ON r.ID_REP = rc.ID_REP AND rc.ES_SOLICITUD = 1 AND rc.ESTADO_SOLICITUD != 'RECHAZADA'" +
            " LEFT JOIN Telefono tel ON r.IMEI = tel.IMEI" +
            " LEFT JOIN Cliente cli ON tel.ID_CLI = cli.ID_CLI" +
            " WHERE r.ID_REP LIKE 'A%' AND r.ID_REP NOT LIKE 'AP%' AND r.ID_REP NOT LIKE 'AG%' AND r.FECHA_FIN IS NULL";

    private static final RowMapper<ReparacionResumen> RESUMEN_MAPPER = (rs, row) -> {
        Timestamp fin = rs.getTimestamp("FECHA_FIN");
        ReparacionResumen rr = new ReparacionResumen(
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
                rs.getString("ESTADO_SOL"),
                rs.getString("TIPO_SOL"),
                rs.getInt("STOCK_SOL"),
                rs.getBoolean("EN_CAMINO_SOL"),
                rs.getString("TIPOS_SOL"),
                rs.getTimestamp("UPDATED_AT").toLocalDateTime(),
                rs.getString("MODELO")
        );
        rr.setComentarioAsignacion(rs.getString("COMENTARIO_ASIGNACION"));
        rr.setEsReutilizado(rs.getBoolean("ES_REUTILIZADO"));
        rr.setObservacionTelefono(rs.getString("OBSERVACION_TELEFONO"));
        java.sql.Timestamp tuAt = rs.getTimestamp("TELEFONO_UPDATED_AT");
        rr.setTelefonoUpdatedAt(tuAt != null ? tuAt.toLocalDateTime() : null);
        try { rr.setUrgente(rs.getBoolean("URGENTE")); } catch (Exception ignored) {}
        try { rr.setEsChasis(rs.getBoolean("ES_CHASIS")); } catch (Exception ignored) {}
        try { rr.setPorCerrar(rs.getBoolean("POR_CERRAR")); } catch (Exception ignored) {}
        try { rr.setTieneAsignaciones(rs.getInt("TIENE_ASIGNACIONES") > 0); } catch (Exception ignored) {}
        try { rr.setNombreTecnicoAsigna(rs.getString("NOMBRE_TEC_ASIGNA")); } catch (Exception ignored) {}
        // Entrega a glass: AG lleva las reales, A las derivadas; el resto de queries no las traen.
        try { Timestamp e = rs.getTimestamp("ENTREGADO_AT"); rr.setEntregadoAt(e != null ? e.toLocalDateTime() : null); } catch (Exception ignored) {}
        try { rr.setEntregadoPorNombre(rs.getString("ENTREGADO_POR_NOMBRE")); } catch (Exception ignored) {}
        try { rr.setGlassAbierta(rs.getInt("GLASS_ABIERTAS") > 0); } catch (Exception ignored) {}
        try { Timestamp ge = rs.getTimestamp("GLASS_ENTREGADO_AT"); rr.setGlassEntregadoAt(ge != null ? ge.toLocalDateTime() : null); } catch (Exception ignored) {}
        try { rr.setGlassEntregadoPorNombre(rs.getString("GLASS_ENTREGADO_POR_NOMBRE")); } catch (Exception ignored) {}
        try { rr.setGlassTecnicoNombre(rs.getString("GLASS_TECNICO_NOMBRE")); } catch (Exception ignored) {}
        rr.setCliente(rs.getString("CLIENTE"));
        return rr;
    };

    public ReparacionDAO(JdbcTemplate jdbc, BorradorDAO borradorDao, MovimientoDAO movimientoDao) {
        this.jdbc        = jdbc;
        this.borradorDao = borradorDao;
        this.movimientoDao = movimientoDao;
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
                         " r.ID_REP_ANTERIOR, r.ID_TEC, r.UPDATED_AT, tel.MODELO, r.COMENTARIO_ASIGNACION, tel.OBSERVACION, tel.UPDATED_AT, r.URGENTE, r.ES_CHASIS, r.POR_CERRAR, ta.NOMBRE, cli.NOMBRE ORDER BY r.FECHA_ASIG ASC";
        if (idTecFilter != null) {
            return jdbc.query(sql + " AND r.ID_TEC = ?" + groupBy, RESUMEN_MAPPER, idTecFilter);
        }
        return jdbc.query(sql + groupBy, RESUMEN_MAPPER);
    }

    /** Asignaciones (A/AG) COMPLETADAS desde el corte dado (inicio de hoy en Madrid):
     *  la unidad de "hecho hoy" de la carga por capacidad — conservan ES_CHASIS,
     *  POR_CERRAR y cliente, cosa que las filas R% no hacen. Incluye tanto reparación (A%)
     *  como glass (AG%), ya que un técnico solo-glass debe poder cerrar su "hecho hoy". */
    public List<ReparacionResumen> getAsignacionesCompletadasHoy(java.sql.Timestamp cutoff) {
        String sql = ASIGNACION_SELECT.replace("r.FECHA_FIN IS NULL", "r.FECHA_FIN >= ?");
        if (!sql.contains("r.FECHA_FIN >= ?"))
            throw new IllegalStateException("ASIGNACION_SELECT cambió: revisar getAsignacionesCompletadasHoy");
        String groupBy = " GROUP BY r.ID_REP, r.IMEI, t.NOMBRE, r.FECHA_ASIG, r.FECHA_FIN," +
                         " r.ID_REP_ANTERIOR, r.ID_TEC, r.UPDATED_AT, tel.MODELO, r.COMENTARIO_ASIGNACION, tel.OBSERVACION, tel.UPDATED_AT, r.URGENTE, r.ES_CHASIS, r.POR_CERRAR, ta.NOMBRE, cli.NOMBRE" +
                         " ORDER BY r.FECHA_FIN ASC";
        List<ReparacionResumen> result = new ArrayList<>(jdbc.query(sql + groupBy, RESUMEN_MAPPER, cutoff));

        String sqlGlass = GLASS_ASIGNACION_SELECT.replace("r.FECHA_FIN IS NULL", "r.FECHA_FIN >= ?");
        if (!sqlGlass.contains("r.FECHA_FIN >= ?"))
            throw new IllegalStateException("GLASS_ASIGNACION_SELECT cambió: revisar getAsignacionesCompletadasHoy");
        String groupByGlass = " GROUP BY r.ID_REP, r.IMEI, t.NOMBRE, r.FECHA_ASIG, r.FECHA_FIN," +
                         " r.ID_REP_ANTERIOR, r.ID_TEC, r.UPDATED_AT, tel.MODELO, r.COMENTARIO_ASIGNACION, tel.OBSERVACION, tel.UPDATED_AT, r.URGENTE, r.ES_CHASIS, r.ENTREGADO_AT, r.ENTREGADO_POR, ta.NOMBRE, cli.NOMBRE" +
                         " ORDER BY r.FECHA_FIN ASC";
        result.addAll(jdbc.query(sqlGlass + groupByGlass, RESUMEN_MAPPER, cutoff));

        return result;
    }

    public Optional<ReparacionResumen> getAsignacionById(String idRep) {
        String sql = ASIGNACION_SELECT + " AND r.ID_REP = ?" +
                " GROUP BY r.ID_REP, r.IMEI, t.NOMBRE, r.FECHA_ASIG, r.FECHA_FIN," +
                " r.ID_REP_ANTERIOR, r.ID_TEC, r.UPDATED_AT, tel.MODELO, r.COMENTARIO_ASIGNACION, tel.OBSERVACION, tel.UPDATED_AT, r.URGENTE, r.ES_CHASIS, r.POR_CERRAR, ta.NOMBRE, cli.NOMBRE";
        List<ReparacionResumen> result = jdbc.query(sql, RESUMEN_MAPPER, idRep);
        return result.isEmpty() ? Optional.empty() : Optional.of(result.get(0));
    }

    public List<ReparacionResumen> getAsignacionesPorImei(String imei) {
        String groupBy = " GROUP BY r.ID_REP, r.IMEI, t.NOMBRE, r.FECHA_ASIG, r.FECHA_FIN," +
                         " r.ID_REP_ANTERIOR, r.ID_TEC, r.UPDATED_AT, tel.MODELO, r.COMENTARIO_ASIGNACION, tel.OBSERVACION, tel.UPDATED_AT, r.URGENTE, r.ES_CHASIS, r.POR_CERRAR, ta.NOMBRE, cli.NOMBRE" +
                         " ORDER BY r.FECHA_ASIG ASC";
        return jdbc.query(ASIGNACION_SELECT + " AND r.IMEI = ?" + groupBy, RESUMEN_MAPPER, imei);
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

    /** IMEI del dispositivo de una reparación/asignación/pulido por su ID_REP, o {@code null} si no existe.
     *  Reparaciones y pulidos comparten la tabla {@code Reparacion}. Usado para enriquecer logs. */
    public String getImeiByIdRep(String idRep) {
        List<String> result = jdbc.query(
                "SELECT IMEI FROM Reparacion WHERE ID_REP = ?",
                (rs, row) -> rs.getString(1), idRep);
        return result.isEmpty() ? null : result.get(0);
    }

    public Set<Integer> getIdComsYaReparados(String imei, String idRepExcluir) {
        // Categoría derivada de la reparación en edición: G% para glass, R% para el resto
        // (antes estaba cableado a R% y las ediciones de glass no marcaban "ya reparado").
        String like = (idRepExcluir != null && idRepExcluir.startsWith("G")) ? "G%" : "R%";
        List<Integer> list = jdbc.query(
                "SELECT DISTINCT rc.ID_COM FROM Reparacion r" +
                " JOIN Reparacion_componente rc ON r.ID_REP = rc.ID_REP" +
                " WHERE r.IMEI = ? AND r.ID_REP LIKE ? AND r.ID_REP != ?" +
                " AND rc.ID_COM IS NOT NULL",
                (rs, row) -> rs.getInt(1), imei, like, idRepExcluir);
        return new HashSet<>(list);
    }

    /** Descripciones de las acciones "otro" ya guardadas del IMEI en la categoría (R/G),
     *  excluyendo una reparación (la que se edita). Se precargan bloqueadas al editar. */
    public List<String> getAccionesOtro(String imei, String categoria, String excluir) {
        String like = "G".equals(categoria) ? "G%" : "R%";
        return jdbc.query(
                "SELECT rc.OBSERVACIONES FROM Reparacion r" +
                " JOIN Reparacion_componente rc ON r.ID_REP = rc.ID_REP" +
                " JOIN Componente c ON rc.ID_COM = c.ID_COM" +
                " WHERE r.IMEI = ? AND r.ID_REP LIKE ? AND r.ID_REP != ?" +
                " AND c.TIPO LIKE 'otro%' AND rc.OBSERVACIONES IS NOT NULL" +
                " ORDER BY r.FECHA_FIN",
                (rs, row) -> rs.getString(1), imei, like, excluir != null ? excluir : "");
    }

    /** Incidencia abierta del IMEI por categoría ("G" = glass, resto = reparación);
     *  las incidencias de glass y reparación son independientes. */
    public String getIncidenciaActivaPorImei(String imei, String categoria) {
        String like = "G".equals(categoria) ? "G%" : "R%";
        List<String> result = jdbc.query(
                "SELECT r.ID_REP FROM Reparacion r" +
                " JOIN Reparacion_componente rc ON r.ID_REP = rc.ID_REP" +
                " WHERE r.IMEI = ? AND r.ID_REP LIKE ?" +
                " AND rc.ES_INCIDENCIA = 1 AND rc.ES_RESUELTO = 0 LIMIT 1",
                (rs, row) -> rs.getString(1), imei, like);
        return result.isEmpty() ? null : result.get(0);
    }

    /** ¿El IMEI ya está asignado al técnico en esa categoría? La validación es por
     *  categoría: el mismo IMEI puede asignarse al mismo técnico una vez en cada
     *  categoría (reparación + glass + pulido), pero no dos veces en la misma.
     *  @param categoria "R" = reparación (A, no AP/AG), "G" = glass (AG), "P" = pulido (AP). */
    public boolean existeAsignacionParaTecnico(String imei, int idTec, String categoria) {
        String filtro = switch (categoria) {
            case "G" -> " AND ID_REP LIKE 'AG%'";
            case "P" -> " AND ID_REP LIKE 'AP%'";
            default  -> " AND ID_REP LIKE 'A%' AND ID_REP NOT LIKE 'AP%' AND ID_REP NOT LIKE 'AG%'";
        };
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM Reparacion WHERE IMEI = ? AND ID_TEC = ?" +
                filtro + " AND FECHA_FIN IS NULL",
                Integer.class, imei, idTec);
        return count != null && count > 0;
    }

    public List<Integer> getTecnicosConAsignacionActiva(String imei) {
        return jdbc.queryForList(
                "SELECT ID_TEC FROM Reparacion WHERE IMEI = ? AND ID_REP LIKE 'A%' AND FECHA_FIN IS NULL",
                Integer.class, imei);
    }

    /** Asignación activa mínima para el aviso de asignación cruzada. */
    public record AsignacionActiva(String idRep, String nombreTecnico, int idTec) {}

    /** Asignaciones activas de un IMEI en las 3 categorías (A=rep, AG=glass, AP=pulido);
     *  la categoría la deriva el cliente del prefijo de idRep. */
    public List<AsignacionActiva> getAsignacionesActivasPorImei(String imei) {
        return jdbc.query(
                "SELECT r.ID_REP, t.NOMBRE, r.ID_TEC FROM Reparacion r" +
                " JOIN Tecnico t ON r.ID_TEC = t.ID_TEC" +
                " WHERE r.IMEI = ? AND r.ID_REP LIKE 'A%' AND r.FECHA_FIN IS NULL",
                (rs, row) -> new AsignacionActiva(rs.getString("ID_REP"), rs.getString("NOMBRE"), rs.getInt("ID_TEC")),
                imei);
    }

    public List<PuntoEstadistica> getEstadisticasPorTecnico(
            String granularidad, LocalDate desde, LocalDate hasta) {
        record Fila(String tecnico, LocalDate fecha) {}
        List<Fila> filas = jdbc.query(
                "SELECT t.NOMBRE, DATE(r.FECHA_FIN) AS FD" +
                " FROM Reparacion r JOIN Tecnico t ON r.ID_TEC = t.ID_TEC" +
                " WHERE (r.ID_REP LIKE 'R%' OR r.ID_REP LIKE 'G%') AND r.FECHA_FIN IS NOT NULL" +
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
    public String insertarAsignacion(String imei, int idTec, String comentario, boolean urgente, boolean esChasis, Integer idTecAsigna, int idUsu) {
        ensureTelefono(imei);
        boolean urgenteFinal = urgente || tieneAbiertaUrgente(imei);
        String idRep = nextId("A");
        jdbc.update("INSERT INTO Reparacion (ID_REP, IMEI, ID_TEC, FECHA_ASIG, COMENTARIO_ASIGNACION, ID_TEC_ASIGNA, URGENTE, ES_CHASIS) VALUES (?,?,?,NOW(),?,?,?,?)",
                idRep, imei, idTec, (comentario != null && !comentario.isBlank()) ? comentario : null, idTecAsigna, urgenteFinal, esChasis);
        resetRevisionAlAsignar(imei, idUsu);
        if (urgenteFinal) propagarUrgente(imei, true);
        return idRep;
    }

    /** Alta de asignación de glass (prefijo AG). Espejo de {@link #insertarAsignacion}. */
    @Transactional
    public String insertarAsignacionGlass(String imei, int idTec, String comentario, boolean urgente, Integer idTecAsigna, int idUsu) {
        ensureTelefono(imei);
        boolean urgenteFinal = urgente || tieneAbiertaUrgente(imei);
        String idRep = nextId("AG");
        jdbc.update("INSERT INTO Reparacion (ID_REP, IMEI, ID_TEC, FECHA_ASIG, COMENTARIO_ASIGNACION, ID_TEC_ASIGNA, URGENTE) VALUES (?,?,?,NOW(),?,?,?)",
                idRep, imei, idTec, (comentario != null && !comentario.isBlank()) ? comentario : null, idTecAsigna, urgenteFinal);
        resetRevisionAlAsignar(imei, idUsu);
        if (urgenteFinal) propagarUrgente(imei, true);
        return idRep;
    }

    @Transactional
    public void insertarCompleta(List<FilaReparacion> filas, String imei, int idTec,
                                  String idRepAnterior, String idAsignacion) {
        insertarCompleta(filas, imei, idTec, idRepAnterior, idAsignacion, null);
    }

    /**
     * Variante con categoría explícita ("G"/"R"). Con asignación manda su prefijo (AG→G);
     * sin asignación (p.ej. altas desde la edición de un glass) decide {@code categoria}.
     */
    @Transactional
    public void insertarCompleta(List<FilaReparacion> filas, String imei, int idTec,
                                  String idRepAnterior, String idAsignacion, String categoria) {
        if (idAsignacion != null) {
            // Bloquear la fila para que un DELETE concurrente (eliminarAsignacion) espere
            Integer existe = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM Reparacion WHERE ID_REP = ? AND ID_TEC = ? AND FECHA_FIN IS NULL FOR UPDATE",
                    Integer.class, idAsignacion, idTec);
            if (existe == null || existe == 0) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "La asignación ya fue eliminada, completada o reasignada a otro técnico");
            }
        }
        ensureTelefono(imei);
        Integer idTecAsigna = null;
        if (idAsignacion != null) {
            idTecAsigna = jdbc.queryForObject(
                    "SELECT ID_TEC_ASIGNA FROM Reparacion WHERE ID_REP = ?", Integer.class, idAsignacion);
        }
        boolean creoReparacion = false;
        Set<Integer> idComsUsados = new java.util.HashSet<>();
        for (FilaReparacion fila : filas) {
            if (!fila.esSolicitud) {
                String idRep = nextId(idAsignacion == null && "G".equals(categoria)
                        ? "G" : resultPrefix(idAsignacion));
                jdbc.update(
                        "INSERT INTO Reparacion (ID_REP, IMEI, ID_TEC, ID_REP_ANTERIOR, FECHA_ASIG, FECHA_FIN, ID_TEC_ASIGNA)" +
                        " VALUES (?,?,?,?,NOW(),NOW(),?)",
                        idRep, imei, idTec, idRepAnterior, idTecAsigna);
                jdbc.update(
                        "INSERT INTO Reparacion_componente" +
                        " (ID_REP, ID_COM, ES_REUTILIZADO, OBSERVACIONES, ES_SOLICITUD, CANTIDAD)" +
                        " VALUES (?,?,?,?,0,?)",
                        idRep, fila.idCom, fila.reutilizado, fila.observacion, fila.cantidad);
                if (!fila.reutilizado) {
                    int masterIdCom = resolveToMasterId(fila.idCom);
                    int rows = jdbc.update(
                            "UPDATE Componente SET STOCK = STOCK - ? WHERE ID_COM = ? AND STOCK >= ?",
                            fila.cantidad, masterIdCom, fila.cantidad);
                    if (rows == 0)
                        throw new ResponseStatusException(HttpStatus.CONFLICT,
                                "Stock insuficiente para el componente ID " + fila.idCom);
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
        if (idAsignacion != null) {
            // Resolver las solicitudes PENDIENTE cuya pieza se ha reparado en esta entrega.
            // Solo tiene sentido si se insertó al menos una reparación nueva en esta llamada.
            if (creoReparacion && !idComsUsados.isEmpty()) {
                StringBuilder inClause = new StringBuilder();
                for (Integer idCom : idComsUsados) {
                    if (inClause.length() > 0) inClause.append(',');
                    inClause.append(idCom);
                }
                jdbc.update(
                        "UPDATE Reparacion_componente SET ES_SOLICITUD = 0" +
                        " WHERE ID_REP = ? AND ES_SOLICITUD = 1 AND ESTADO_SOLICITUD = 'PENDIENTE'" +
                        " AND ID_COM IN (" + inClause + ")",
                        idAsignacion);
            }
            // Comprobar si queda alguna solicitud bloqueante y cerrar la asignación si no.
            // Esto debe ejecutar siempre (incluso con lista vacía: todas las filas ya se
            // guardaron individualmente y el técnico pulsa "Terminar asignación").
            Integer bloqueantes = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM Reparacion_componente" +
                    " WHERE ID_REP = ? AND ES_SOLICITUD = 1 AND ESTADO_SOLICITUD = 'PENDIENTE'",
                    Integer.class, idAsignacion);
            if (bloqueantes != null && bloqueantes == 0) {
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

    @Transactional
    public String guardarFilaIndividual(List<FilaReparacion> filas, String imei, int idTec,
                                        String idRepAnterior, String idAsignacion) {
        Integer existe = jdbc.queryForObject(
                "SELECT COUNT(*) FROM Reparacion WHERE ID_REP = ? AND ID_TEC = ? AND FECHA_FIN IS NULL FOR UPDATE",
                Integer.class, idAsignacion, idTec);
        if (existe == null || existe == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "La asignación ya fue eliminada, completada o reasignada a otro técnico");
        }
        ensureTelefono(imei);
        Integer idTecAsigna = jdbc.queryForObject(
                "SELECT ID_TEC_ASIGNA FROM Reparacion WHERE ID_REP = ?", Integer.class, idAsignacion);
        String idRepCreado = null;
        Set<Integer> idComsUsados = new java.util.HashSet<>();
        for (FilaReparacion fila : filas) {
            if (!fila.esSolicitud) {
                String idRep = nextId(resultPrefix(idAsignacion));
                jdbc.update(
                        "INSERT INTO Reparacion (ID_REP, IMEI, ID_TEC, ID_REP_ANTERIOR, FECHA_ASIG, FECHA_FIN, ID_TEC_ASIGNA)" +
                        " VALUES (?,?,?,?,NOW(),NOW(),?)",
                        idRep, imei, idTec, idRepAnterior, idTecAsigna);
                jdbc.update(
                        "INSERT INTO Reparacion_componente" +
                        " (ID_REP, ID_COM, ES_REUTILIZADO, OBSERVACIONES, ES_SOLICITUD, CANTIDAD)" +
                        " VALUES (?,?,?,?,0,?)",
                        idRep, fila.idCom, fila.reutilizado, fila.observacion, fila.cantidad);
                if (!fila.reutilizado) {
                    int masterIdCom = resolveToMasterId(fila.idCom);
                    int rows = jdbc.update(
                            "UPDATE Componente SET STOCK = STOCK - ? WHERE ID_COM = ? AND STOCK >= ?",
                            fila.cantidad, masterIdCom, fila.cantidad);
                    if (rows == 0)
                        throw new ResponseStatusException(HttpStatus.CONFLICT,
                                "Stock insuficiente para el componente ID " + fila.idCom);
                }
                idRepCreado = idRep;
                idComsUsados.add(fila.idCom);
            } else {
                jdbc.update(
                        "INSERT INTO Reparacion_componente" +
                        " (ID_REP, ID_COM, ES_SOLICITUD, DESCRIPCION_SOLICITUD, ESTADO_SOLICITUD, CANTIDAD)" +
                        " VALUES (?,?,1,?,'PENDIENTE',?)",
                        idAsignacion, fila.idCom, fila.descripcionSolicitud, fila.cantidad);
            }
        }
        if (idRepCreado == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "guardarFilaIndividual requiere al menos una fila con uso real");
        }
        if (!idComsUsados.isEmpty()) {
            StringBuilder inClause = new StringBuilder();
            for (Integer idCom : idComsUsados) {
                if (inClause.length() > 0) inClause.append(',');
                inClause.append(idCom);
            }
            jdbc.update(
                    "UPDATE Reparacion_componente SET ES_SOLICITUD = 0" +
                    " WHERE ID_REP = ? AND ES_SOLICITUD = 1 AND ESTADO_SOLICITUD = 'PENDIENTE'" +
                    " AND ID_COM IN (" + inClause + ")",
                    idAsignacion);
        }
        return idRepCreado;
    }

    public void completar(String idRep) {
        int filas = jdbc.update(
                "UPDATE Reparacion SET FECHA_FIN = NOW() WHERE ID_REP = ? AND FECHA_FIN IS NULL", idRep);
        if (filas == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "La asignación ya fue eliminada o completada por otro usuario");
        }
    }

    @Transactional
    public void actualizarAsignacion(String idRep, int idTec, String comentarioAsignacion,
                                     LocalDateTime updatedAt, Integer idTecAsigna) {
        Integer idTecActual = jdbc.queryForObject(
                "SELECT ID_TEC FROM Reparacion WHERE ID_REP = ?", Integer.class, idRep);
        int filas = jdbc.update(
                "UPDATE Reparacion SET ID_TEC = ?, COMENTARIO_ASIGNACION = ? WHERE ID_REP = ? AND UPDATED_AT = ?",
                idTec, comentarioAsignacion, idRep,
                Timestamp.valueOf(updatedAt.truncatedTo(ChronoUnit.SECONDS)));
        if (filas == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Dato modificado por otro usuario");
        }
        if (idTecActual != null && idTecActual != idTec) {
            jdbc.update("UPDATE Reparacion SET ID_TEC_ASIGNA = ? WHERE ID_REP = ?", idTecAsigna, idRep);
            borradorDao.eliminar(idRep);   // reasignada a otro técnico: el borrador del técnico anterior ya no aplica
        }
    }

    /** ¿Tiene el IMEI alguna asignación abierta marcada urgente? */
    boolean tieneAbiertaUrgente(String imei) {
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM Reparacion WHERE IMEI = ? AND FECHA_FIN IS NULL AND URGENTE = TRUE",
                Integer.class, imei);
        return n != null && n > 0;
    }

    /** Fija URGENTE en TODAS las asignaciones abiertas del IMEI (invariante: un estado por teléfono). */
    int propagarUrgente(String imei, boolean urgente) {
        return jdbc.update(
                "UPDATE Reparacion SET URGENTE = ?, UPDATED_AT = UPDATED_AT WHERE IMEI = ? AND FECHA_FIN IS NULL",
                urgente, imei);
    }

    /** Marca URGENTE en la asignación dada y propaga el mismo estado a todas las demás
     *  asignaciones abiertas del mismo IMEI (invariante: un estado de urgencia por
     *  teléfono, no por fila). */
    @Transactional
    public void actualizarUrgente(String idRep, boolean urgente) {
        String imei = getImeiByIdRep(idRep);
        if (imei == null) return;   // id inexistente: no-op (hoy, 0 filas actualizadas)
        propagarUrgente(imei, urgente);
    }

    public void actualizarChasis(String idRep, boolean esChasis) {
        jdbc.update("UPDATE Reparacion SET ES_CHASIS = ?, UPDATED_AT = UPDATED_AT WHERE ID_REP = ?", esChasis, idRep);
    }

    public void actualizarPorCerrar(String idRep, boolean porCerrar) {
        jdbc.update("UPDATE Reparacion SET POR_CERRAR = ?, UPDATED_AT = UPDATED_AT WHERE ID_REP = ?", porCerrar, idRep);
    }

    /** Asignación de glass abierta de un IMEI (id + dueño actual), la más antigua primero. */
    public record GlassAbierta(String idRep, String nombreTecnico) {}

    /** AG abiertas del IMEI: destinatarias de la entrega (spec entrega-glass §2). */
    public List<GlassAbierta> getGlassAbiertas(String imei) {
        return jdbc.query(
                "SELECT r.ID_REP, t.NOMBRE FROM Reparacion r JOIN Tecnico t ON r.ID_TEC = t.ID_TEC" +
                " WHERE r.IMEI = ? AND r.ID_REP LIKE 'AG%' AND r.FECHA_FIN IS NULL ORDER BY r.FECHA_ASIG ASC",
                (rs, i) -> new GlassAbierta(rs.getString("ID_REP"), rs.getString("NOMBRE")),
                imei);
    }

    /** Sella la entrega (ahora, por idTecEntrega) en TODAS las AG abiertas del IMEI. Volver a entregar sobrescribe. */
    public void entregarGlass(String imei, int idTecEntrega) {
        jdbc.update(
                "UPDATE Reparacion SET ENTREGADO_AT = NOW(), ENTREGADO_POR = ?, UPDATED_AT = UPDATED_AT" +
                " WHERE IMEI = ? AND ID_REP LIKE 'AG%' AND FECHA_FIN IS NULL",
                idTecEntrega, imei);
    }

    /** Deshace la entrega en TODAS las AG abiertas del IMEI. */
    public void deshacerEntregaGlass(String imei) {
        jdbc.update(
                "UPDATE Reparacion SET ENTREGADO_AT = NULL, ENTREGADO_POR = NULL, UPDATED_AT = UPDATED_AT" +
                " WHERE IMEI = ? AND ID_REP LIKE 'AG%' AND FECHA_FIN IS NULL",
                imei);
    }

    /** Marca URGENTE=true en TODAS las asignaciones abiertas (pulido incluido) de los
     *  teléfonos que cualifican: alguna rep/glass pendiente no urgente, con cliente,
     *  cuya FECHA_ASIG es anterior al cutoff (inicio de hoy en Madrid). Devuelve nº de filas. */
    public int marcarUrgentesClienteVencidas(java.sql.Timestamp cutoffUtc) {
        return jdbc.update(
            "UPDATE Reparacion r JOIN (" +
            "  SELECT DISTINCT r2.IMEI FROM Reparacion r2" +
            "  JOIN Telefono t ON t.IMEI = r2.IMEI" +
            "  WHERE r2.ID_REP LIKE 'A%' AND r2.ID_REP NOT LIKE 'AP%'" +
            "    AND r2.FECHA_FIN IS NULL AND t.ID_CLI IS NOT NULL" +
            "    AND r2.URGENTE = FALSE AND r2.FECHA_ASIG < ?" +
            ") q ON q.IMEI = r.IMEI " +
            "SET r.URGENTE = TRUE, r.UPDATED_AT = r.UPDATED_AT " +
            "WHERE r.FECHA_FIN IS NULL AND r.URGENTE = FALSE",
            cutoffUtc);
    }

    @Transactional
    public void actualizarTecnico(String idRep, int idTec, LocalDateTime updatedAt) {
        Integer idTecActual = jdbc.queryForObject(
                "SELECT ID_TEC FROM Reparacion WHERE ID_REP = ?", Integer.class, idRep);
        // UPDATE atómico: el WHERE con UPDATED_AT evita la ventana SELECT→UPDATE del patrón TOCTOU
        int filas = jdbc.update(
                "UPDATE Reparacion SET ID_TEC = ? WHERE ID_REP = ? AND UPDATED_AT = ?",
                idTec, idRep,
                Timestamp.valueOf(updatedAt.truncatedTo(ChronoUnit.SECONDS)));
        if (filas == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Dato modificado por otro usuario");
        }
        if (idTecActual != null && idTecActual != idTec) {
            borradorDao.eliminar(idRep);   // reasignada a otro técnico: el borrador del técnico anterior ya no aplica
        }
    }

    @Transactional
    public void editarReparacion(String idRep, int idComNuevo, boolean esReutilizadoNuevo,
                                  String observacionNueva, int nNuevas, LocalDateTime updatedAt) {
        record RcRow(int idCom, boolean esReutilizado, int cantidad, LocalDateTime updatedAt) {}
        RcRow vieja = jdbc.queryForObject(
                "SELECT ID_COM, ES_REUTILIZADO, CANTIDAD, UPDATED_AT" +
                " FROM Reparacion_componente WHERE ID_REP = ? FOR UPDATE",
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
            int oldMasterIdCom = resolveToMasterId(vieja.idCom());
            jdbc.update("UPDATE Componente SET STOCK = STOCK + ? WHERE ID_COM = ?",
                    vieja.cantidad(), oldMasterIdCom);
        }
        jdbc.update(
                "UPDATE Reparacion_componente SET ID_COM=?, ES_REUTILIZADO=?, OBSERVACIONES=?, CANTIDAD=?" +
                " WHERE ID_REP=?",
                idComNuevo, esReutilizadoNuevo, observacionNueva, nNuevas, idRep);
        if (!esReutilizadoNuevo) {
            int newMasterIdCom = resolveToMasterId(idComNuevo);
            jdbc.update("UPDATE Componente SET STOCK = STOCK - ? WHERE ID_COM = ?",
                    nNuevas, newMasterIdCom);
        }
    }

    @Transactional
    public void marcarIncidenciaYAsignar(String idRep, String comentario, String imei, int idTec, Integer idTecAsigna, int idUsu) {
        jdbc.update(
                "UPDATE Reparacion_componente SET ES_INCIDENCIA = 1, INCIDENCIA = ? WHERE ID_REP = ?",
                comentario, idRep);
        ensureTelefono(imei);
        // Reincidencia de glass -> se reasigna como glass (AG); reparación -> A.
        String idAsig = nextId(idRep.startsWith("G") ? "AG" : "A");
        jdbc.update("INSERT INTO Reparacion (ID_REP, IMEI, ID_TEC, ID_REP_ANTERIOR, FECHA_ASIG, COMENTARIO_ASIGNACION, ID_TEC_ASIGNA) VALUES (?,?,?,?,NOW(),?,?)",
                idAsig, imei, idTec, idRep, comentario, idTecAsigna);
        resetRevisionAlAsignar(imei, idUsu);
        // Sin checkbox de urgente: hereda en silencio si el teléfono ya lo estaba
        // (la propagación incluye esta fila recién creada).
        if (tieneAbiertaUrgente(imei)) propagarUrgente(imei, true);
    }

    @Transactional
    public void borrarIncidenciaPorImei(String imei, String categoria) {
        String likeRep  = "G".equals(categoria) ? "G%" : "R%";
        String likeAsig = "G".equals(categoria) ? "AG%" : "A%";
        jdbc.update(
                "UPDATE Reparacion_componente rc" +
                " JOIN Reparacion r ON rc.ID_REP = r.ID_REP" +
                " SET rc.ES_INCIDENCIA = 0, rc.INCIDENCIA = NULL" +
                " WHERE r.IMEI = ? AND r.ID_REP LIKE ?" +
                " AND rc.ES_INCIDENCIA = 1 AND rc.ES_RESUELTO = 0",
                imei, likeRep);
        List<String> asigs = jdbc.query(
                "SELECT ID_REP FROM Reparacion WHERE IMEI = ? AND ID_REP LIKE ?" +
                ("G".equals(categoria) ? "" : " AND ID_REP NOT LIKE 'AP%' AND ID_REP NOT LIKE 'AG%'") +
                " AND FECHA_FIN IS NULL",
                (rs, row) -> rs.getString(1), imei, likeAsig);
        for (String idAsig : asigs) {
            // FOR UPDATE: si insertarCompleta tiene la fila bloqueada, esperamos.
            // Tras el lock re-chequeamos FECHA_FIN: si el técnico ya cerró la A*, la saltamos.
            Integer abierta = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM Reparacion WHERE ID_REP = ? AND FECHA_FIN IS NULL FOR UPDATE",
                    Integer.class, idAsig);
            if (abierta == null || abierta == 0) continue;
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
            String mismoPrefijo = idRep.startsWith("G") ? "G%" : "R%";
            Integer restantes = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM Reparacion WHERE ID_REP_ANTERIOR = ? AND ID_REP LIKE ? AND ID_REP != ?",
                    Integer.class, idRepOrig, mismoPrefijo, idRep);
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
                // Reconciliar urgencia: la fila reabierta conserva su URGENTE congelado;
                // si alguna asignación abierta del teléfono (incluida esta) es urgente,
                // propagar a todas (invariante: un solo estado de urgencia por teléfono).
                if (tieneAbiertaUrgente(imei)) propagarUrgente(imei, true);
            }
        }

        for (RcRow rc : rows) {
            if (!rc.esReutilizado() && rc.idCom() > 0) {
                int masterIdCom = resolveToMasterId(rc.idCom());
                jdbc.update("UPDATE Componente SET STOCK = STOCK + ? WHERE ID_COM = ?",
                        rc.cantidad(), masterIdCom);
            }
        }
        jdbc.update("DELETE FROM Reparacion_componente WHERE ID_REP = ?", idRep);
        jdbc.update("DELETE FROM Reparacion WHERE ID_REP = ?", idRep);
        deleteIfLastReparacion(imei);
    }

    @Transactional
    public void agotarComponente(String idAsignacion, int idCom, int cantidad, String descripcion) {
        Integer existe = jdbc.queryForObject(
                "SELECT COUNT(*) FROM Reparacion WHERE ID_REP = ? AND FECHA_FIN IS NULL FOR UPDATE",
                Integer.class, idAsignacion);
        if (existe == null || existe == 0)
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "La asignación ya fue cerrada o no existe");
        int masterIdCom = resolveToMasterId(idCom);
        int rows = jdbc.update(
                "UPDATE Componente SET STOCK = STOCK - ? WHERE ID_COM = ? AND STOCK >= ?",
                cantidad, masterIdCom, cantidad);
        if (rows == 0) {
            Integer stock = jdbc.queryForObject(
                    "SELECT STOCK FROM Componente WHERE ID_COM = ?", Integer.class, masterIdCom);
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Stock insuficiente para descontar (stock actual: " + stock + ", a descontar: " + cantidad + ")");
        }
        jdbc.update(
                "INSERT INTO Reparacion_componente" +
                " (ID_REP, ID_COM, ES_SOLICITUD, DESCRIPCION_SOLICITUD, ESTADO_SOLICITUD, CANTIDAD)" +
                " VALUES (?,?,1,?,'PENDIENTE',1)",
                idAsignacion, idCom, descripcion);
    }

    // ── pulido ────────────────────────────────────────────────────────────────

    private static final String ASIGNACION_PULIDO_SELECT =
            "SELECT r.ID_REP, r.IMEI, t.NOMBRE AS NOMBRE_TEC," +
            " r.FECHA_ASIG, r.FECHA_FIN," +
            " NULL AS TIPO_COM, NULL AS OBSERVACIONES," +
            " 0 AS ES_INCIDENCIA, 0 AS ES_RESUELTO, 0 AS ES_REUTILIZADO, NULL AS INCIDENCIA," +
            " r.ID_REP_ANTERIOR, r.ID_TEC," +
            " 0 AS ES_SOLICITUD, NULL AS DESC_SOL," +
            " NULL AS ESTADO_SOL, NULL AS TIPO_SOL, 0 AS STOCK_SOL, 0 AS EN_CAMINO_SOL, NULL AS TIPOS_SOL," +
            " r.UPDATED_AT, tel.MODELO, r.COMENTARIO_ASIGNACION," +
            " tel.OBSERVACION AS OBSERVACION_TELEFONO, tel.UPDATED_AT AS TELEFONO_UPDATED_AT, r.URGENTE," +
            " ta.NOMBRE AS NOMBRE_TEC_ASIGNA," +
            " cli.NOMBRE AS CLIENTE" +
            " FROM Reparacion r" +
            " JOIN Tecnico t ON r.ID_TEC = t.ID_TEC" +
            " LEFT JOIN Tecnico ta ON r.ID_TEC_ASIGNA = ta.ID_TEC" +
            " LEFT JOIN Telefono tel ON r.IMEI = tel.IMEI" +
            " LEFT JOIN Cliente cli ON tel.ID_CLI = cli.ID_CLI" +
            " WHERE r.ID_REP LIKE 'AP%' AND r.FECHA_FIN IS NULL";

    private static final String HISTORIAL_PULIDO_SELECT =
            "SELECT r.ID_REP, r.IMEI, t.NOMBRE AS NOMBRE_TEC," +
            " r.FECHA_ASIG, r.FECHA_FIN," +
            " NULL AS TIPO_COM, NULL AS OBSERVACIONES," +
            " 0 AS ES_INCIDENCIA, 0 AS ES_RESUELTO, 0 AS ES_REUTILIZADO, NULL AS INCIDENCIA," +
            " r.ID_REP_ANTERIOR, r.ID_TEC," +
            " 0 AS ES_SOLICITUD, NULL AS DESC_SOL," +
            " NULL AS ESTADO_SOL, NULL AS TIPO_SOL, 0 AS STOCK_SOL, 0 AS EN_CAMINO_SOL, NULL AS TIPOS_SOL," +
            " r.UPDATED_AT, tel.MODELO, r.COMENTARIO_ASIGNACION," +
            " tel.OBSERVACION AS OBSERVACION_TELEFONO, tel.UPDATED_AT AS TELEFONO_UPDATED_AT," +
            " ta.NOMBRE AS NOMBRE_TEC_ASIGNA," +
            " cli.NOMBRE AS CLIENTE" +
            " FROM Reparacion r" +
            " JOIN Tecnico t ON r.ID_TEC = t.ID_TEC" +
            " LEFT JOIN Tecnico ta ON r.ID_TEC_ASIGNA = ta.ID_TEC" +
            " LEFT JOIN Telefono tel ON r.IMEI = tel.IMEI" +
            " LEFT JOIN Cliente cli ON tel.ID_CLI = cli.ID_CLI" +
            " WHERE r.ID_REP LIKE 'P%'";

    // ── glass ─────────────────────────────────────────────────────────────────
    // Glass ≈ reparación: mismas columnas y JOINs que reparación (consume pieza,
    // solicitudes, incidencias), solo cambia el espacio de IDs (AG/G).

    private static final String GLASS_ASIGNACION_SELECT =
            "SELECT r.ID_REP, r.IMEI, t.NOMBRE AS NOMBRE_TEC," +
            " r.FECHA_ASIG, r.FECHA_FIN," +
            " NULL AS TIPO_COM, NULL AS OBSERVACIONES," +
            " (CASE WHEN r.ID_REP_ANTERIOR IS NOT NULL THEN 1 ELSE 0 END) AS ES_INCIDENCIA, 0 AS ES_RESUELTO, 0 AS ES_REUTILIZADO, NULL AS INCIDENCIA," +
            " r.ID_REP_ANTERIOR, r.ID_TEC," +
            " COUNT(rc.ID_RC) AS ES_SOLICITUD, NULL AS DESC_SOL," +
            " (SELECT rc2.ESTADO_SOLICITUD FROM Reparacion_componente rc2" +
            "  WHERE rc2.ID_REP = r.ID_REP AND rc2.ES_SOLICITUD = 1" +
            "  ORDER BY CASE rc2.ESTADO_SOLICITUD WHEN 'PENDIENTE' THEN 0 WHEN 'RECHAZADA' THEN 1 ELSE 2 END LIMIT 1) AS ESTADO_SOL," +
            " (SELECT c2.TIPO FROM Reparacion_componente rc2" +
            "  JOIN Componente c2 ON rc2.ID_COM = c2.ID_COM" +
            "  WHERE rc2.ID_REP = r.ID_REP AND rc2.ES_SOLICITUD = 1" +
            "  ORDER BY CASE rc2.ESTADO_SOLICITUD WHEN 'PENDIENTE' THEN 0 WHEN 'RECHAZADA' THEN 1 ELSE 2 END LIMIT 1) AS TIPO_SOL," +
            " (SELECT COALESCE(master2.STOCK, c2.STOCK)" +
            "  FROM Reparacion_componente rc2" +
            "  JOIN Componente c2 ON rc2.ID_COM = c2.ID_COM" +
            "  LEFT JOIN Componente master2 ON c2.ID_COM_MASTER = master2.ID_COM" +
            "  WHERE rc2.ID_REP = r.ID_REP AND rc2.ES_SOLICITUD = 1" +
            "  ORDER BY CASE rc2.ESTADO_SOLICITUD WHEN 'PENDIENTE' THEN 0 WHEN 'RECHAZADA' THEN 1 ELSE 2 END LIMIT 1) AS STOCK_SOL," +
            " (SELECT CASE WHEN COUNT(*) > 0 THEN 1 ELSE 0 END FROM Compra_componente cc" +
            "  WHERE cc.ESTADO = 'en_camino' AND cc.ID_COM IN (" +
            "   SELECT rc2.ID_COM FROM Reparacion_componente rc2" +
            "   WHERE rc2.ID_REP = r.ID_REP AND rc2.ES_SOLICITUD = 1 AND rc2.ESTADO_SOLICITUD != 'RECHAZADA')) AS EN_CAMINO_SOL," +
            " (SELECT GROUP_CONCAT(c2.TIPO ORDER BY c2.TIPO SEPARATOR ', ')" +
            "  FROM Reparacion_componente rc2 JOIN Componente c2 ON rc2.ID_COM = c2.ID_COM" +
            "  WHERE rc2.ID_REP = r.ID_REP AND rc2.ES_SOLICITUD = 1 AND rc2.ESTADO_SOLICITUD != 'RECHAZADA') AS TIPOS_SOL," +
            " r.UPDATED_AT, tel.MODELO, r.COMENTARIO_ASIGNACION," +
            " tel.OBSERVACION AS OBSERVACION_TELEFONO, tel.UPDATED_AT AS TELEFONO_UPDATED_AT, r.URGENTE, r.ES_CHASIS," +
            " r.ENTREGADO_AT," +
            " (SELECT te.NOMBRE FROM Tecnico te WHERE te.ID_TEC = r.ENTREGADO_POR) AS ENTREGADO_POR_NOMBRE," +
            " ta.NOMBRE AS NOMBRE_TEC_ASIGNA," +
            " cli.NOMBRE AS CLIENTE" +
            " FROM Reparacion r" +
            " JOIN Tecnico t ON r.ID_TEC = t.ID_TEC" +
            " LEFT JOIN Tecnico ta ON r.ID_TEC_ASIGNA = ta.ID_TEC" +
            " LEFT JOIN Reparacion_componente rc ON r.ID_REP = rc.ID_REP AND rc.ES_SOLICITUD = 1 AND rc.ESTADO_SOLICITUD != 'RECHAZADA'" +
            " LEFT JOIN Telefono tel ON r.IMEI = tel.IMEI" +
            " LEFT JOIN Cliente cli ON tel.ID_CLI = cli.ID_CLI" +
            " WHERE r.ID_REP LIKE 'AG%' AND r.FECHA_FIN IS NULL";

    private static final String GLASS_HISTORIAL_SELECT =
            "SELECT r.ID_REP, r.IMEI, t.NOMBRE AS NOMBRE_TEC," +
            " r.FECHA_ASIG, r.FECHA_FIN," +
            " c.TIPO AS TIPO_COM, rc.OBSERVACIONES," +
            " COALESCE(rc.ES_INCIDENCIA, 0) AS ES_INCIDENCIA," +
            " COALESCE(rc.ES_RESUELTO, 0) AS ES_RESUELTO," +
            " COALESCE(rc.ES_REUTILIZADO, 0) AS ES_REUTILIZADO," +
            " rc.INCIDENCIA, r.ID_REP_ANTERIOR, r.ID_TEC," +
            " 0 AS ES_SOLICITUD, NULL AS DESC_SOL," +
            " NULL AS ESTADO_SOL, NULL AS TIPO_SOL, 0 AS STOCK_SOL, 0 AS EN_CAMINO_SOL, NULL AS TIPOS_SOL," +
            " r.UPDATED_AT, tel.MODELO, NULL AS COMENTARIO_ASIGNACION," +
            " tel.OBSERVACION AS OBSERVACION_TELEFONO," +
            " tel.UPDATED_AT AS TELEFONO_UPDATED_AT," +
            " ta.NOMBRE AS NOMBRE_TEC_ASIGNA," +
            " (SELECT COUNT(*) FROM Reparacion r2" +
            "  WHERE r2.IMEI = r.IMEI AND r2.ID_REP LIKE 'A%'" +
            "  AND r2.FECHA_FIN IS NULL) AS TIENE_ASIGNACIONES," +
            " cli.NOMBRE AS CLIENTE" +
            " FROM Reparacion r" +
            " JOIN Tecnico t ON r.ID_TEC = t.ID_TEC" +
            " LEFT JOIN Tecnico ta ON r.ID_TEC_ASIGNA = ta.ID_TEC" +
            " LEFT JOIN Reparacion_componente rc ON r.ID_REP = rc.ID_REP" +
            " LEFT JOIN Componente c ON rc.ID_COM = c.ID_COM" +
            " LEFT JOIN Telefono tel ON r.IMEI = tel.IMEI" +
            " LEFT JOIN Cliente cli ON tel.ID_CLI = cli.ID_CLI" +
            " WHERE r.ID_REP LIKE 'G%'";

    public List<ReparacionResumen> getAsignacionesGlass(Integer idTecFilter) {
        String groupBy = " GROUP BY r.ID_REP, r.IMEI, t.NOMBRE, r.FECHA_ASIG, r.FECHA_FIN," +
                " r.ID_REP_ANTERIOR, r.ID_TEC, r.UPDATED_AT, tel.MODELO, r.COMENTARIO_ASIGNACION," +
                " tel.OBSERVACION, tel.UPDATED_AT, r.URGENTE, r.ES_CHASIS, r.ENTREGADO_AT, r.ENTREGADO_POR, ta.NOMBRE, cli.NOMBRE ORDER BY r.FECHA_ASIG ASC";
        if (idTecFilter != null)
            return jdbc.query(GLASS_ASIGNACION_SELECT + " AND r.ID_TEC = ?" + groupBy, RESUMEN_MAPPER, idTecFilter);
        return jdbc.query(GLASS_ASIGNACION_SELECT + groupBy, RESUMEN_MAPPER);
    }

    public List<ReparacionResumen> getHistorialGlass(Integer idTecFilter) {
        if (idTecFilter != null)
            return jdbc.query(GLASS_HISTORIAL_SELECT + " AND r.ID_TEC = ?" + ORDER_HISTORIAL, RESUMEN_MAPPER, idTecFilter);
        return jdbc.query(GLASS_HISTORIAL_SELECT + ORDER_HISTORIAL, RESUMEN_MAPPER);
    }

    public List<ReparacionResumen> getAsignacionesGlassPorImei(String imei) {
        String groupBy = " GROUP BY r.ID_REP, r.IMEI, t.NOMBRE, r.FECHA_ASIG, r.FECHA_FIN," +
                " r.ID_REP_ANTERIOR, r.ID_TEC, r.UPDATED_AT, tel.MODELO, r.COMENTARIO_ASIGNACION," +
                " tel.OBSERVACION, tel.UPDATED_AT, r.URGENTE, r.ES_CHASIS, r.ENTREGADO_AT, r.ENTREGADO_POR, ta.NOMBRE, cli.NOMBRE ORDER BY r.FECHA_ASIG ASC";
        return jdbc.query(GLASS_ASIGNACION_SELECT + " AND r.IMEI = ?" + groupBy, RESUMEN_MAPPER, imei);
    }

    public List<ReparacionResumen> getHistorialGlassPorImei(String imei) {
        return jdbc.query(GLASS_HISTORIAL_SELECT + " AND r.IMEI = ?" + ORDER_HISTORIAL, RESUMEN_MAPPER, imei);
    }

    public List<ReparacionResumen> getAsignacionesPulido(Integer idTecFilter) {
        String order = " ORDER BY r.FECHA_ASIG ASC";
        if (idTecFilter != null) {
            return jdbc.query(ASIGNACION_PULIDO_SELECT + " AND r.ID_TEC = ?" + order, RESUMEN_MAPPER, idTecFilter);
        }
        return jdbc.query(ASIGNACION_PULIDO_SELECT + order, RESUMEN_MAPPER);
    }

    public List<ReparacionResumen> getHistorialPulido(Integer idTecFilter) {
        String order = " ORDER BY r.FECHA_ASIG DESC";
        if (idTecFilter != null) {
            return jdbc.query(HISTORIAL_PULIDO_SELECT + " AND r.ID_TEC = ?" + order, RESUMEN_MAPPER, idTecFilter);
        }
        return jdbc.query(HISTORIAL_PULIDO_SELECT + order, RESUMEN_MAPPER);
    }

    @Transactional
    public String insertarAsignacionPulido(String imei, int idTec, String comentario, Integer idTecAsigna, int idUsu) {
        ensureTelefono(imei);
        String idRep = nextId("AP");
        jdbc.update(
                "INSERT INTO Reparacion (ID_REP, IMEI, ID_TEC, FECHA_ASIG, COMENTARIO_ASIGNACION, ID_TEC_ASIGNA) VALUES (?,?,?,NOW(),?,?)",
                idRep, imei, idTec, (comentario != null && !comentario.isBlank()) ? comentario : null, idTecAsigna);
        resetRevisionAlAsignar(imei, idUsu);
        // Pulido no tiene checkbox de urgente: hereda en silencio si el teléfono ya lo estaba
        // (la propagación incluye esta fila recién creada, que nació con URGENTE = 0).
        if (tieneAbiertaUrgente(imei)) propagarUrgente(imei, true);
        return idRep;
    }

    @Transactional
    public void completarPulido(String idAP) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT IMEI, ID_TEC, COMENTARIO_ASIGNACION, ID_TEC_ASIGNA FROM Reparacion WHERE ID_REP = ? AND FECHA_FIN IS NULL FOR UPDATE", idAP);
        if (rows.isEmpty())
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Pulido ya completado o no existe");
        Map<String, Object> row = rows.get(0);
        String idP = nextId("P");
        jdbc.update(
                "INSERT INTO Reparacion (ID_REP, IMEI, ID_TEC, ID_REP_ANTERIOR, FECHA_ASIG, FECHA_FIN, COMENTARIO_ASIGNACION, ID_TEC_ASIGNA) VALUES (?,?,?,?,NOW(),NOW(),?,?)",
                idP, row.get("IMEI"), row.get("ID_TEC"), idAP, row.get("COMENTARIO_ASIGNACION"), row.get("ID_TEC_ASIGNA"));
        jdbc.update("UPDATE Reparacion SET FECHA_FIN = NOW() WHERE ID_REP = ?", idAP);
    }

    @Transactional
    public void completarPulidoLote(List<String> ids) {
        for (String idAP : ids) completarPulido(idAP);
    }

    public void eliminarAsignacionPulido(String idAP) {
        jdbc.update("DELETE FROM Reparacion WHERE ID_REP = ? AND FECHA_FIN IS NULL", idAP);
    }

    @Transactional
    public void eliminarPulido(String idP) {
        String imei = jdbc.queryForObject(
                "SELECT IMEI FROM Reparacion WHERE ID_REP = ? AND FECHA_FIN IS NOT NULL", String.class, idP);
        jdbc.update("DELETE FROM Reparacion WHERE ID_REP = ?", idP);
        deleteIfLastReparacion(imei);
    }

    @Transactional
    public void actualizarAsignacionPulido(String idAP, int idTec, String comentario, LocalDateTime updatedAt,
                                           Integer idTecAsigna) {
        Integer idTecActual = jdbc.queryForObject(
                "SELECT ID_TEC FROM Reparacion WHERE ID_REP = ?", Integer.class, idAP);
        int filas = jdbc.update(
                "UPDATE Reparacion SET ID_TEC = ?, COMENTARIO_ASIGNACION = ? WHERE ID_REP = ? AND UPDATED_AT = ?",
                idTec, comentario, idAP, Timestamp.valueOf(updatedAt.truncatedTo(ChronoUnit.SECONDS)));
        if (filas == 0) throw new ResponseStatusException(HttpStatus.CONFLICT, "Dato modificado por otro usuario");
        // Reasignación a otro técnico: "asignado por" pasa a ser el SuperTécnico que reasigna.
        if (idTecAsigna != null && idTecActual != null && idTecActual != idTec) {
            jdbc.update("UPDATE Reparacion SET ID_TEC_ASIGNA = ? WHERE ID_REP = ?", idTecAsigna, idAP);
        }
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private int resolveToMasterId(int idCom) {
        Integer master = jdbc.queryForObject(
                "SELECT COALESCE(ID_COM_MASTER, ID_COM) FROM Componente WHERE ID_COM = ?",
                Integer.class, idCom);
        return master != null ? master : idCom;
    }

    /** Prefijo de la fila terminada según el de la asignación: AG->G, resto->R. */
    private static String resultPrefix(String idAsignacion) {
        return (idAsignacion != null && idAsignacion.startsWith("AG")) ? "G" : "R";
    }

    private String nextId(String prefijo) {
        String hoy  = LocalDate.now(ZoneId.of("Europe/Madrid")).format(FMT_ID);
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

    /** Al asignar trabajo, un teléfono OK vuelve solo al ciclo de revisión (pasada nueva NO: misma pasada). */
    private void resetRevisionAlAsignar(String imei, int idUsu) {
        List<Integer> filasIdCli = jdbc.query("SELECT ID_CLI FROM Telefono WHERE IMEI = ?",
                (rs, row) -> (Integer) rs.getObject("ID_CLI"), imei);
        Integer idCli = filasIdCli.isEmpty() ? null : filasIdCli.get(0);
        if (jdbc.update("UPDATE Telefono SET ESTADO = 'EN_REVISION' WHERE IMEI = ? AND ESTADO = 'OK'", imei) > 0) {
            movimientoDao.registrar(imei, MovimientoDAO.ubicacionDe("OK", idCli), "PARA_REVISAR", idUsu, "Trabajo asignado", null);
        }
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

    public Optional<ReparacionResumen> getHistorialById(String idRep) {
        List<ReparacionResumen> rows = jdbc.query(
                HISTORIAL_SELECT + " AND r.ID_REP = ?" + ORDER_HISTORIAL,
                RESUMEN_MAPPER, idRep);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    public Optional<ReparacionResumen> getResumenById(String idRep) {
        if (idRep.startsWith("AG")) return getAsignacionGlassById(idRep);
        if (idRep.startsWith("G"))  return getHistorialGlassById(idRep);
        return idRep.startsWith("A") ? getAsignacionById(idRep) : getHistorialById(idRep);
    }

    /** Asignación pendiente por ID, glass-aware (AG -> glass, A -> reparación). */
    public Optional<ReparacionResumen> getAsignacionAnyById(String idRep) {
        return idRep.startsWith("AG") ? getAsignacionGlassById(idRep) : getAsignacionById(idRep);
    }

    public Optional<ReparacionResumen> getAsignacionGlassById(String idRep) {
        String groupBy = " GROUP BY r.ID_REP, r.IMEI, t.NOMBRE, r.FECHA_ASIG, r.FECHA_FIN," +
                " r.ID_REP_ANTERIOR, r.ID_TEC, r.UPDATED_AT, tel.MODELO, r.COMENTARIO_ASIGNACION," +
                " tel.OBSERVACION, tel.UPDATED_AT, r.URGENTE, r.ES_CHASIS, r.ENTREGADO_AT, r.ENTREGADO_POR, ta.NOMBRE, cli.NOMBRE";
        List<ReparacionResumen> result = jdbc.query(GLASS_ASIGNACION_SELECT + " AND r.ID_REP = ?" + groupBy, RESUMEN_MAPPER, idRep);
        return result.isEmpty() ? Optional.empty() : Optional.of(result.get(0));
    }

    public Optional<ReparacionResumen> getHistorialGlassById(String idRep) {
        List<ReparacionResumen> rows = jdbc.query(
                GLASS_HISTORIAL_SELECT + " AND r.ID_REP = ?" + ORDER_HISTORIAL, RESUMEN_MAPPER, idRep);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    public String getNombreTecnicoById(int idTec) {
        return jdbc.queryForObject(
                "SELECT NOMBRE FROM Tecnico WHERE ID_TEC = ?", String.class, idTec);
    }

    public String getTipoComponenteById(int idCom) {
        return jdbc.queryForObject(
                "SELECT TIPO FROM Componente WHERE ID_COM = ?", String.class, idCom);
    }

    public String getModeloByImei(String imei) {
        List<String> rows = jdbc.query(
                "SELECT COALESCE(MODELO,'?') FROM Telefono WHERE IMEI = ?",
                (rs, i) -> rs.getString(1), imei);
        return rows.isEmpty() ? "?" : rows.get(0);
    }
}
