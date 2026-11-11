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

    static final String SQL_FILAS = "SELECT ID_FILA, FECHA, PLATAFORMA, SKU, IMEI, PEDIDO, FECHA_COMPRA, ORIGEN, CANAL, POSICION, CERRADA_EN,"
            + " ENVIO_FECHA, TRANSPORTISTA, SEGUIMIENTO, REABIERTA_EN, ENVIO_ID, IMPRESA_EN FROM Pedido_fila";
    static final String SQL_VIVAS_SHIPPYPRO = SQL_FILAS + " WHERE ORIGEN = 'SHIPPYPRO' AND CERRADA_EN IS NULL ORDER BY FECHA, ID_FILA";
    static final String SQL_CERRADAS_SHIPPYPRO = SQL_FILAS + " WHERE ORIGEN = 'SHIPPYPRO' AND CERRADA_EN IS NOT NULL AND (%s)";
    static final String SQL_POR_MARCAR_SHIPPYPRO = SQL_FILAS + " WHERE ORIGEN = 'SHIPPYPRO'"
            + " AND (ENVIO_FECHA IS NULL OR IMPRESA_EN IS NULL OR ENVIO_ID IS NULL) AND (%s)";
    static final String SQL_INSERTAR = "INSERT INTO Pedido_fila (FECHA, PLATAFORMA, SKU, IMEI, PEDIDO, FECHA_COMPRA, ORIGEN, CANAL, POSICION)"
            + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
    static final String SQL_CERRAR = "UPDATE Pedido_fila SET CERRADA_EN = ? WHERE CERRADA_EN IS NULL AND ID_FILA IN (%s)";
    /** Reabrir borra tambien los datos del envio: un pedido que vuelve a estar pendiente ya no esta enviado. */
    static final String SQL_REABRIR = "UPDATE Pedido_fila SET CERRADA_EN = NULL, ENVIO_FECHA = NULL, TRANSPORTISTA = NULL,"
            + " SEGUIMIENTO = NULL, ENVIO_ID = NULL, IMPRESA_EN = NULL, REABIERTA_EN = ? WHERE CERRADA_EN IS NOT NULL AND ID_FILA IN (%s)";
    /** Un envio es definitivo: si la fila estaba viva se cierra ahora; si ya estaba cerrada conserva su hora de cierre. */
    static final String SQL_MARCAR_ENVIO = "UPDATE Pedido_fila SET ENVIO_FECHA = ?, TRANSPORTISTA = ?, SEGUIMIENTO = ?,"
            + " CERRADA_EN = COALESCE(CERRADA_EN, ?), ENVIO_ID = ?, IMPRESA_EN = ? WHERE ID_FILA = ? AND ENVIO_FECHA IS NULL";
    /** Completa el id del envio y la impresion de una fila ya enviada; lo que ya tiene no se toca ni se borra. */
    static final String SQL_MARCAR_ETIQUETA = "UPDATE Pedido_fila SET ENVIO_ID = COALESCE(ENVIO_ID, ?),"
            + " IMPRESA_EN = COALESCE(IMPRESA_EN, ?) WHERE ID_FILA = ? AND ENVIO_FECHA IS NOT NULL";
    static final String SQL_DEL_PERIODO = SQL_FILAS + " WHERE (FECHA BETWEEN ? AND ?) OR CERRADA_EN IS NULL ORDER BY FECHA, ID_FILA";
    /** Escapa con la barra invertida: en MariaDB el literal '\\' es una sola barra. */
    static final String SQL_BUSCAR = SQL_FILAS + " WHERE PLATAFORMA LIKE ? ESCAPE '\\\\' OR SKU LIKE ? ESCAPE '\\\\'"
            + " OR IMEI LIKE ? ESCAPE '\\\\' OR PEDIDO LIKE ? ESCAPE '\\\\' ORDER BY FECHA DESC, ID_FILA DESC LIMIT ?";
    static final String SQL_POR_ID = SQL_FILAS + " WHERE ID_FILA = ?";
    /** Filas de ShippyPro con IMEI: las vivas (de cualquier fecha) y las cerradas con FECHA desde la fecha dada. */
    static final String SQL_SHIPPYPRO_CON_IMEI_DESDE = SQL_FILAS + " WHERE ORIGEN = 'SHIPPYPRO' AND IMEI IS NOT NULL AND IMEI <> ''"
            + " AND (CERRADA_EN IS NULL OR FECHA >= ?)";
    static final String SQL_SHIPPYPRO_CON_IMEI = SQL_SHIPPYPRO_CON_IMEI_DESDE + " AND IMEI IN (%s) ORDER BY FECHA, ID_FILA";
    static final String SQL_FILA_BLOQUEADA = "SELECT IMEI, ORIGEN FROM Pedido_fila WHERE ID_FILA = ? FOR UPDATE";
    static final String SQL_PONER_IMEI = "UPDATE Pedido_fila SET IMEI = ? WHERE ID_FILA = ?";
    static final String SQL_CAMBIO = "INSERT INTO Pedido_fila_cambio (ID_FILA, ID_USU, FECHA, IMEI_ANTERIOR, IMEI_NUEVO) VALUES (?, ?, ?, ?, ?)";
    /** Tope defensivo de apuntes que sirve {@link #cambiosDeFila}. */
    static final int TOPE_CAMBIOS = 200;
    static final String SQL_CAMBIOS_DE_FILA = "SELECT u.NOMBRE_USUARIO, c.FECHA, c.IMEI_ANTERIOR, c.IMEI_NUEVO FROM Pedido_fila_cambio c"
            + " LEFT JOIN Usuario u ON u.ID_USU = c.ID_USU WHERE c.ID_FILA = ? ORDER BY c.FECHA DESC, c.ID_CAMBIO DESC LIMIT " + TOPE_CAMBIOS;
    /** Los vendidos para el bot: filas de ShippyPro pendientes o enviadas (no las cerradas sin envio), con IMEI de 15 cifras, cuyo ULTIMO
     *  apunte de cambio de IMEI es de {@code ?} en adelante; del mas reciente al mas antiguo. Una sola consulta, con tope. */
    static final String SQL_VENDIDOS = "SELECT f.IMEI, f.SKU, f.CANAL, f.PEDIDO, u.FECHA_IMEI FROM Pedido_fila f"
            // El filtro dentro de la subconsulta da lo mismo que HAVING MAX(FECHA) >= ?: entra si algun apunte es de la ventana y el MAX de los de la ventana es el MAX de todos.
            + " JOIN (SELECT ID_FILA, MAX(FECHA) AS FECHA_IMEI FROM Pedido_fila_cambio WHERE FECHA >= ? GROUP BY ID_FILA) u"
            + " ON u.ID_FILA = f.ID_FILA"
            + " WHERE f.ORIGEN = 'SHIPPYPRO' AND (f.CERRADA_EN IS NULL OR f.ENVIO_FECHA IS NOT NULL)"
            + " AND (? IS NULL OR f.CANAL = ?)"
            + " AND f.IMEI REGEXP '^[0-9]{15}$' ORDER BY u.FECHA_IMEI DESC, f.ID_FILA DESC LIMIT ?";
    static final String SQL_BORRAR_HISTORICO = "DELETE FROM Pedido_fila WHERE ORIGEN = 'HISTORICO'";
    static final String SQL_INSERTAR_HISTORICO = "INSERT INTO Pedido_fila (FECHA, PLATAFORMA, SKU, IMEI, PEDIDO, FECHA_COMPRA, ORIGEN,"
            + " CANAL, POSICION, CERRADA_EN) VALUES (?, ?, ?, ?, ?, ?, 'HISTORICO', NULL, NULL, ?)";
    static final String SQL_SHIPPYPRO_BLOQUEADAS = SQL_FILAS + " WHERE ORIGEN = 'SHIPPYPRO' ORDER BY FECHA, ID_FILA FOR UPDATE";
    static final String SQL_TRASPASAR_IMEI = "UPDATE Pedido_fila SET IMEI = ? WHERE ID_FILA = ? AND ORIGEN = 'SHIPPYPRO'"
            + " AND (IMEI IS NULL OR IMEI = '')";
    static final String SQL_CAMBIOS_DE_HISTORICO = "SELECT COUNT(*) FROM Pedido_fila_cambio c JOIN Pedido_fila f ON f.ID_FILA = c.ID_FILA"
            + " WHERE f.ORIGEN = 'HISTORICO'";
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

    /** Las filas cerradas de origen ShippyPro de esos pedidos (canal y numero), en tandas. */
    public List<FilaPedido> cerradasDeShippyPro(Collection<SincroShippyPro.ClavePedido> pedidos) {
        return deShippyPro(SQL_CERRADAS_SHIPPYPRO, pedidos);
    }

    private List<FilaPedido> deShippyPro(String sql, Collection<SincroShippyPro.ClavePedido> pedidos) {
        List<FilaPedido> filas = new ArrayList<>();
        for (List<SincroShippyPro.ClavePedido> tanda : trozos(List.copyOf(pedidos))) {
            List<Object> args = new ArrayList<>();
            for (SincroShippyPro.ClavePedido c : tanda) {
                args.add(c.canal().name());
                args.add(c.pedido());
            }
            String condicion = String.join(" OR ", Collections.nCopies(tanda.size(), "(CANAL = ? AND PEDIDO = ?)"));
            filas.addAll(jdbc.query(String.format(sql, condicion), FilasDAO::fila, args.toArray()));
        }
        return filas;
    }

    /** Las filas de origen ShippyPro (vivas o cerradas) de esos pedidos (canal y numero) a las que aun puede faltar algo del envio:
     *  sin envio conocido, sin id de envio o sin impresion de la etiqueta; en tandas. */
    public List<FilaPedido> porMarcarDeShippyPro(Collection<SincroShippyPro.ClavePedido> pedidos) {
        return deShippyPro(SQL_POR_MARCAR_SHIPPYPRO, pedidos);
    }

    /** Pone a cada fila los datos de su envio y, si estaba viva, la cierra ahora; las que ya tenian envio no se tocan. */
    public void marcarEnviadas(List<SincroShippyPro.MarcaEnvio> marcas, LocalDateTime ahora) {
        if (marcas.isEmpty()) return;
        List<Object[]> lote = new ArrayList<>();
        for (SincroShippyPro.MarcaEnvio m : marcas)
            lote.add(new Object[]{Timestamp.valueOf(m.fecha()), AnchosNsys.corta(m.transportista(), AnchosPedidos.TRANSPORTISTA),
                    AnchosNsys.corta(m.seguimiento(), AnchosPedidos.SEGUIMIENTO), Timestamp.valueOf(ahora), m.envioId(),
                    m.impresaEn() == null ? null : Timestamp.valueOf(m.impresaEn()), m.fila()});
        jdbc.batchUpdate(SQL_MARCAR_ENVIO, lote);
    }

    /** Completa el id del envio y la impresion de las filas ya enviadas; nunca borra lo ya puesto. */
    public void marcarEtiquetas(List<SincroShippyPro.MarcaEtiqueta> marcas) {
        if (marcas.isEmpty()) return;
        List<Object[]> lote = new ArrayList<>();
        for (SincroShippyPro.MarcaEtiqueta m : marcas)
            lote.add(new Object[]{m.envioId(), m.impresaEn() == null ? null : Timestamp.valueOf(m.impresaEn()), m.fila()});
        jdbc.batchUpdate(SQL_MARCAR_ETIQUETA, lote);
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

    /** Cierra (ya no pendientes) las filas indicadas; las que ya lo estaban no se tocan. */
    public void cerrar(Collection<Integer> ids, LocalDateTime ahora) {
        for (List<Integer> tanda : trozos(List.copyOf(ids))) {
            List<Object> args = new ArrayList<>();
            args.add(Timestamp.valueOf(ahora));
            args.addAll(tanda);
            jdbc.update(String.format(SQL_CERRAR, marcas(tanda.size())), args.toArray());
        }
    }

    /** Vuelve a dejar vivas (sin hora de cierre ni datos de envio, y apunta cuando se reabren) las filas indicadas, con su IMEI intacto; las que ya lo estaban no se tocan. */
    public void reabrir(Collection<Integer> ids, LocalDateTime ahora) {
        for (List<Integer> tanda : trozos(List.copyOf(ids))) {
            List<Object> args = new ArrayList<>();
            args.add(Timestamp.valueOf(ahora));
            args.addAll(tanda);
            jdbc.update(String.format(SQL_REABRIR, marcas(tanda.size())), args.toArray());
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

    /** Los apuntes de IMEI de una fila, del mas reciente al mas antiguo (hasta 200), con el nombre de quien los hizo (nulo si no hay usuario). */
    public List<CambioImei> cambiosDeFila(int id) {
        return jdbc.query(SQL_CAMBIOS_DE_FILA, (rs, n) -> new CambioImei(rs.getString("NOMBRE_USUARIO"),
                rs.getTimestamp("FECHA").toLocalDateTime(), rs.getString("IMEI_ANTERIOR"), rs.getString("IMEI_NUEVO")), id);
    }

    /** Las filas de ShippyPro con alguno de esos IMEI: las vivas y las cerradas con FECHA desde {@code desde}. */
    public List<FilaPedido> shippyProConImei(Collection<String> imeis, LocalDate desde) {
        List<FilaPedido> filas = new ArrayList<>();
        for (List<String> tanda : trozos(List.copyOf(imeis))) {
            List<Object> args = new ArrayList<>();
            args.add(fecha(desde));
            args.addAll(tanda);
            filas.addAll(jdbc.query(String.format(SQL_SHIPPYPRO_CON_IMEI, marcas(tanda.size())), FilasDAO::fila, args.toArray()));
        }
        return filas;
    }

    /** Todas las filas de ShippyPro con IMEI: las vivas y las cerradas con FECHA desde {@code desde}. */
    public List<FilaPedido> shippyProConImeiDesde(LocalDate desde) {
        return jdbc.query(SQL_SHIPPYPRO_CON_IMEI_DESDE + " ORDER BY FECHA, ID_FILA", FilasDAO::fila, fecha(desde));
    }

    /** Los IMEI vendidos (ver {@link #SQL_VENDIDOS}) con ultimo cambio de IMEI desde {@code desde}, a lo sumo {@code tope} filas, sin
     *  IMEI repetidos: si esta en dos filas sale la del cambio mas reciente. Un IMEI quitado de la fila no sale (la fila ya no lo tiene). */
    public List<Vendido> vendidosDesde(LocalDateTime desde, Canal canal, int tope) {
        String nombreCanal = canal == null ? null : canal.name();
        // El canal filtra en la consulta; el dedup por IMEI va despues, sobre lo ya filtrado.
        List<Vendido> filas = jdbc.query(SQL_VENDIDOS, (rs, n) -> new Vendido(rs.getString("IMEI"), rs.getString("SKU"),
                rs.getString("CANAL"), rs.getString("PEDIDO"), hora(rs, "FECHA_IMEI")),
                Timestamp.valueOf(desde), nombreCanal, nombreCanal, tope);
        java.util.Map<String, Vendido> porImei = new java.util.LinkedHashMap<>();
        for (Vendido v : filas) porImei.putIfAbsent(v.imei(), v);
        return new ArrayList<>(porImei.values());
    }

    /** Resultado de {@link #ponerImei}: PUESTO, NO_EXISTE o CERRADA (del historico: no se toca). */
    public enum Resultado { PUESTO, NO_EXISTE, CERRADA }

    private record Bloqueada(String imei, boolean historico) {}

    /** Cambia el IMEI de una fila de ShippyPro (pendiente, no pendiente o enviada) y apunta el cambio con el IMEI anterior. READ COMMITTED y la fila bloqueada al leerla
     *  (MariaDB 11.6+: leer y luego actualizar con aislamiento de instantanea da el error 1020); el estado de la fila se decide
     *  con el bloqueo puesto. Si no existe o es del historico, no escribe nada. */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Resultado ponerImei(int idFila, String imei, Integer idUsu, LocalDateTime ahora) {
        List<Bloqueada> filas = jdbc.query(SQL_FILA_BLOQUEADA,
                (rs, n) -> new Bloqueada(rs.getString("IMEI"), "HISTORICO".equals(rs.getString("ORIGEN"))), idFila);
        if (filas.isEmpty()) return Resultado.NO_EXISTE;
        if (filas.get(0).historico()) return Resultado.CERRADA;
        String nuevo = AnchosNsys.corta(imei, AnchosPedidos.IMEI);
        jdbc.update(SQL_PONER_IMEI, nuevo, idFila);
        jdbc.update(SQL_CAMBIO, idFila, idUsu, Timestamp.valueOf(ahora), filas.get(0).imei(), nuevo);
        return Resultado.PUESTO;
    }

    // ---------------------------------------------------------------- carga del historico (se llaman dentro de una transaccion)

    /** Todas las filas de origen ShippyPro, vivas o cerradas, en orden de hoja y bloqueadas hasta el fin de la transaccion. */
    public List<FilaPedido> todasDeShippyProBloqueadas() {
        return jdbc.query(SQL_SHIPPYPRO_BLOQUEADAS, FilasDAO::fila);
    }

    /** Cuantos apuntes de Pedido_fila_cambio cuelgan de filas HISTORICO (deberian ser 0: borrar esas filas los borraria en cascada). */
    public int contarCambiosDeHistorico() {
        Integer n = jdbc.queryForObject(SQL_CAMBIOS_DE_HISTORICO, Integer.class);
        return n == null ? 0 : n;
    }

    /** Borra las filas de origen HISTORICO y devuelve cuantas. No toca las de ShippyPro (ni Pedido_fila_cambio: el historico no se edita). */
    public int borrarHistorico() {
        return jdbc.update(SQL_BORRAR_HISTORICO);
    }

    /** Inserta las filas del historico en el orden dado y en lotes de {@value #TANDA}. Origen HISTORICO, sin canal ni posicion, y
     *  cerradas a la medianoche de su fecha; los textos se cortan a los anchos del DDL. */
    public void insertarHistorico(List<FilaPedido> filas) {
        for (List<FilaPedido> tanda : trozos(filas)) {
            List<Object[]> lote = new ArrayList<>();
            for (FilaPedido f : tanda)
                lote.add(new Object[]{fecha(f.fecha()), AnchosNsys.corta(f.plataforma(), AnchosPedidos.PLATAFORMA),
                        AnchosNsys.corta(f.sku(), AnchosPedidos.SKU), AnchosNsys.corta(f.imei(), AnchosPedidos.IMEI),
                        AnchosNsys.corta(f.pedido(), AnchosPedidos.PEDIDO), fecha(f.fechaCompra()),
                        Timestamp.valueOf(f.fecha().atStartOfDay())});
            jdbc.batchUpdate(SQL_INSERTAR_HISTORICO, lote);
        }
    }

    /** Pone el IMEI en una fila de ShippyPro que no lo tiene y lo apunta (sin usuario, IMEI anterior nulo). Falso, sin escribir nada
     *  mas, si la fila ya tenia IMEI o no existe. */
    public boolean traspasarImei(int idFila, String imei, LocalDateTime ahora) {
        String nuevo = AnchosNsys.corta(imei, AnchosPedidos.IMEI);
        if (jdbc.update(SQL_TRASPASAR_IMEI, nuevo, idFila) == 0) return false;
        jdbc.update(SQL_CAMBIO, idFila, null, Timestamp.valueOf(ahora), null, nuevo);
        return true;
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
        if (resultados.isEmpty()) return Optional.empty();
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
        long idEnvio = rs.getLong("ENVIO_ID");
        Long envioId = rs.wasNull() ? null : idEnvio;
        return new FilaPedido(rs.getInt("ID_FILA"), dia(rs, "FECHA"), rs.getString("PLATAFORMA"), rs.getString("SKU"),
                rs.getString("IMEI"), rs.getString("PEDIDO"), dia(rs, "FECHA_COMPRA"), rs.getString("ORIGEN"),
                canal == null ? null : Canal.valueOf(canal), pos, hora(rs, "CERRADA_EN"), hora(rs, "ENVIO_FECHA"),
                rs.getString("TRANSPORTISTA"), rs.getString("SEGUIMIENTO"), hora(rs, "REABIERTA_EN"), envioId, hora(rs, "IMPRESA_EN"));
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
