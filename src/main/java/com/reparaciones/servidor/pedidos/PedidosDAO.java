package com.reparaciones.servidor.pedidos;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/** Pedido, Pedido_unidad y Pedido_lectura (spec pieza 2 §5). Horas de Madrid sin zona, como el espejo de NSYS. */
@Repository
public class PedidosDAO {

    static final int TANDA = 500;
    static final int MAX_DESCONOCIDOS = 500;

    static final String SQL_IDS = "SELECT ID_PEDIDO, CANAL, NUMERO FROM Pedido WHERE NUMERO IN (%s)";
    static final String SQL_INSERTAR_PEDIDO = "INSERT INTO Pedido (CANAL, NUMERO, CLIENTE, PAIS, FECHA, FUENTE, VISTO_PRIMERO,"
            + " VISTO_ULTIMO, CERRADO_EN, MOTIVO_CIERRE) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
    static final String SQL_ACTUALIZAR_PEDIDO = "UPDATE Pedido SET CLIENTE = ?, PAIS = ?, FECHA = ?, VISTO_ULTIMO = ?,"
            + " CERRADO_EN = CASE WHEN ? IS NULL THEN NULL ELSE COALESCE(CERRADO_EN, ?) END, MOTIVO_CIERRE = ? WHERE ID_PEDIDO = ?";
    static final String SQL_GUARDAR_UNIDAD = "INSERT INTO Pedido_unidad (ID_PEDIDO, POSICION, SKU, IMEI, IMEI_FUENTE, IMEI_REF,"
            + " IMEI_TECLEADO_EN, SIN_PEDIDO_CONCRETO, OTRO_ORDER_ID, CERRADA_EN, MOTIVO_CIERRE) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
            + " ON DUPLICATE KEY UPDATE SKU = VALUES(SKU), IMEI = VALUES(IMEI), IMEI_FUENTE = VALUES(IMEI_FUENTE),"
            + " IMEI_REF = VALUES(IMEI_REF), IMEI_TECLEADO_EN = VALUES(IMEI_TECLEADO_EN),"
            + " SIN_PEDIDO_CONCRETO = VALUES(SIN_PEDIDO_CONCRETO), OTRO_ORDER_ID = VALUES(OTRO_ORDER_ID),"
            + " CERRADA_EN = IF(VALUES(MOTIVO_CIERRE) IS NULL, NULL, COALESCE(CERRADA_EN, VALUES(CERRADA_EN))),"
            + " MOTIVO_CIERRE = VALUES(MOTIVO_CIERRE)";
    /** Las posiciones pueden tener huecos (un artículo que el maestro no conoce también cuenta: arreglo de la Task 5): se borran
     *  las que no han salido en esta lectura. */
    static final String SQL_RECORTAR_UNIDADES = "DELETE FROM Pedido_unidad WHERE ID_PEDIDO = ? AND POSICION NOT IN (%s)";
    static final String SQL_BORRAR_UNIDADES = "DELETE FROM Pedido_unidad WHERE ID_PEDIDO = ?";
    static final String SQL_ABIERTOS_IDS = "SELECT ID_PEDIDO, FUENTE FROM Pedido WHERE CERRADO_EN IS NULL";
    static final String SQL_CERRAR_PEDIDO = "UPDATE Pedido SET CERRADO_EN = ?, MOTIVO_CIERRE = ? WHERE ID_PEDIDO = ?";
    static final String SQL_ABIERTOS = "SELECT ID_PEDIDO, CANAL, NUMERO, CLIENTE, PAIS, FECHA, FUENTE FROM Pedido"
            + " WHERE CERRADO_EN IS NULL ORDER BY FECHA, NUMERO";
    static final String SQL_UNIDADES = "SELECT ID_UNIDAD, ID_PEDIDO, POSICION, SKU, IMEI, IMEI_FUENTE, IMEI_TECLEADO_EN,"
            + " SIN_PEDIDO_CONCRETO, OTRO_ORDER_ID FROM Pedido_unidad WHERE CERRADA_EN IS NULL AND ID_PEDIDO IN (%s)"
            + " ORDER BY ID_PEDIDO, POSICION";
    static final String SQL_UNIDADES_DE_IMEIS = "SELECT u.IMEI, p.NUMERO, p.CANAL, u.SKU FROM Pedido_unidad u"
            + " JOIN Pedido p ON p.ID_PEDIDO = u.ID_PEDIDO WHERE p.CERRADO_EN IS NULL AND u.CERRADA_EN IS NULL AND u.IMEI IN (%s)"
            + " ORDER BY p.FECHA, p.NUMERO";
    static final String SQL_EMPEZAR_LECTURA = "INSERT INTO Pedido_lectura (FUENTE, INICIO) VALUES (?, ?)";
    static final String SQL_TERMINAR_LECTURA = "UPDATE Pedido_lectura SET FIN = ?, RESULTADO = ?, FILAS = ?, MENSAJE = ?"
            + " WHERE ID_LECTURA = ?";
    static final String SQL_DESCONOCIDOS = "UPDATE Pedido_lectura SET SKU_DESCONOCIDOS = ? WHERE ID_LECTURA = ?";
    static final String SQL_ULTIMA_LECTURA = "SELECT ID_LECTURA, FUENTE, INICIO, FIN, RESULTADO, FILAS, SKU_DESCONOCIDOS, MENSAJE"
            + " FROM Pedido_lectura WHERE FUENTE = ? AND RESULTADO IN (%s) ORDER BY ID_LECTURA DESC LIMIT 1";
    static final String SQL_ULTIMA_LECTURA_CON_FILAS = "SELECT ID_LECTURA, FUENTE, INICIO, FIN, RESULTADO, FILAS, SKU_DESCONOCIDOS, MENSAJE"
            + " FROM Pedido_lectura WHERE FUENTE = ? AND RESULTADO IN (%s) AND FILAS > 0 ORDER BY ID_LECTURA DESC LIMIT 1";
    static final String SQL_MARCAR_ERROR = "UPDATE Pedido_lectura SET RESULTADO = 'ERROR', MENSAJE = ? WHERE ID_LECTURA IN (%s)";
    static final String SQL_PODAR = "DELETE FROM Pedido_lectura WHERE INICIO < ?";

    /** Una unidad abierta con IMEI, para la línea de Asignaciones. */
    public record UnidadDeImei(String imei, String numero, String canal, String sku) {}

    private final JdbcTemplate jdbc;

    public PedidosDAO(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ---------------------------------------------------------------- lecturas

    public int empezarLectura(FuentePedidos fuente, LocalDateTime inicio) {
        KeyHolder claves = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(SQL_EMPEZAR_LECTURA, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, fuente.name());
            ps.setTimestamp(2, Timestamp.valueOf(inicio));
            return ps;
        }, claves);
        return clave(claves);
    }

    public void terminarLectura(int id, String resultado, int filas, String mensaje, LocalDateTime fin) {
        jdbc.update(SQL_TERMINAR_LECTURA, Timestamp.valueOf(fin), resultado, filas, mensaje, id);
    }

    public void anotarDesconocidos(int id, Collection<String> skus) {
        jdbc.update(SQL_DESCONOCIDOS, skus.isEmpty() ? null : resumen(List.copyOf(skus), 20), id);
    }

    /** «a, b, … y N más», cortado a {@value #MAX_DESCONOCIDOS} caracteres. */
    static String resumen(List<String> skus, int como) {
        String texto = skus.stream().limit(como).collect(Collectors.joining(", "));
        if (skus.size() > como) texto += ", … y " + (skus.size() - como) + " más";
        return texto.length() > MAX_DESCONOCIDOS ? texto.substring(0, MAX_DESCONOCIDOS) : texto;
    }

    public Optional<LecturaGuardada> ultimaLectura(FuentePedidos fuente, Set<String> resultados) {
        List<Object> args = new ArrayList<>();
        args.add(fuente.name());
        args.addAll(resultados);
        return jdbc.query(String.format(SQL_ULTIMA_LECTURA, marcas(resultados.size())), PedidosDAO::lectura, args.toArray())
                .stream().findFirst();
    }

    /** Como {@link #ultimaLectura} pero solo entre las que trajeron filas (referencia del freno del 90 %). */
    public Optional<LecturaGuardada> ultimaLecturaConFilas(FuentePedidos fuente, Set<String> resultados) {
        List<Object> args = new ArrayList<>();
        args.add(fuente.name());
        args.addAll(resultados);
        return jdbc.query(String.format(SQL_ULTIMA_LECTURA_CON_FILAS, marcas(resultados.size())), PedidosDAO::lectura, args.toArray())
                .stream().findFirst();
    }

    /** Pasa a ERROR (con mensaje) las lecturas indicadas; nada si no hay ninguna. */
    public void marcarError(Collection<Integer> idsLectura, String mensaje) {
        if (idsLectura.isEmpty()) return;
        List<Object> args = new ArrayList<>();
        args.add(mensaje);
        args.addAll(idsLectura);
        jdbc.update(String.format(SQL_MARCAR_ERROR, marcas(idsLectura.size())), args.toArray());
    }

    public void podarLecturas(LocalDateTime antesDe) {
        jdbc.update(SQL_PODAR, Timestamp.valueOf(antesDe));
    }

    // ---------------------------------------------------------------- pedidos

    /** Guarda una vuelta (spec §5 y §6.3): los pedidos nuevos se insertan, los que ya estaban se actualizan (y se reabren si
     *  estaban cerrados), sus unidades se guardan por posición y se quitan las que sobran; los abiertos que no han salido se
     *  cierran. Un pedido del Simulador con todas sus unidades cerradas se cierra. READ COMMITTED como «Revisado» (MariaDB 11.6+). */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void aplicar(List<PedidoLeido> pedidos, LocalDateTime ahora) {
        Timestamp t = Timestamp.valueOf(ahora);
        Map<String, Integer> ids = ids(pedidos);
        Set<Integer> vistos = new HashSet<>();
        List<Object[]> unidades = new ArrayList<>();
        Map<Integer, List<Integer>> posiciones = new HashMap<>();
        for (PedidoLeido p : pedidos) {
            String motivo = motivoCierre(p);
            Timestamp cierre = motivo == null ? null : t;
            Integer id = ids.get(clave(p.canal(), p.numero()));
            if (id == null) {
                id = insertar(p, t, cierre, motivo);
                ids.put(clave(p.canal(), p.numero()), id);
            } else {
                jdbc.update(SQL_ACTUALIZAR_PEDIDO, p.cliente(), p.pais(), Timestamp.valueOf(p.fecha()), t, motivo, cierre, motivo, id);
            }
            vistos.add(id);
            for (PedidoLeido.Unidad u : p.unidades()) unidades.add(argumentos(id, u, t));
            posiciones.put(id, p.unidades().stream().map(PedidoLeido.Unidad::posicion).toList());
        }
        for (List<Object[]> lote : trozos(unidades)) jdbc.batchUpdate(SQL_GUARDAR_UNIDAD, lote);
        posiciones.forEach(this::recortar);
        List<Object[]> cierres = new ArrayList<>();
        jdbc.query(SQL_ABIERTOS_IDS, rs -> {
            int id = rs.getInt("ID_PEDIDO");
            if (!vistos.contains(id))
                cierres.add(new Object[]{t, "SHIPPYPRO".equals(rs.getString("FUENTE")) ? "YA_NO_PENDIENTE" : "FUERA_DE_VENTANA", id});
        });
        if (!cierres.isEmpty()) jdbc.batchUpdate(SQL_CERRAR_PEDIDO, cierres);
    }

    /** Quita las unidades del pedido cuyas posiciones no han salido en esta lectura (todas, si no ha salido ninguna). */
    private void recortar(int idPedido, List<Integer> posiciones) {
        if (posiciones.isEmpty()) {
            jdbc.update(SQL_BORRAR_UNIDADES, idPedido);
            return;
        }
        List<Object> args = new ArrayList<>();
        args.add(idPedido);
        args.addAll(posiciones);
        jdbc.update(String.format(SQL_RECORTAR_UNIDADES, marcas(posiciones.size())), args.toArray());
    }

    static Object[] argumentos(int idPedido, PedidoLeido.Unidad u, Timestamp ahora) {
        return new Object[]{idPedido, u.posicion(), u.sku(), u.imei(), u.imeiFuente() == null ? null : u.imeiFuente().name(),
                u.imeiRef(), u.imeiTecleadoEn() == null ? null : Timestamp.valueOf(u.imeiTecleadoEn()), u.sinPedidoConcreto(),
                u.otroOrderId(), u.motivoCierre() == null ? null : ahora, u.motivoCierre()};
    }

    /** TODO_VENDIDO o ABONADO si es del Simulador y todas sus unidades están cerradas (ABONADO si todas por abono). */
    static String motivoCierre(PedidoLeido p) {
        if (p.fuente() != FuentePedidos.SIMULADOR || p.unidades().isEmpty()) return null;
        if (p.unidades().stream().anyMatch(u -> u.motivoCierre() == null)) return null;
        return p.unidades().stream().allMatch(u -> "ABONO".equals(u.motivoCierre())) ? "ABONADO" : "TODO_VENDIDO";
    }

    private Integer insertar(PedidoLeido p, Timestamp ahora, Timestamp cierre, String motivo) {
        KeyHolder claves = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(SQL_INSERTAR_PEDIDO, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, p.canal().name());
            ps.setString(2, p.numero());
            ps.setString(3, p.cliente());
            ps.setString(4, p.pais());
            ps.setTimestamp(5, Timestamp.valueOf(p.fecha()));
            ps.setString(6, p.fuente().name());
            ps.setTimestamp(7, ahora);
            ps.setTimestamp(8, ahora);
            ps.setTimestamp(9, cierre);
            ps.setString(10, motivo);
            return ps;
        }, claves);
        return clave(claves);
    }

    private Map<String, Integer> ids(List<PedidoLeido> pedidos) {
        Map<String, Integer> ids = new HashMap<>();
        List<String> numeros = pedidos.stream().map(PedidoLeido::numero).distinct().toList();
        for (List<String> tanda : trozos(numeros))
            jdbc.query(String.format(SQL_IDS, marcas(tanda.size())),
                    rs -> { ids.put(rs.getString("CANAL") + "|" + rs.getString("NUMERO"), rs.getInt("ID_PEDIDO")); }, tanda.toArray());
        return ids;
    }

    public List<PedidoGuardado> abiertos() {
        return jdbc.query(SQL_ABIERTOS, (rs, n) -> new PedidoGuardado(rs.getInt("ID_PEDIDO"), Canal.valueOf(rs.getString("CANAL")),
                rs.getString("NUMERO"), rs.getString("CLIENTE"), rs.getString("PAIS"), rs.getTimestamp("FECHA").toLocalDateTime(),
                FuentePedidos.valueOf(rs.getString("FUENTE"))));
    }

    public List<UnidadGuardada> unidades(Collection<Integer> idPedidos) {
        List<UnidadGuardada> unidades = new ArrayList<>();
        for (List<Integer> tanda : trozos(List.copyOf(idPedidos)))
            unidades.addAll(jdbc.query(String.format(SQL_UNIDADES, marcas(tanda.size())), (rs, n) -> new UnidadGuardada(
                    rs.getInt("ID_UNIDAD"), rs.getInt("ID_PEDIDO"), rs.getInt("POSICION"), rs.getString("SKU"), rs.getString("IMEI"),
                    rs.getString("IMEI_FUENTE"), hora(rs, "IMEI_TECLEADO_EN"), rs.getBoolean("SIN_PEDIDO_CONCRETO"),
                    rs.getString("OTRO_ORDER_ID")), tanda.toArray()));
        return unidades;
    }

    public List<UnidadDeImei> unidadesAbiertasDeImeis(Collection<String> imeis) {
        List<UnidadDeImei> unidades = new ArrayList<>();
        for (List<String> tanda : trozos(List.copyOf(imeis)))
            unidades.addAll(jdbc.query(String.format(SQL_UNIDADES_DE_IMEIS, marcas(tanda.size())), (rs, n) -> new UnidadDeImei(
                    rs.getString("IMEI"), rs.getString("NUMERO"), rs.getString("CANAL"), rs.getString("SKU")), tanda.toArray()));
        return unidades;
    }

    // ---------------------------------------------------------------- ayudas

    private static LecturaGuardada lectura(ResultSet rs, int n) throws SQLException {
        return new LecturaGuardada(rs.getInt("ID_LECTURA"), FuentePedidos.valueOf(rs.getString("FUENTE")), hora(rs, "INICIO"),
                hora(rs, "FIN"), rs.getString("RESULTADO"), rs.getInt("FILAS"), rs.getString("SKU_DESCONOCIDOS"), rs.getString("MENSAJE"));
    }

    private static LocalDateTime hora(ResultSet rs, String columna) throws SQLException {
        Timestamp t = rs.getTimestamp(columna);
        return t == null ? null : t.toLocalDateTime();
    }

    private static String clave(Canal canal, String numero) {
        return canal.name() + "|" + numero;
    }

    private static int clave(KeyHolder claves) {
        Number n = claves.getKey();
        if (n == null) throw new IllegalStateException("La base de datos no devolvió el id");
        return n.intValue();
    }

    static String marcas(int n) {
        return String.join(", ", java.util.Collections.nCopies(n, "?"));
    }

    static <T> List<List<T>> trozos(List<T> lista) {
        List<List<T>> trozos = new ArrayList<>();
        for (int i = 0; i < lista.size(); i += TANDA) trozos.add(lista.subList(i, Math.min(lista.size(), i + TANDA)));
        return trozos;
    }
}
