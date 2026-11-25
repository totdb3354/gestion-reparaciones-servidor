package com.reparaciones.servidor.bot;

import com.reparaciones.servidor.bot.ReglaDevoluciones.Paso;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.*;

/** Bot_movimiento y las lecturas de la lista de devoluciones (spec 2026-11-24-bot-devoluciones §4 y §5). */
@Repository
public class BotDAO {

    static final String SQL_GUARDAR = "INSERT IGNORE INTO Bot_movimiento (RUN_ID, IMEI, ACCION, RESULTADO, ORIGEN_NSYS, DESTINO_NSYS, MOTIVO, FECHA, RECIBIDO)"
            + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
    static final String SQL_APUNTAR = "INSERT INTO Pedido_fila_cambio (ID_FILA, ID_USU, FECHA, IMEI_ANTERIOR, IMEI_NUEVO, TIPO, DETALLE)"
            + " VALUES (?, NULL, ?, ?, NULL, ?, ?)";

    private final JdbcTemplate jdbc;

    public BotDAO(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Guarda un movimiento; un repetido (misma vuelta, IMEI y accion) no entra. 1 si entro, 0 si ya estaba. */
    public int guardar(String run, MovimientoBot m, LocalDateTime recibido) {
        return jdbc.update(SQL_GUARDAR, run, m.imei(), m.accion(), m.resultado(), m.origen(), m.destino(), m.motivo(),
                Timestamp.valueOf(m.fecha()), Timestamp.valueOf(recibido));
    }

    /** Un apunte del historial de la fila sin usuario, con el IMEI que salio de ella en IMEI_ANTERIOR. */
    public void apuntar(int idFila, LocalDateTime fecha, String imei, String tipo, String detalle) {
        jdbc.update(SQL_APUNTAR, idFila, Timestamp.valueOf(fecha), imei, tipo, detalle);
    }

    /** Un apunte de Pedido_fila_cambio de los que mira la lista: los de IMEI (sale o entra) y los SIN_DESTINO. */
    public record Apunte(int idFila, LocalDateTime fecha, String imeiAnterior, String imeiNuevo, String tipo, String detalle, String usuario) {
        /** Quien quito el IMEI, para el historial: la persona, el libro de ventas (detalle LIBRO) o un proceso. */
        public String quien() {
            if (usuario != null) return usuario;
            return "LIBRO".equals(detalle) ? "el libro de ventas" : "un proceso";
        }
    }

    /** Los IMEI con un descuento OK del bot desde {@code ?}, que salieron alguna vez de una fila y que hoy no estan en ninguna
     *  (vender gana a devolver): como mucho {@code ?}. */
    static final String SQL_CANDIDATOS = "SELECT DISTINCT b.IMEI FROM Bot_movimiento b"
            + " WHERE b.ACCION = 'DESCONTADO' AND b.RESULTADO = 'OK' AND b.FECHA >= ?"
            + " AND EXISTS (SELECT 1 FROM Pedido_fila_cambio c WHERE c.TIPO = 'IMEI' AND c.IMEI_ANTERIOR = b.IMEI)"
            + " AND NOT EXISTS (SELECT 1 FROM Pedido_fila f WHERE f.IMEI = b.IMEI)"
            + " ORDER BY b.IMEI LIMIT ?";
    static final String SQL_PASOS = "SELECT IMEI, ACCION, RESULTADO, ORIGEN_NSYS, FECHA FROM Bot_movimiento WHERE IMEI IN (";
    static final String ORDEN_PASOS = ") ORDER BY FECHA, ID";
    static final String SQL_APUNTES = "SELECT c.ID_FILA, c.FECHA, c.IMEI_ANTERIOR, c.IMEI_NUEVO, c.TIPO, c.DETALLE, u.NOMBRE_USUARIO"
            + " FROM Pedido_fila_cambio c LEFT JOIN Usuario u ON u.ID_USU = c.ID_USU"
            + " WHERE c.TIPO IN ('IMEI', 'SIN_DESTINO') AND (c.IMEI_ANTERIOR IN (%s) OR c.IMEI_NUEVO IN (%s))"
            + " ORDER BY c.FECHA, c.ID_CAMBIO";
    static final String SQL_ENTRADAS = "SELECT IMEI_SN, MAX(FECHA) AS ENTRADA FROM Nsys_evento"
            + " WHERE TIPO = 'UBICACION' AND A_NSYS = 'Pedidos realizados' AND IMEI_SN IN (%s) GROUP BY IMEI_SN";
    static final String SQL_ULTIMA_SALIDA = "SELECT c.ID_FILA, c.FECHA, c.IMEI_ANTERIOR, c.IMEI_NUEVO, c.TIPO, c.DETALLE, u.NOMBRE_USUARIO"
            + " FROM Pedido_fila_cambio c LEFT JOIN Usuario u ON u.ID_USU = c.ID_USU"
            + " WHERE c.TIPO = 'IMEI' AND c.IMEI_ANTERIOR = ? ORDER BY c.FECHA DESC, c.ID_CAMBIO DESC LIMIT 1";
    /** Los resultados de los DEVUELTO desde el ultimo descuento OK, el mas reciente primero (hasta 4: basta para saber si el
     *  que acaba de llegar es el tercer ERROR seguido). */
    static final String SQL_ULTIMOS_DEVUELTOS = "SELECT RESULTADO FROM Bot_movimiento WHERE IMEI = ? AND ACCION = 'DEVUELTO'"
            + " AND FECHA >= COALESCE((SELECT MAX(d.FECHA) FROM Bot_movimiento d WHERE d.IMEI = ? AND d.ACCION = 'DESCONTADO'"
            + " AND d.RESULTADO = 'OK'), '1970-01-01') ORDER BY FECHA DESC, ID DESC LIMIT 4";

    public List<String> candidatos(LocalDateTime desde, int tope) {
        return jdbc.queryForList(SQL_CANDIDATOS, String.class, Timestamp.valueOf(desde), tope);
    }

    public Map<String, List<Paso>> pasos(Collection<String> imeis) {
        if (imeis.isEmpty()) return Map.of();
        Map<String, List<Paso>> porImei = new HashMap<>();
        jdbc.query(SQL_PASOS + marcas(imeis.size()) + ORDEN_PASOS, (ResultSet rs, int n) -> {
            porImei.computeIfAbsent(rs.getString("IMEI"), k -> new ArrayList<>()).add(new Paso(rs.getString("ACCION"),
                    rs.getString("RESULTADO"), rs.getString("ORIGEN_NSYS"), rs.getTimestamp("FECHA").toLocalDateTime()));
            return null;
        }, imeis.toArray());
        return porImei;
    }

    public List<Apunte> apuntes(Collection<String> imeis) {
        if (imeis.isEmpty()) return List.of();
        String m = marcas(imeis.size());
        List<Object> args = new ArrayList<>(imeis);
        args.addAll(imeis);
        return jdbc.query(String.format(SQL_APUNTES, m, m), BotDAO::apunte, args.toArray());
    }

    public Map<String, LocalDateTime> entradasEnPedidos(Collection<String> imeis) {
        if (imeis.isEmpty()) return Map.of();
        Map<String, LocalDateTime> entradas = new HashMap<>();
        jdbc.query(String.format(SQL_ENTRADAS, marcas(imeis.size())), (ResultSet rs, int n) -> {
            entradas.put(rs.getString("IMEI_SN"), rs.getTimestamp("ENTRADA").toLocalDateTime());
            return null;
        }, imeis.toArray());
        return entradas;
    }

    public Optional<Apunte> ultimaSalida(String imei) {
        return jdbc.query(SQL_ULTIMA_SALIDA, BotDAO::apunte, imei).stream().findFirst();
    }

    public List<String> ultimosDevueltos(String imei) {
        return jdbc.queryForList(SQL_ULTIMOS_DEVUELTOS, String.class, imei, imei);
    }

    static Apunte apunte(ResultSet rs, int n) throws SQLException {
        return new Apunte(rs.getInt("ID_FILA"), rs.getTimestamp("FECHA").toLocalDateTime(), rs.getString("IMEI_ANTERIOR"),
                rs.getString("IMEI_NUEVO"), rs.getString("TIPO"), rs.getString("DETALLE"), rs.getString("NOMBRE_USUARIO"));
    }

    private static String marcas(int n) {
        return String.join(", ", Collections.nCopies(n, "?"));
    }
}
