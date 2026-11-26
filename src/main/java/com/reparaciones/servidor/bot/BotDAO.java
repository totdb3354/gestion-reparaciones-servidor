package com.reparaciones.servidor.bot;

import com.reparaciones.servidor.bot.ReglaDevoluciones.Paso;
import com.reparaciones.servidor.bot.ReglaDevoluciones.Salida;
import com.reparaciones.servidor.pedidos.CambioImei;
import com.reparaciones.servidor.pedidos.Reservas;
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
            return "LIBRO".equals(detalle) ? "el libro de ventas" : ReglaDevoluciones.UN_PROCESO;
        }

        /** Si este apunte saca a {@code imei} de su fila, esa salida; si no, null. Un apunte IMEI que lo quito o sobreescribio
         *  ({@link ReglaDevoluciones#SALIDA_IMEI}); su reserva soltada sin que nadie picara el movil: RESERVA_SUSTITUIDA o
         *  RESERVA_QUITADA por {@link #MOTIVOS_RESERVA_SALIDA} ({@link ReglaDevoluciones#SALIDA_RESERVA}), o por «ya no está»
         *  ({@link ReglaDevoluciones#SALIDA_YA_NO_ESTA}, que la regla deja sin destino). El resto de motivos (enviada sin IMEI, con
         *  etiqueta, no encontrado, puesto en otra fila, no disponible) no son una salida: el movil salio, lo tiene una persona o sigue vendiendose. */
        public Salida salida(String imei) {
            if (imei == null || !imei.equals(imeiAnterior)) return null;
            if (CambioImei.IMEI.equals(tipo)) return new Salida(idFila, fecha, quien(), ReglaDevoluciones.SALIDA_IMEI);
            if (CambioImei.RESERVA_SUSTITUIDA.equals(tipo)) return new Salida(idFila, fecha, quien(), ReglaDevoluciones.SALIDA_RESERVA);
            if (!CambioImei.RESERVA_QUITADA.equals(tipo)) return null;
            if (Reservas.YA_NO_ESTA.equals(detalle)) return new Salida(idFila, fecha, "ShippyPro", ReglaDevoluciones.SALIDA_YA_NO_ESTA);
            if (Reservas.EN_PEDIDOS_REALIZADOS.equals(detalle))
                return new Salida(idFila, fecha, "el ERP", ReglaDevoluciones.SALIDA_COMPROBAR);
            return MOTIVOS_RESERVA_SALIDA.contains(detalle) ? new Salida(idFila, fecha, "el ERP", ReglaDevoluciones.SALIDA_RESERVA) : null;
        }

        /** Que el IMEI se puso en su fila: un apunte IMEI o la reserva del ERP (RESERVADO). */
        public boolean puesto(String imei) {
            return imei != null && imei.equals(imeiNuevo) && (CambioImei.IMEI.equals(tipo) || CambioImei.RESERVADO.equals(tipo));
        }
    }

    /** Los motivos de RESERVA_QUITADA que devuelven el movil a stock (como «Cancelar móvil» en la 0.9.47). NO_DISPONIBLE no: el
     *  movil se vendio, se dio de baja o (sin al-bot) lo descontaron por otra venta, p. ej. del Simulador; devolverlo dejaria en
     *  NSYS un movil que ya salio. */
    static final Set<String> MOTIVOS_RESERVA_SALIDA = Set.of(Reservas.CANCELADA, Reservas.MARCADA);
    /** Condicion SQL sobre un apunte {@code c} de Pedido_fila_cambio: saca a {@code c.IMEI_ANTERIOR} de su fila (la misma eleccion
     *  que {@link Apunte#salida}). */
    static final String ES_SALIDA = "(c.TIPO IN ('IMEI', 'RESERVA_SUSTITUIDA') OR (c.TIPO = 'RESERVA_QUITADA' AND c.DETALLE IN ('"
            + Reservas.CANCELADA + "', '" + Reservas.MARCADA + "', '" + Reservas.YA_NO_ESTA + "', '" + Reservas.EN_PEDIDOS_REALIZADOS + "')))";

    /** Los IMEI con un descuento OK del bot desde {@code ?}, que salieron alguna vez de una fila (un apunte lo quito o solto su
     *  reserva, o un pedido de ShippyPro cancelado o cerrado sin envio se quedo con el) y que hoy no estan, ni puestos ni
     *  reservados, en ninguna fila abierta sin cancelar (vender gana a devolver; el resto de filas las decide la regla con
     *  {@link #filasConImei} y {@link #vueltas}): como mucho {@code ?}. */
    static final String SQL_CANDIDATOS = "SELECT DISTINCT b.IMEI FROM Bot_movimiento b"
            + " WHERE b.ACCION = 'DESCONTADO' AND b.RESULTADO = 'OK' AND b.FECHA >= ?"
            + " AND EXISTS (SELECT 1 FROM Nsys_movil n WHERE n.IMEI_SN = b.IMEI AND n.ESTADO = 'DE_PEDIDO' AND n.LOCATION = 'Pedidos realizados')"
            + " AND (EXISTS (SELECT 1 FROM Pedido_fila_cambio c WHERE c.IMEI_ANTERIOR = b.IMEI AND " + ES_SALIDA + ")"
            + " OR EXISTS (SELECT 1 FROM Pedido_fila x WHERE x.IMEI = b.IMEI AND x.ORIGEN = 'SHIPPYPRO'"
            + " AND (x.MARCA = 'CANCELADO' OR (x.CERRADA_EN IS NOT NULL AND x.ENVIO_FECHA IS NULL))))"
            + " AND NOT EXISTS (SELECT 1 FROM Pedido_fila f WHERE (f.IMEI = b.IMEI OR f.IMEI_RESERVADO = b.IMEI) AND f.CERRADA_EN IS NULL"
            + " AND (f.MARCA IS NULL OR f.MARCA <> 'CANCELADO'))"
            + " ORDER BY b.IMEI LIMIT ?";
    static final String SQL_PASOS = "SELECT IMEI, ACCION, RESULTADO, ORIGEN_NSYS, FECHA FROM Bot_movimiento WHERE IMEI IN (";
    static final String ORDEN_PASOS = ") ORDER BY FECHA, ID";
    static final String SQL_APUNTES = "SELECT c.ID_FILA, c.FECHA, c.IMEI_ANTERIOR, c.IMEI_NUEVO, c.TIPO, c.DETALLE, u.NOMBRE_USUARIO"
            + " FROM Pedido_fila_cambio c LEFT JOIN Usuario u ON u.ID_USU = c.ID_USU"
            + " WHERE c.TIPO IN ('IMEI', 'SIN_DESTINO', 'RESERVADO', 'RESERVA_SUSTITUIDA', 'RESERVA_QUITADA') AND (c.IMEI_ANTERIOR IN (%s) OR c.IMEI_NUEVO IN (%s))"
            + " ORDER BY c.FECHA, c.ID_CAMBIO";
    static final String SQL_ENTRADAS = "SELECT IMEI_SN, MAX(FECHA) AS ENTRADA FROM Nsys_evento"
            + " WHERE TIPO = 'UBICACION' AND A_NSYS = 'Pedidos realizados' AND IMEI_SN IN (%s) GROUP BY IMEI_SN";
    /** De donde (DE: ubicacion del ERP, p. ej. «En revisión») entro el movil en «Pedidos realizados» la ultima vez (0.9.48.3). */
    static final String SQL_DE_ENTRADAS = "SELECT e.IMEI_SN, e.DE FROM Nsys_evento e"
            + " WHERE e.TIPO = 'UBICACION' AND e.A_NSYS = 'Pedidos realizados' AND e.IMEI_SN IN (%s)"
            + " AND e.FECHA = (SELECT MAX(e2.FECHA) FROM Nsys_evento e2 WHERE e2.IMEI_SN = e.IMEI_SN AND e2.TIPO = 'UBICACION'"
            + " AND e2.A_NSYS = 'Pedidos realizados')";
    /** Todas las filas que tienen el IMEI, puesto o reservado, abiertas incluidas, con quien las cancelo y cuando se puso el IMEI en
     *  ellas (el reservado, cuando se reservo). */
    static final String SQL_FILAS_CON_IMEI = "SELECT f.ID_FILA, f.IMEI, f.IMEI_RESERVADO, f.RESERVADA_EN, f.CERRADA_EN, f.ENVIO_FECHA, f.IMPRESA_EN, f.FALTA_ENVIAR_EN, f.MARCA, f.MARCA_EN, f.ORIGEN,"
            + " (SELECT NOMBRE_USUARIO FROM Usuario WHERE ID_USU = f.MARCA_USU) AS MARCA_POR,"
            + " (SELECT MAX(c.FECHA) FROM Pedido_fila_cambio c WHERE c.ID_FILA = f.ID_FILA AND c.TIPO = 'IMEI' AND c.IMEI_NUEVO = f.IMEI) AS PUESTO_EN"
            + " FROM Pedido_fila f WHERE f.IMEI IN (%s) OR f.IMEI_RESERVADO IN (%s)";
    /** Cuando NSYS dice que el movil volvio a la empresa: venta deshecha, reentrada, sale de «Pedidos realizados» (DE lleva la caja
     *  de NSYS tal cual) o entra sin localizacion. */
    static final String SQL_VUELTAS = "SELECT IMEI_SN, FECHA FROM Nsys_evento WHERE IMEI_SN IN (%s)"
            + " AND (TIPO IN ('VENTA_DESHECHA', 'REENTRADA') OR (TIPO = 'UBICACION' AND (DE = 'Pedidos realizados' OR COALESCE(A_NSYS, '') = '')))";
    /** La ultima fila cancelada («Cancelar móvil») que se quedo con el IMEI. */
    static final String SQL_ULTIMA_CANCELACION = "SELECT f.ID_FILA, f.MARCA_EN, (SELECT NOMBRE_USUARIO FROM Usuario WHERE ID_USU = f.MARCA_USU) AS MARCA_POR"
            + " FROM Pedido_fila f WHERE f.IMEI = ? AND f.ORIGEN = 'SHIPPYPRO' AND f.MARCA = 'CANCELADO' AND f.MARCA_EN IS NOT NULL AND f.IMPRESA_EN IS NULL"
            + " ORDER BY f.MARCA_EN DESC, f.ID_FILA DESC LIMIT 1";
    static final String SQL_ULTIMA_SALIDA = "SELECT c.ID_FILA, c.FECHA, c.IMEI_ANTERIOR, c.IMEI_NUEVO, c.TIPO, c.DETALLE, u.NOMBRE_USUARIO"
            + " FROM Pedido_fila_cambio c LEFT JOIN Usuario u ON u.ID_USU = c.ID_USU"
            + " WHERE c.IMEI_ANTERIOR = ? AND " + ES_SALIDA + " AND NOT (c.TIPO = 'RESERVA_QUITADA' AND c.DETALLE IN ('" + Reservas.YA_NO_ESTA
            + "', '" + Reservas.EN_PEDIDOS_REALIZADOS + "'))"
            + " ORDER BY c.FECHA DESC, c.ID_CAMBIO DESC LIMIT 1";
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

    public Map<String, String> deEntradasEnPedidos(Collection<String> imeis) {
        if (imeis.isEmpty()) return Map.of();
        Map<String, String> de = new HashMap<>();
        jdbc.query(String.format(SQL_DE_ENTRADAS, marcas(imeis.size())), (ResultSet rs, int n) -> {
            de.put(rs.getString("IMEI_SN"), rs.getString("DE"));
            return null;
        }, imeis.toArray());
        return de;
    }

    /** Por IMEI, las filas que hoy lo tienen. */
    public Map<String, List<ReglaDevoluciones.FilaConImei>> filasConImei(Collection<String> imeis) {
        if (imeis.isEmpty()) return Map.of();
        Map<String, List<ReglaDevoluciones.FilaConImei>> porImei = new HashMap<>();
        Set<String> buscados = new HashSet<>(imeis);
        String m = marcas(imeis.size());
        List<Object> args = new ArrayList<>(imeis);
        args.addAll(imeis);
        jdbc.query(String.format(SQL_FILAS_CON_IMEI, m, m), (ResultSet rs, int n) -> {
            boolean cancelada = ReglaDevoluciones.SALIDA_CANCELADO.equals(rs.getString("MARCA"));
            String imei = rs.getString("IMEI"), reservado = rs.getString("IMEI_RESERVADO");
            if (imei != null && buscados.contains(imei))
                porImei.computeIfAbsent(imei, k -> new ArrayList<>()).add(filaConImei(rs, cancelada, fecha(rs, "PUESTO_EN")));
            // Una reserva viva cuenta como el IMEI en la fila (vender gana a devolver), puesta cuando se reservo.
            if (reservado != null && !reservado.equals(imei) && buscados.contains(reservado))
                porImei.computeIfAbsent(reservado, k -> new ArrayList<>()).add(filaConImei(rs, cancelada, fecha(rs, "RESERVADA_EN")));
            return null;
        }, args.toArray());
        return porImei;
    }

    private static ReglaDevoluciones.FilaConImei filaConImei(ResultSet rs, boolean cancelada, LocalDateTime puestoEn) throws SQLException {
        return new ReglaDevoluciones.FilaConImei(rs.getInt("ID_FILA"), fecha(rs, "CERRADA_EN"), fecha(rs, "ENVIO_FECHA"),
                fecha(rs, "IMPRESA_EN"), fecha(rs, "FALTA_ENVIAR_EN"), cancelada, cancelada ? fecha(rs, "MARCA_EN") : null,
                cancelada ? rs.getString("MARCA_POR") : null, puestoEn, "HISTORICO".equals(rs.getString("ORIGEN")));
    }

    /** Por IMEI, las fechas en que NSYS registra que el movil volvio a la empresa (cualquier lote). */
    public Map<String, List<LocalDateTime>> vueltas(Collection<String> imeis) {
        return fechasPorImei(SQL_VUELTAS, "IMEI_SN", "FECHA", imeis);
    }

    private Map<String, List<LocalDateTime>> fechasPorImei(String sql, String colImei, String colFecha, Collection<String> imeis) {
        if (imeis.isEmpty()) return Map.of();
        Map<String, List<LocalDateTime>> porImei = new HashMap<>();
        jdbc.query(String.format(sql, marcas(imeis.size())), (ResultSet rs, int n) -> {
            porImei.computeIfAbsent(rs.getString(colImei), k -> new ArrayList<>()).add(rs.getTimestamp(colFecha).toLocalDateTime());
            return null;
        }, imeis.toArray());
        return porImei;
    }

    /** La salida mas reciente del IMEI para el historial: el ultimo apunte que lo saco de una fila (tambien su reserva soltada) o la ultima fila cancelada
     *  que se quedo con el (la misma eleccion que la regla, sin «Ya no está en ShippyPro», que nunca se devuelve solo). */
    public Optional<ReglaDevoluciones.Salida> ultimaSalida(String imei) {
        Optional<ReglaDevoluciones.Salida> apunte = jdbc.query(SQL_ULTIMA_SALIDA, BotDAO::apunte, imei).stream().findFirst()
                .map(a -> a.salida(imei));
        Optional<ReglaDevoluciones.Salida> cancelacion = jdbc.query(SQL_ULTIMA_CANCELACION, (ResultSet rs, int n) -> {
            String por = rs.getString("MARCA_POR");
            return new ReglaDevoluciones.Salida(rs.getInt("ID_FILA"), rs.getTimestamp("MARCA_EN").toLocalDateTime(),
                    por == null ? ReglaDevoluciones.UN_PROCESO : por, ReglaDevoluciones.SALIDA_CANCELADO);
        }, imei).stream().findFirst();
        if (apunte.isEmpty()) return cancelacion;
        if (cancelacion.isEmpty()) return apunte;
        return cancelacion.get().fecha().isAfter(apunte.get().fecha()) ? cancelacion : apunte;
    }

    public List<String> ultimosDevueltos(String imei) {
        return jdbc.queryForList(SQL_ULTIMOS_DEVUELTOS, String.class, imei, imei);
    }

    static Apunte apunte(ResultSet rs, int n) throws SQLException {
        return new Apunte(rs.getInt("ID_FILA"), rs.getTimestamp("FECHA").toLocalDateTime(), rs.getString("IMEI_ANTERIOR"),
                rs.getString("IMEI_NUEVO"), rs.getString("TIPO"), rs.getString("DETALLE"), rs.getString("NOMBRE_USUARIO"));
    }

    private static LocalDateTime fecha(ResultSet rs, String columna) throws SQLException {
        Timestamp ts = rs.getTimestamp(columna);
        return ts == null ? null : ts.toLocalDateTime();
    }

    private static String marcas(int n) {
        return String.join(", ", Collections.nCopies(n, "?"));
    }
}
