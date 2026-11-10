package com.reparaciones.servidor.pedidos;

import com.reparaciones.servidor.nsys.AnchosNsys;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/** Pedido_fila (la tabla de pedidos como la hoja), su registro de cambios de IMEI y Pedido_lectura. Horas de Madrid sin zona. */
@Repository
public class FilasDAO {

    static final int TANDA = 500;

    static final String SQL_FILAS = "SELECT ID_FILA, FECHA, PLATAFORMA, SKU, IMEI, PEDIDO, FECHA_COMPRA, ORIGEN, CANAL, POSICION, ENVIADA_EN"
            + " FROM Pedido_fila";
    static final String SQL_VIVAS_SHIPPYPRO = SQL_FILAS + " WHERE ORIGEN = 'SHIPPYPRO' AND ENVIADA_EN IS NULL ORDER BY FECHA, ID_FILA";
    static final String SQL_INSERTAR = "INSERT INTO Pedido_fila (FECHA, PLATAFORMA, SKU, IMEI, PEDIDO, FECHA_COMPRA, ORIGEN, CANAL, POSICION)"
            + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
    static final String SQL_CERRAR = "UPDATE Pedido_fila SET ENVIADA_EN = ? WHERE ENVIADA_EN IS NULL AND ID_FILA IN (%s)";
    static final String SQL_DEL_PERIODO = SQL_FILAS + " WHERE (FECHA BETWEEN ? AND ?) OR ENVIADA_EN IS NULL ORDER BY FECHA, ID_FILA";
    /** Escapa con la barra invertida: en MariaDB el literal '\\' es una sola barra. */
    static final String SQL_BUSCAR = SQL_FILAS + " WHERE PLATAFORMA LIKE ? ESCAPE '\\\\' OR SKU LIKE ? ESCAPE '\\\\'"
            + " OR IMEI LIKE ? ESCAPE '\\\\' OR PEDIDO LIKE ? ESCAPE '\\\\' ORDER BY FECHA DESC, ID_FILA DESC LIMIT ?";
    static final String SQL_POR_ID = SQL_FILAS + " WHERE ID_FILA = ?";
    static final String SQL_VIVAS_CON_IMEI = SQL_FILAS + " WHERE ENVIADA_EN IS NULL AND IMEI IN (%s) ORDER BY FECHA, ID_FILA";
    static final String SQL_IMEI_BLOQUEADO = "SELECT IMEI FROM Pedido_fila WHERE ID_FILA = ? FOR UPDATE";
    static final String SQL_PONER_IMEI = "UPDATE Pedido_fila SET IMEI = ? WHERE ID_FILA = ?";
    static final String SQL_CAMBIO = "INSERT INTO Pedido_fila_cambio (ID_FILA, ID_USU, FECHA, IMEI_ANTERIOR, IMEI_NUEVO) VALUES (?, ?, ?, ?, ?)";
    static final String SQL_EMPEZAR_LECTURA = "INSERT INTO Pedido_lectura (INICIO) VALUES (?)";
    static final String SQL_TERMINAR_LECTURA = "UPDATE Pedido_lectura SET FIN = ?, RESULTADO = ?, FILAS = ?, SKU_DESCONOCIDOS = ?, MENSAJE = ?"
            + " WHERE ID_LECTURA = ?";
    static final String SQL_ULTIMA_LECTURA = "SELECT ID_LECTURA, INICIO, FIN, RESULTADO, FILAS, SKU_DESCONOCIDOS, MENSAJE"
            + " FROM Pedido_lectura WHERE RESULTADO IN (%s) ORDER BY ID_LECTURA DESC LIMIT 1";
    static final String SQL_PODAR = "DELETE FROM Pedido_lectura WHERE INICIO < ?";

    private final JdbcTemplate jdbc;

    public FilasDAO(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ---------------------------------------------------------------- filas

    public List<FilaPedido> vivasDeShippyPro() {
        return jdbc.query(SQL_VIVAS_SHIPPYPRO, FilasDAO::fila);
    }

    /** Inserta las filas nuevas (el id del record se ignora), con los textos cortados a los anchos del DDL. */
    public void insertar(List<FilaPedido> nuevas) {
        if (nuevas.isEmpty()) return;
        List<Object[]> lote = new ArrayList<>();
        for (FilaPedido f : nuevas)
            lote.add(new Object[]{fecha(f.fecha()), AnchosNsys.corta(f.plataforma(), AnchosPedidos.PLATAFORMA),
                    AnchosNsys.corta(f.sku(), AnchosPedidos.SKU), AnchosNsys.corta(f.imei(), AnchosPedidos.IMEI),
                    AnchosNsys.corta(f.pedido(), AnchosPedidos.PEDIDO), fecha(f.fechaCompra()), f.origen(),
                    f.canal() == null ? null : f.canal().name(), f.posicion()});
        jdbc.batchUpdate(SQL_INSERTAR, lote);
    }

    /** Marca como enviadas (ya no vivas) las filas indicadas; las que ya lo estaban no se tocan. */
    public void cerrar(Collection<Integer> ids, LocalDateTime ahora) {
        for (List<Integer> tanda : trozos(List.copyOf(ids))) {
            List<Object> args = new ArrayList<>();
            args.add(Timestamp.valueOf(ahora));
            args.addAll(tanda);
            jdbc.update(String.format(SQL_CERRAR, marcas(tanda.size())), args.toArray());
        }
    }

    /** Las filas con FECHA entre las dos fechas y, sea cual sea su fecha, las vivas; en orden de hoja. */
    public List<FilaPedido> delPeriodo(LocalDate desde, LocalDate hasta) {
        return jdbc.query(SQL_DEL_PERIODO, FilasDAO::fila, fecha(desde), fecha(hasta));
    }

    /** Las {@code tope + 1} filas mas recientes que contienen el texto en plataforma, SKU, IMEI o pedido, en orden de hoja
     *  (la mas antigua primero). Pedir una de mas permite saber si hay mas de {@code tope}. */
    public List<FilaPedido> buscar(String texto, int tope) {
        String patron = "%" + texto.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
        List<FilaPedido> filas = new ArrayList<>(
                jdbc.query(SQL_BUSCAR, FilasDAO::fila, patron, patron, patron, patron, tope + 1));
        Collections.reverse(filas);
        return filas;
    }

    public Optional<FilaPedido> porId(int id) {
        return jdbc.query(SQL_POR_ID, FilasDAO::fila, id).stream().findFirst();
    }

    public List<FilaPedido> vivasConImei(Collection<String> imeis) {
        List<FilaPedido> filas = new ArrayList<>();
        for (List<String> tanda : trozos(List.copyOf(imeis)))
            filas.addAll(jdbc.query(String.format(SQL_VIVAS_CON_IMEI, marcas(tanda.size())), FilasDAO::fila, tanda.toArray()));
        return filas;
    }

    /** Cambia el IMEI de una fila y apunta el cambio con el IMEI anterior. READ COMMITTED y la fila bloqueada al leer el anterior
     *  (MariaDB 11.6+: leer y luego actualizar con aislamiento de instantanea da el error 1020). Si la fila no existe, no hace nada. */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void ponerImei(int idFila, String imei, Integer idUsu, LocalDateTime ahora) {
        List<String> anterior = jdbc.query(SQL_IMEI_BLOQUEADO, (rs, n) -> rs.getString("IMEI"), idFila);
        if (anterior.isEmpty()) return;
        String nuevo = AnchosNsys.corta(imei, AnchosPedidos.IMEI);
        jdbc.update(SQL_PONER_IMEI, nuevo, idFila);
        jdbc.update(SQL_CAMBIO, idFila, idUsu, Timestamp.valueOf(ahora), anterior.get(0), nuevo);
    }

    // ---------------------------------------------------------------- lecturas

    public int empezarLectura(LocalDateTime inicio) {
        KeyHolder claves = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(SQL_EMPEZAR_LECTURA, Statement.RETURN_GENERATED_KEYS);
            ps.setTimestamp(1, Timestamp.valueOf(inicio));
            return ps;
        }, claves);
        Number n = claves.getKey();
        if (n == null) throw new IllegalStateException("La base de datos no devolvio el id");
        return n.intValue();
    }

    /** Cierra la lectura. Los SKU desconocidos van ordenados y separados por «, », cortados a su ancho; nulo si no hay. */
    public void terminarLectura(int id, String resultado, int filas, Collection<String> desconocidos, String mensaje, LocalDateTime fin) {
        String skus = desconocidos.isEmpty() ? null
                : AnchosNsys.corta(String.join(", ", new TreeSet<>(desconocidos)), AnchosPedidos.SKU_DESCONOCIDOS);
        jdbc.update(SQL_TERMINAR_LECTURA, Timestamp.valueOf(fin), resultado, filas, skus,
                AnchosNsys.corta(mensaje, AnchosPedidos.MENSAJE), id);
    }

    public Optional<LecturaGuardada> ultimaLectura(Set<String> resultados) {
        return jdbc.query(String.format(SQL_ULTIMA_LECTURA, marcas(resultados.size())), FilasDAO::lectura, resultados.toArray())
                .stream().findFirst();
    }

    public void podarLecturas(LocalDateTime antesDe) {
        jdbc.update(SQL_PODAR, Timestamp.valueOf(antesDe));
    }

    // ---------------------------------------------------------------- ayudas

    private static FilaPedido fila(ResultSet rs, int n) throws SQLException {
        String canal = rs.getString("CANAL");
        int posicion = rs.getInt("POSICION");
        Integer pos = rs.wasNull() ? null : posicion;
        return new FilaPedido(rs.getInt("ID_FILA"), dia(rs, "FECHA"), rs.getString("PLATAFORMA"), rs.getString("SKU"),
                rs.getString("IMEI"), rs.getString("PEDIDO"), dia(rs, "FECHA_COMPRA"), rs.getString("ORIGEN"),
                canal == null ? null : Canal.valueOf(canal), pos, hora(rs, "ENVIADA_EN"));
    }

    private static LecturaGuardada lectura(ResultSet rs, int n) throws SQLException {
        return new LecturaGuardada(rs.getInt("ID_LECTURA"), hora(rs, "INICIO"), hora(rs, "FIN"), rs.getString("RESULTADO"),
                rs.getInt("FILAS"), rs.getString("SKU_DESCONOCIDOS"), rs.getString("MENSAJE"));
    }

    private static LocalDate dia(ResultSet rs, String columna) throws SQLException {
        Date d = rs.getDate(columna);
        return d == null ? null : d.toLocalDate();
    }

    private static LocalDateTime hora(ResultSet rs, String columna) throws SQLException {
        Timestamp t = rs.getTimestamp(columna);
        return t == null ? null : t.toLocalDateTime();
    }

    private static Date fecha(LocalDate d) {
        return d == null ? null : Date.valueOf(d);
    }

    static String marcas(int n) {
        return String.join(", ", Collections.nCopies(n, "?"));
    }

    static <T> List<List<T>> trozos(List<T> lista) {
        List<List<T>> trozos = new ArrayList<>();
        for (int i = 0; i < lista.size(); i += TANDA) trozos.add(lista.subList(i, Math.min(lista.size(), i + TANDA)));
        return trozos;
    }
}
