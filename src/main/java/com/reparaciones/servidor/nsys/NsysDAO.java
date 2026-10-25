package com.reparaciones.servidor.nsys;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/** Acceso a las tablas del espejo de NSYS (spec pieza 1 §4). Las horas del espejo son de Madrid; las del taller
 *  (`Reparacion`) están en UTC, como el resto de la BD, y se pasan a Madrid al leerlas. */
@Repository
public class NsysDAO {

    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");
    static final int LOTE = 1000;
    static final String COLUMNAS_MOVIL = "IMEI_SN, BATCH, GOODS, GRADE, STATUS, LOCATION, SUPPLIER, RECEIVING_DATE,"
            + " SOLD_DATE, SKU, MODELO, MODELO_MAESTRO, CAPACIDAD, COLOR, GRADO_DETALLE, ES_ESIM, UBICACION,"
            + " UBICACION_ULTIMA, ESTADO, VISTO_PRIMERO, CAMBIADO";
    static final String SQL_GUARDAR_MOVIL = "INSERT INTO Nsys_movil (" + COLUMNAS_MOVIL + ") VALUES"
            + " (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
            + " ON DUPLICATE KEY UPDATE GOODS = VALUES(GOODS), GRADE = VALUES(GRADE), STATUS = VALUES(STATUS),"
            + " LOCATION = VALUES(LOCATION), SUPPLIER = VALUES(SUPPLIER), RECEIVING_DATE = VALUES(RECEIVING_DATE),"
            + " SOLD_DATE = VALUES(SOLD_DATE), SKU = VALUES(SKU), MODELO = VALUES(MODELO),"
            + " MODELO_MAESTRO = VALUES(MODELO_MAESTRO), CAPACIDAD = VALUES(CAPACIDAD), COLOR = VALUES(COLOR),"
            + " GRADO_DETALLE = VALUES(GRADO_DETALLE), ES_ESIM = VALUES(ES_ESIM), UBICACION = VALUES(UBICACION),"
            + " UBICACION_ULTIMA = VALUES(UBICACION_ULTIMA), ESTADO = VALUES(ESTADO), CAMBIADO = VALUES(CAMBIADO)";
    static final String SQL_EVENTO = "INSERT INTO Nsys_evento (IMEI_SN, BATCH, FECHA, SOLO_DIA, TIPO, DE, A, A_NSYS,"
            + " ID_IMPORTACION) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
    static final String SQL_PRODUCTO = "INSERT INTO Nsys_producto (CLAVE, MODELO, MODELO_MAESTRO, CAPACIDAD, COLOR,"
            + " CALIDAD_DETALLE, SKU_WEB, SKU_AMAZON, SKU_SIN_GRADO, ES_ESIM, NOMBRE) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
    static final String SQL_EMPEZAR = "INSERT INTO Nsys_importacion (FICHERO, FECHA_EXPORTACION, ORIGEN, EMPEZADA)"
            + " VALUES (?, ?, ?, ?)";
    static final String SQL_TERMINAR = "UPDATE Nsys_importacion SET RESULTADO = ?, FILAS = ?, CAMBIOS = ?, MENSAJE = ?,"
            + " TERMINADA = ? WHERE ID_IMPORTACION = ?";
    /** true si hay productos y ninguno tiene nombre: el maestro se cargó antes de la columna NOMBRE (0.9.22). */
    static final String SQL_PRODUCTOS_SIN_NOMBRE =
            "SELECT COUNT(*) > 0 AND COALESCE(SUM(NOMBRE IS NOT NULL), 0) = 0 FROM Nsys_producto";
    /** El coste guardado de cada lote (0.9.22): para escribir solo los que cambian. */
    static final String SQL_COSTES = "SELECT IMEI_SN, BATCH, COSTE FROM Nsys_movil WHERE COSTE IS NOT NULL";
    static final String SQL_GUARDAR_COSTE = "UPDATE Nsys_movil SET COSTE = ? WHERE IMEI_SN = ? AND BATCH = ?";
    /** Rellena el modelo de los teléfonos del taller que no lo tienen, sin pisar nunca uno puesto, solo para IMEI de 15 cifras que ya existen en Telefono (spec §7). */
    static final String SQL_RELLENAR_MODELOS = "UPDATE Telefono t JOIN (SELECT IMEI_SN, MAX(MODELO) AS MODELO"
            + " FROM Nsys_movil WHERE MODELO IS NOT NULL GROUP BY IMEI_SN) n ON n.IMEI_SN = t.IMEI"
            + " SET t.MODELO = n.MODELO WHERE (t.MODELO IS NULL OR t.MODELO = '') AND t.IMEI REGEXP '^[0-9]{15}$'";
    /** Los lotes de un IMEI, del más reciente al más antiguo. En la primera carga todos tienen el mismo VISTO_PRIMERO y
     *  el texto del lote no va en orden de fechas: desempata la fecha de recepción (sin fecha, al final) y luego el lote. */
    static final String LOTE_MAS_RECIENTE_PRIMERO = " ORDER BY VISTO_PRIMERO DESC, RECEIVING_DATE DESC, BATCH DESC";
    private static final String COLUMNAS_IMPORTACION ="ID_IMPORTACION, FICHERO, FECHA_EXPORTACION, RESULTADO, FILAS, MENSAJE";

    /** Las asignaciones abiertas del taller (A… reparación, AG… glass, AP… pulido; sin FECHA_FIN) con el técnico. Se
     *  cruzan con el espejo en Java por IMEI, para no depender de las intercalaciones de Nsys_movil y Reparacion
     *  (spec 0.9.18 §6.1). */
    static final String SQL_TRABAJOS_ABIERTOS = "SELECT r.IMEI, r.ID_REP, r.FECHA_ASIG, r.ENTREGADO_AT, t.NOMBRE"
            + " FROM Reparacion r JOIN Tecnico t ON t.ID_TEC = r.ID_TEC WHERE r.ID_REP LIKE 'A%' AND r.FECHA_FIN IS NULL";
    /** «No está en NSYS» (0.9.22 §3.4): las filas de Reparacion de unos IMEI con su técnico, el modelo del teléfono y el cliente del
     *  trabajo o, si no tiene, el del teléfono. Se completa con las marcas del IN y ORDEN_TRABAJOS. */
    static final String SQL_TRABAJOS_DE_IMEIS = "SELECT r.IMEI, r.ID_REP, r.FECHA_ASIG, r.FECHA_FIN, t.NOMBRE AS TECNICO, tel.MODELO,"
            + " c.NOMBRE AS CLIENTE FROM Reparacion r JOIN Tecnico t ON t.ID_TEC = r.ID_TEC JOIN Telefono tel ON tel.IMEI = r.IMEI"
            + " LEFT JOIN Cliente c ON c.ID_CLI = COALESCE(r.ID_CLI, tel.ID_CLI) WHERE r.IMEI IN (";
    static final String ORDEN_TRABAJOS = " ORDER BY r.FECHA_ASIG, r.ID_REP";
    /** Tamaño de las tandas de los IN por IMEI (spec 0.9.18 §6.2). */
    static final int TANDA_IN = 500;

    /** El «Goods» de los móviles del espejo de un modelo, capacidad y eSIM, por color y de más usado a menos (spec 0.9.21
     *  §2.4): el producto que hay que poner en NSYS al cambiar el color. `<=>`: MODELO y CAPACIDAD pueden ser NULL. */
    static final String SQL_GOODS_POR_COLOR = "SELECT COLOR, GOODS, COUNT(*) AS N FROM Nsys_movil"
            + " WHERE MODELO <=> ? AND MODELO_MAESTRO = ? AND CAPACIDAD <=> ? AND ES_ESIM = ? AND COLOR IS NOT NULL"
            + " AND GOODS IS NOT NULL GROUP BY COLOR, GOODS ORDER BY COLOR, N DESC, GOODS";

    /** Los Goods del espejo con el modelo del maestro de sus móviles y cuántos son (0.9.22 §2.1): de aquí sale cómo escribe NSYS
     *  cada color. Una consulta para todos los modelos; solo se pide si un color no tiene móviles en su modelo y capacidad. */
    static final String SQL_GOODS_CONTADOS = "SELECT MODELO_MAESTRO, GOODS, COUNT(*) AS N FROM Nsys_movil WHERE GOODS IS NOT NULL"
            + " GROUP BY MODELO_MAESTRO, GOODS";

    private final JdbcTemplate jdbc;

    public NsysDAO(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ---------------------------------------------------------------- foto, maestro y cajas

    public Map<ClaveMovil, MovilNsys> cargarMoviles() {
        Map<ClaveMovil, MovilNsys> mapa = new HashMap<>();
        for (MovilNsys m : jdbc.query("SELECT " + COLUMNAS_MOVIL + " FROM Nsys_movil", NsysDAO::movil)) mapa.put(m.clave(), m);
        return mapa;
    }

    public List<UbicacionNsys> cargarUbicaciones() {
        return jdbc.query("SELECT LOCATION, UBICACION, CUENTA_STOCK, ES_PEDIDO FROM Nsys_ubicacion ORDER BY ORDEN",
                (rs, n) -> new UbicacionNsys(rs.getString("LOCATION"), rs.getString("UBICACION"),
                        rs.getBoolean("CUENTA_STOCK"), rs.getBoolean("ES_PEDIDO")));
    }

    public List<ProductoMaestro> cargarProductos() {
        return jdbc.query("SELECT CLAVE, MODELO, MODELO_MAESTRO, CAPACIDAD, COLOR, CALIDAD_DETALLE, SKU_WEB, SKU_AMAZON,"
                        + " SKU_SIN_GRADO, ES_ESIM, NOMBRE FROM Nsys_producto",
                (rs, n) -> new ProductoMaestro(rs.getString("CLAVE"), rs.getString("MODELO"), rs.getString("MODELO_MAESTRO"),
                        rs.getString("CAPACIDAD"), rs.getString("COLOR"), rs.getString("CALIDAD_DETALLE"),
                        rs.getString("SKU_WEB"), rs.getString("SKU_AMAZON"), rs.getString("SKU_SIN_GRADO"),
                        rs.getBoolean("ES_ESIM"), rs.getString("NOMBRE")));
    }

    /** El maestro guardado no tiene ningún nombre de producto (se cargó antes de la 0.9.22). */
    public boolean productosSinNombre() {
        return Boolean.TRUE.equals(jdbc.queryForObject(SQL_PRODUCTOS_SIN_NOMBRE, Boolean.class));
    }

    @Transactional
    public void reemplazarProductos(List<ProductoMaestro> productos) {
        jdbc.update("DELETE FROM Nsys_producto");
        for (List<ProductoMaestro> lote : trozos(productos))
            jdbc.batchUpdate(SQL_PRODUCTO, lote.stream().map(p -> new Object[]{p.clave(), p.modelo(), p.modeloMaestro(),
                    p.capacidad(), p.color(), p.calidadDetalle(), p.skuWeb(), p.skuAmazon(), p.skuSinGrado(), p.esEsim(),
                    p.nombre()}).toList());
    }

    /** Guarda las filas nuevas o cambiadas y los eventos de una comparación, todo o nada. */
    @Transactional
    public void aplicar(ResultadoComparacion r, Integer idImportacion) {
        for (List<MovilNsys> lote : trozos(r.aGuardar()))
            jdbc.batchUpdate(SQL_GUARDAR_MOVIL, lote.stream().map(NsysDAO::argumentos).toList());
        insertarEventos(r.eventos(), idImportacion);
    }

    /** Una fila de {@link #SQL_COSTES}. */
    record Coste(ClaveMovil clave, BigDecimal coste) {}

    /** El coste de cada lote del espejo que lo tiene (0.9.22). */
    Map<ClaveMovil, BigDecimal> cargarCostes() {
        Map<ClaveMovil, BigDecimal> mapa = new HashMap<>();
        for (Coste c : jdbc.query(SQL_COSTES, NsysDAO::coste)) mapa.put(c.clave(), c.coste());
        return mapa;
    }

    static Coste coste(ResultSet rs, int n) throws SQLException {
        return new Coste(new ClaveMovil(rs.getString("IMEI_SN"), rs.getString("BATCH")), rs.getBigDecimal("COSTE"));
    }

    /** El coste de NSYS («Total cost») de estos lotes, para la plantilla de color (0.9.22 §4): en tandas por IMEI de
     *  {@value #TANDA_IN}; un lote sin coste no sale. */
    public Map<ClaveMovil, BigDecimal> costes(Collection<ClaveMovil> lotes) {
        Set<ClaveMovil> pedidos = new HashSet<>(lotes);
        List<String> imeis = pedidos.stream().map(ClaveMovil::imeiSn).distinct().toList();
        Map<ClaveMovil, BigDecimal> mapa = new HashMap<>();
        for (List<String> tanda : trozos(imeis, TANDA_IN))
            for (Coste c : jdbc.query(SQL_COSTES + " AND IMEI_SN IN (" + marcas(tanda.size()) + ")", NsysDAO::coste,
                    tanda.toArray()))
                if (pedidos.contains(c.clave())) mapa.put(c.clave(), c.coste());
        return mapa;
    }

    /** Los argumentos {@code {coste, imeiSn, batch}} de {@link #SQL_GUARDAR_COSTE} para las filas de un Items cuyo «Total cost»
     *  (spec 0.9.22 §2.3) cambia, en el orden del fichero; solo lee. Una fila sin coste no borra el que había, y un IMEI+lote
     *  repetido cuenta la primera fila. Se llama después de {@link #aplicar}, con los móviles nuevos ya guardados; los escribe
     *  {@link #guardarCostes} por lotes, cada uno en su transacción. */
    public List<Object[]> costesCambiados(List<FilaItems> filas) {
        Map<ClaveMovil, BigDecimal> antes = cargarCostes();
        Set<ClaveMovil> vistas = new HashSet<>();
        List<Object[]> cambios = new ArrayList<>();
        for (FilaItems f : filas) {
            if (!vistas.add(f.clave()) || f.coste() == null) continue;
            BigDecimal previo = antes.get(f.clave());
            if (previo == null || previo.compareTo(f.coste()) != 0)
                cambios.add(new Object[]{f.coste(), f.clave().imeiSn(), f.clave().batch()});
        }
        return cambios;
    }

    /** Escribe un lote de {@link #costesCambiados}: un lote, una transacción, para no dejar bloqueadas miles de filas (el
     *  guardado de una reparación bloquea su móvil) mientras dura toda la escritura. La llama el importador, lote a lote. */
    @Transactional
    public void guardarCostes(List<Object[]> lote) {
        jdbc.batchUpdate(SQL_GUARDAR_COSTE, lote);
    }

    /** Los textos de los eventos son campos de Nsys_movil o ubicaciones, ya cortados al leer (AnchosNsys): caben. */
    @Transactional
    public void insertarEventos(List<EventoNsys> eventos, Integer idImportacion) {
        for (List<EventoNsys> lote : trozos(eventos))
            jdbc.batchUpdate(SQL_EVENTO, lote.stream().map(e -> new Object[]{e.imeiSn(), e.batch(), Timestamp.valueOf(e.fecha()),
                    e.soloDia(), e.tipo().name(), e.de(), e.a(), e.aNsys(), idImportacion}).toList());
    }

    /** Cierra un fichero del histórico: inserta sus eventos y lo marca OK en una sola transacción, para que un fallo
     *  a medias no deje eventos que se repitan al reintentar. */
    @Transactional
    public void cerrarHistorico(List<EventoNsys> eventos, int idImportacion, int filas, LocalDateTime ahora) {
        insertarEventos(eventos, idImportacion);
        terminarImportacion(idImportacion, ImportadorNsys.OK, filas, eventos.size(), null, ahora);
    }

    /** Sin cortar: LectorItems y LectorMaestro ya cortan cada campo al ancho de su columna (AnchosNsys), así la foto
     *  guardada y la que se compara son la misma. */
    static Object[] argumentos(MovilNsys m) {
        return new Object[]{m.imeiSn(), m.batch(), m.goods(), m.grade(), m.status(), m.location(), m.supplier(),
                fecha(m.receivingDate()), fecha(m.soldDate()), m.sku(), m.modelo(), m.modeloMaestro(), m.capacidad(),
                m.color(), m.gradoDetalle(), m.esEsim(), m.ubicacion(), m.ubicacionUltima(), m.estado().name(),
                Timestamp.valueOf(m.vistoPrimero()), Timestamp.valueOf(m.cambiado())};
    }

    // ---------------------------------------------------------------- registro de importaciones

    public int empezarImportacion(String fichero, LocalDateTime fechaExportacion, String origen, LocalDateTime ahora) {
        KeyHolder claves = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(SQL_EMPEZAR, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, AnchosNsys.corta(fichero, AnchosNsys.FICHERO));
            ps.setTimestamp(2, Timestamp.valueOf(fechaExportacion));
            ps.setString(3, origen);
            ps.setTimestamp(4, Timestamp.valueOf(ahora));
            return ps;
        }, claves);
        return claves.getKey().intValue();
    }

    public void terminarImportacion(int id, String resultado, int filas, int cambios, String mensaje, LocalDateTime ahora) {
        jdbc.update(SQL_TERMINAR, resultado, filas, cambios, AnchosNsys.corta(mensaje, AnchosNsys.MENSAJE),
                Timestamp.valueOf(ahora), id);
    }

    public Optional<Importacion> ultimaImportacion(String origen, Set<String> resultados) {
        return unaImportacion(origen, resultados, "DESC");
    }

    public Optional<Importacion> primeraImportacion(String origen, Set<String> resultados) {
        return unaImportacion(origen, resultados, "ASC");
    }

    /** Los ficheros de un origen ya resueltos (OK o RECHAZADO): no se vuelven a procesar. */
    public Set<String> ficherosProcesados(String origen) {
        return new HashSet<>(jdbc.query("SELECT FICHERO FROM Nsys_importacion WHERE ORIGEN = ? AND RESULTADO IN ('OK', 'RECHAZADO')",
                (rs, n) -> rs.getString("FICHERO"), origen));
    }

    /** Las últimas importaciones de un origen, de la más reciente a la más antigua (spec 0.9.18 §6.5). */
    public List<RegistroImportacion> importaciones(String origen, int limite) {
        return jdbc.query("SELECT ID_IMPORTACION, FICHERO, FECHA_EXPORTACION, RESULTADO, FILAS, CAMBIOS, EMPEZADA, TERMINADA,"
                        + " MENSAJE FROM Nsys_importacion WHERE ORIGEN = ? ORDER BY FECHA_EXPORTACION DESC, ID_IMPORTACION DESC"
                        + " LIMIT ?", NsysDAO::registroImportacion, origen, limite);
    }

    static RegistroImportacion registroImportacion(ResultSet rs, int n) throws SQLException {
        return new RegistroImportacion(rs.getInt("ID_IMPORTACION"), rs.getString("FICHERO"),
                rs.getTimestamp("FECHA_EXPORTACION").toLocalDateTime(), rs.getString("RESULTADO"), rs.getInt("FILAS"),
                rs.getInt("CAMBIOS"), fechaHora(rs.getTimestamp("EMPEZADA")), fechaHora(rs.getTimestamp("TERMINADA")),
                rs.getString("MENSAJE"));
    }

    /** El histórico de un vistazo: todas sus importaciones, las hechas (OK o RECHAZADO) y la fecha de la última hecha. */
    public ResumenHistorico resumenHistorico() {
        return jdbc.queryForObject("SELECT COUNT(*) AS TOTAL, COALESCE(SUM(RESULTADO IN ('OK', 'RECHAZADO')), 0) AS HECHOS,"
                        + " MAX(CASE WHEN RESULTADO IN ('OK', 'RECHAZADO') THEN FECHA_EXPORTACION END) AS ULTIMO"
                        + " FROM Nsys_importacion WHERE ORIGEN = 'HISTORICO'",
                (rs, n) -> new ResumenHistorico(rs.getInt("TOTAL"), rs.getInt("HECHOS"), fechaHora(rs.getTimestamp("ULTIMO"))));
    }

    private Optional<Importacion> unaImportacion(String origen, Set<String> resultados, String orden) {
        List<String> estados = new ArrayList<>(new TreeSet<>(resultados));
        List<Object> argumentos = new ArrayList<>();
        argumentos.add(origen);
        argumentos.addAll(estados);
        String sql = "SELECT " + COLUMNAS_IMPORTACION + " FROM Nsys_importacion WHERE ORIGEN = ? AND RESULTADO IN ("
                + String.join(", ", Collections.nCopies(estados.size(), "?")) + ") ORDER BY FECHA_EXPORTACION " + orden
                + ", ID_IMPORTACION " + orden + " LIMIT 1";
        List<Importacion> filas = jdbc.query(sql, (rs, n) -> new Importacion(rs.getInt("ID_IMPORTACION"),
                rs.getString("FICHERO"), rs.getTimestamp("FECHA_EXPORTACION").toLocalDateTime(), rs.getString("RESULTADO"),
                rs.getInt("FILAS"), rs.getString("MENSAJE")), argumentos.toArray());
        return filas.stream().findFirst();
    }

    // ---------------------------------------------------------------- taller (IMEIcheck fuera)

    public int rellenarModelosTelefono() {
        return jdbc.update(SQL_RELLENAR_MODELOS);
    }

    /** Código de modelo del ERP según NSYS (el lote más reciente que lo tenga), o null. */
    public String modeloDe(String imei) {
        List<String> modelos = jdbc.query("SELECT MODELO FROM Nsys_movil WHERE IMEI_SN = ? AND MODELO IS NOT NULL"
                + LOTE_MAS_RECIENTE_PRIMERO + " LIMIT 1", (rs, n) -> rs.getString("MODELO"), imei);
        return modelos.isEmpty() ? null : modelos.get(0);
    }

    // ---------------------------------------------------------------- lecturas de la API

    /** Los móviles que están en la empresa: en stock, de pedido o que no cuentan como stock. */
    public List<MovilNsys> enEmpresa() {
        return jdbc.query("SELECT " + COLUMNAS_MOVIL + " FROM Nsys_movil WHERE ESTADO IN ('STOCK', 'DE_PEDIDO', 'NO_CUENTA')",
                NsysDAO::movil);
    }

    /** Móviles cuyo IMEI/SN contiene {@code texto} (solo letras y cifras), de cualquier estado, los más recientes primero. */
    public List<MovilNsys> buscar(String texto, int limite) {
        String limpio = texto == null ? "" : texto.replaceAll("[^A-Za-z0-9]", "");
        if (limpio.isEmpty()) return List.of();
        return jdbc.query("SELECT " + COLUMNAS_MOVIL + " FROM Nsys_movil WHERE IMEI_SN LIKE ? ORDER BY VISTO_PRIMERO DESC LIMIT ?",
                NsysDAO::movil, "%" + limpio + "%", limite);
    }

    /** Todos los lotes de un IMEI/SN, el más reciente primero. */
    public List<MovilNsys> porImei(String imeiSn) {
        return jdbc.query("SELECT " + COLUMNAS_MOVIL + " FROM Nsys_movil WHERE IMEI_SN = ?" + LOTE_MAS_RECIENTE_PRIMERO,
                NsysDAO::movil, imeiSn);
    }

    /** Los lotes de estos IMEI/SN, en tandas de {@value #TANDA_IN}: de cada IMEI, el más reciente primero (spec 0.9.18 §6.2). */
    public List<MovilNsys> porImeis(Collection<String> imeis) {
        List<MovilNsys> moviles = new ArrayList<>();
        for (List<String> tanda : trozos(List.copyOf(imeis), TANDA_IN))
            moviles.addAll(jdbc.query("SELECT " + COLUMNAS_MOVIL + " FROM Nsys_movil WHERE IMEI_SN IN (" + marcas(tanda.size())
                    + ")" + LOTE_MAS_RECIENTE_PRIMERO, NsysDAO::movil, tanda.toArray()));
        return moviles;
    }

    static final String COLUMNAS_EVENTO = "IMEI_SN, BATCH, FECHA, SOLO_DIA, TIPO, DE, A, A_NSYS";

    /** De los IMEI/SN con más de un lote, todos sus lotes (0.9.21, corrección en NSYS): para las cadenas del stock, de una
     *  vez y sin leer IMEI a IMEI. */
    public List<MovilNsys> lotesDeImeisConVarios() {
        return jdbc.query("SELECT " + COLUMNAS_MOVIL + " FROM Nsys_movil WHERE IMEI_SN IN"
                + " (SELECT IMEI_SN FROM Nsys_movil GROUP BY IMEI_SN HAVING COUNT(*) > 1)", NsysDAO::movil);
    }

    /** Las bajas con hora (ESTADO → Removed, no del esqueleto), por orden (0.9.21, corrección en NSYS). */
    public List<EventoNsys> bajasConHora() {
        return jdbc.query("SELECT " + COLUMNAS_EVENTO + " FROM Nsys_evento"
                + " WHERE TIPO = 'ESTADO' AND A = 'Removed' AND NOT SOLO_DIA ORDER BY FECHA, ID_EVENTO", NsysDAO::evento);
    }

    public List<EventoNsys> eventos(String imeiSn) {
        return jdbc.query("SELECT " + COLUMNAS_EVENTO + " FROM Nsys_evento WHERE IMEI_SN = ? ORDER BY FECHA, ID_EVENTO",
                NsysDAO::evento, imeiSn);
    }

    static EventoNsys evento(ResultSet rs, int n) throws SQLException {
        return new EventoNsys(rs.getString("IMEI_SN"), rs.getString("BATCH"), rs.getTimestamp("FECHA").toLocalDateTime(),
                rs.getBoolean("SOLO_DIA"), TipoEvento.valueOf(rs.getString("TIPO")), rs.getString("DE"), rs.getString("A"),
                rs.getString("A_NSYS"));
    }

    /** Los trabajos del taller de un IMEI (asignaciones A… y trabajos hechos R/G/P), con el nombre del técnico. Sus horas
     *  pasan de UTC a Madrid para que el rastro no mezcle zonas con los eventos de NSYS. */
    public List<TrabajoTaller> trabajos(String imei) {
        return jdbc.query("SELECT r.ID_REP, r.FECHA_ASIG, r.FECHA_FIN, t.NOMBRE FROM Reparacion r"
                        + " JOIN Tecnico t ON t.ID_TEC = r.ID_TEC WHERE r.IMEI = ? ORDER BY r.FECHA_ASIG, r.ID_REP",
                (rs, n) -> new TrabajoTaller(rs.getString("ID_REP"), utcAMadrid(rs.getTimestamp("FECHA_ASIG")),
                        utcAMadrid(rs.getTimestamp("FECHA_FIN")), rs.getString("NOMBRE")), imei);
    }

    /** Los IMEI/SN del espejo por {@link #claveCruce} (0.9.22 §3.4), uno por IMEI sea cual sea su lote: para cruzarlos en Java con
     *  los del taller (las tablas tienen intercalaciones distintas). Una consulta sin pasar por una lista de filas. */
    public Set<String> clavesDelEspejo() {
        Set<String> claves = new HashSet<>();
        jdbc.query("SELECT DISTINCT IMEI_SN FROM Nsys_movil",
                (RowCallbackHandler) rs -> claves.add(claveCruce(rs.getString("IMEI_SN"))));
        return claves;
    }

    /** Los IMEI con algún trabajo del taller, de cualquier categoría y fecha (0.9.22 §3.4). */
    public List<String> imeisDelTaller() {
        return jdbc.queryForList("SELECT DISTINCT IMEI FROM Reparacion", String.class);
    }

    /** Lo que «No está en NSYS» enseña de estos IMEI ({@link AltaTaller#de}), en tandas de {@value #TANDA_IN}. Horas de UTC a
     *  Madrid. */
    public List<AltaTaller> altas(Collection<String> imeis) {
        List<AltaTaller.Trabajo> trabajos = new ArrayList<>();
        for (List<String> tanda : trozos(List.copyOf(imeis), TANDA_IN))
            trabajos.addAll(jdbc.query(SQL_TRABAJOS_DE_IMEIS + marcas(tanda.size()) + ")" + ORDEN_TRABAJOS,
                    NsysDAO::trabajoDeAlta, tanda.toArray()));
        return AltaTaller.de(trabajos);
    }

    static AltaTaller.Trabajo trabajoDeAlta(ResultSet rs, int n) throws SQLException {
        return new AltaTaller.Trabajo(rs.getString("IMEI"), rs.getString("ID_REP"), utcAMadrid(rs.getTimestamp("FECHA_ASIG")),
                utcAMadrid(rs.getTimestamp("FECHA_FIN")), rs.getString("TECNICO"), rs.getString("MODELO"), rs.getString("CLIENTE"));
    }

    /** Las asignaciones abiertas de todo el taller, por IMEI ({@link #claveCruce}). Son unos cientos como mucho. */
    public Map<String, List<TrabajoAbierto>> trabajosAbiertos() {
        return porImeiDelTaller(jdbc.query(SQL_TRABAJOS_ABIERTOS + ORDEN_TRABAJOS, NsysDAO::filaTrabajoAbierto));
    }

    /** Las asignaciones abiertas de estos IMEI, por IMEI, en tandas de {@value #TANDA_IN}. */
    public Map<String, List<TrabajoAbierto>> trabajosAbiertos(Collection<String> imeis) {
        List<FilaTrabajoAbierto> filas = new ArrayList<>();
        for (List<String> tanda : trozos(List.copyOf(imeis), TANDA_IN))
            filas.addAll(jdbc.query(SQL_TRABAJOS_ABIERTOS + " AND r.IMEI IN (" + marcas(tanda.size()) + ")" + ORDEN_TRABAJOS,
                    NsysDAO::filaTrabajoAbierto, tanda.toArray()));
        return porImeiDelTaller(filas);
    }

    /** Una fila de las asignaciones abiertas: el IMEI del taller y su trabajo. */
    record FilaTrabajoAbierto(String imei, TrabajoAbierto trabajo) {}

    static FilaTrabajoAbierto filaTrabajoAbierto(ResultSet rs, int n) throws SQLException {
        String categoria = Rastro.categoria(rs.getString("ID_REP"));
        LocalDateTime entregado = "GLASS".equals(categoria) ? utcAMadrid(rs.getTimestamp("ENTREGADO_AT")) : null;
        return new FilaTrabajoAbierto(rs.getString("IMEI"), new TrabajoAbierto(categoria, rs.getString("NOMBRE"),
                utcAMadrid(rs.getTimestamp("FECHA_ASIG")), entregado));
    }

    /** Los trabajos por IMEI, en el orden en que llegan (por fecha de asignación). */
    static Map<String, List<TrabajoAbierto>> porImeiDelTaller(List<FilaTrabajoAbierto> filas) {
        Map<String, List<TrabajoAbierto>> mapa = new HashMap<>();
        for (FilaTrabajoAbierto f : filas) mapa.computeIfAbsent(claveCruce(f.imei()), k -> new ArrayList<>()).add(f.trabajo());
        return mapa;
    }

    /** Clave para cruzar un IMEI/SN del espejo con el del taller: sin espacios a los lados y en mayúsculas (un número de
     *  serie puede venir en minúsculas de un lado y en mayúsculas del otro). */
    public static String claveCruce(String imeiSn) {
        return imeiSn == null ? "" : imeiSn.strip().toUpperCase(Locale.ROOT);
    }

    /** Una fila de {@link #SQL_GOODS_POR_COLOR}. */
    record GoodsDeColor(String color, String goods) {}

    /** El «Goods» más usado de cada color (el del maestro) entre los móviles del espejo del mismo modelo, capacidad y eSIM que
     *  {@code m}; un color sin ningún móvil no sale. Sin modelo en el maestro no se pregunta. A igual número, el primero por
     *  orden alfabético. */
    public Map<String, String> goodsMasUsados(MovilNsys m) {
        if (m.modeloMaestro() == null) return Map.of();
        return primeroDeCadaColor(jdbc.query(SQL_GOODS_POR_COLOR, (rs, n) -> new GoodsDeColor(rs.getString("COLOR"),
                rs.getString("GOODS")), m.modelo(), m.modeloMaestro(), m.capacidad(), m.esEsim()));
    }

    /** Los Goods del espejo con su modelo del maestro y cuántos móviles los llevan (0.9.22 §2.1). */
    public List<GrafiasNsys.GoodsContado> goodsContados() {
        return jdbc.query(SQL_GOODS_CONTADOS, (rs, n) -> new GrafiasNsys.GoodsContado(rs.getString("MODELO_MAESTRO"),
                rs.getString("GOODS"), rs.getInt("N")));
    }

    /** Las filas llegan por color y, dentro de cada uno, de la más usada a la menos: vale la primera. */
    static Map<String, String> primeroDeCadaColor(List<GoodsDeColor> filas) {
        Map<String, String> mapa = new LinkedHashMap<>();
        for (GoodsDeColor f : filas) mapa.putIfAbsent(f.color() == null ? null : f.color().strip(), f.goods());
        return mapa;
    }

    // ---------------------------------------------------------------- utilidades

    static MovilNsys movil(ResultSet rs, int n) throws SQLException {
        return new MovilNsys(rs.getString("IMEI_SN"), rs.getString("BATCH"), rs.getString("GOODS"), rs.getString("GRADE"),
                rs.getString("STATUS"), rs.getString("LOCATION"), rs.getString("SUPPLIER"),
                dia(rs.getDate("RECEIVING_DATE")), dia(rs.getDate("SOLD_DATE")), rs.getString("SKU"),
                rs.getString("MODELO"), rs.getString("MODELO_MAESTRO"), rs.getString("CAPACIDAD"), rs.getString("COLOR"),
                rs.getString("GRADO_DETALLE"), rs.getBoolean("ES_ESIM"), rs.getString("UBICACION"),
                rs.getString("UBICACION_ULTIMA"), EstadoMovil.valueOf(rs.getString("ESTADO")),
                fechaHora(rs.getTimestamp("VISTO_PRIMERO")), fechaHora(rs.getTimestamp("CAMBIADO")));
    }

    static <T> List<List<T>> trozos(List<T> lista) {
        return trozos(lista, LOTE);
    }

    static <T> List<List<T>> trozos(List<T> lista, int tamano) {
        List<List<T>> trozos = new ArrayList<>();
        for (int i = 0; i < lista.size(); i += tamano) trozos.add(lista.subList(i, Math.min(lista.size(), i + tamano)));
        return trozos;
    }

    /** «?, ?, ?» para un IN de {@code n} valores. */
    static String marcas(int n) {
        return String.join(", ", Collections.nCopies(n, "?"));
    }

    private static Date fecha(LocalDate dia) {
        return dia == null ? null : Date.valueOf(dia);
    }

    private static LocalDate dia(Date fecha) {
        return fecha == null ? null : fecha.toLocalDate();
    }

    private static LocalDateTime fechaHora(Timestamp ts) {
        return ts == null ? null : ts.toLocalDateTime();
    }

    /** Una hora del taller (UTC sin zona, como guarda la BD con la JVM y la sesión en UTC) en hora de Madrid. */
    static LocalDateTime utcAMadrid(Timestamp ts) {
        return ts == null ? null
                : ts.toLocalDateTime().atOffset(ZoneOffset.UTC).atZoneSameInstant(MADRID).toLocalDateTime();
    }
}
