package com.reparaciones.servidor.nsys;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

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
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
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
            + " CALIDAD_DETALLE, SKU_WEB, SKU_AMAZON, SKU_SIN_GRADO, ES_ESIM) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
    static final String SQL_EMPEZAR = "INSERT INTO Nsys_importacion (FICHERO, FECHA_EXPORTACION, ORIGEN, EMPEZADA)"
            + " VALUES (?, ?, ?, ?)";
    static final String SQL_TERMINAR = "UPDATE Nsys_importacion SET RESULTADO = ?, FILAS = ?, CAMBIOS = ?, MENSAJE = ?,"
            + " TERMINADA = ? WHERE ID_IMPORTACION = ?";
    /** Rellena el modelo de los teléfonos del taller que no lo tienen, sin pisar nunca uno puesto, solo para IMEI de 15 cifras que ya existen en Telefono (spec §7). */
    static final String SQL_RELLENAR_MODELOS = "UPDATE Telefono t JOIN (SELECT IMEI_SN, MAX(MODELO) AS MODELO"
            + " FROM Nsys_movil WHERE MODELO IS NOT NULL GROUP BY IMEI_SN) n ON n.IMEI_SN = t.IMEI"
            + " SET t.MODELO = n.MODELO WHERE (t.MODELO IS NULL OR t.MODELO = '') AND t.IMEI REGEXP '^[0-9]{15}$'";
    /** Los lotes de un IMEI, del más reciente al más antiguo. En la primera carga todos tienen el mismo VISTO_PRIMERO y
     *  el texto del lote no va en orden de fechas: desempata la fecha de recepción (sin fecha, al final) y luego el lote. */
    static final String LOTE_MAS_RECIENTE_PRIMERO = " ORDER BY VISTO_PRIMERO DESC, RECEIVING_DATE DESC, BATCH DESC";
    private static final String COLUMNAS_IMPORTACION ="ID_IMPORTACION, FICHERO, FECHA_EXPORTACION, RESULTADO, FILAS, MENSAJE";

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
                        + " SKU_SIN_GRADO, ES_ESIM FROM Nsys_producto",
                (rs, n) -> new ProductoMaestro(rs.getString("CLAVE"), rs.getString("MODELO"), rs.getString("MODELO_MAESTRO"),
                        rs.getString("CAPACIDAD"), rs.getString("COLOR"), rs.getString("CALIDAD_DETALLE"),
                        rs.getString("SKU_WEB"), rs.getString("SKU_AMAZON"), rs.getString("SKU_SIN_GRADO"),
                        rs.getBoolean("ES_ESIM")));
    }

    @Transactional
    public void reemplazarProductos(List<ProductoMaestro> productos) {
        jdbc.update("DELETE FROM Nsys_producto");
        for (List<ProductoMaestro> lote : trozos(productos))
            jdbc.batchUpdate(SQL_PRODUCTO, lote.stream().map(p -> new Object[]{p.clave(), p.modelo(), p.modeloMaestro(),
                    p.capacidad(), p.color(), p.calidadDetalle(), p.skuWeb(), p.skuAmazon(), p.skuSinGrado(), p.esEsim()}).toList());
    }

    /** Guarda las filas nuevas o cambiadas y los eventos de una comparación, todo o nada. */
    @Transactional
    public void aplicar(ResultadoComparacion r, Integer idImportacion) {
        for (List<MovilNsys> lote : trozos(r.aGuardar()))
            jdbc.batchUpdate(SQL_GUARDAR_MOVIL, lote.stream().map(NsysDAO::argumentos).toList());
        insertarEventos(r.eventos(), idImportacion);
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

    public List<EventoNsys> eventos(String imeiSn) {
        return jdbc.query("SELECT IMEI_SN, BATCH, FECHA, SOLO_DIA, TIPO, DE, A, A_NSYS FROM Nsys_evento WHERE IMEI_SN = ?"
                        + " ORDER BY FECHA, ID_EVENTO",
                (rs, n) -> new EventoNsys(rs.getString("IMEI_SN"), rs.getString("BATCH"),
                        rs.getTimestamp("FECHA").toLocalDateTime(), rs.getBoolean("SOLO_DIA"),
                        TipoEvento.valueOf(rs.getString("TIPO")), rs.getString("DE"), rs.getString("A"),
                        rs.getString("A_NSYS")), imeiSn);
    }

    /** Los trabajos del taller de un IMEI (asignaciones A… y trabajos hechos R/G/P), con el nombre del técnico. Sus horas
     *  pasan de UTC a Madrid para que el rastro no mezcle zonas con los eventos de NSYS. */
    public List<TrabajoTaller> trabajos(String imei) {
        return jdbc.query("SELECT r.ID_REP, r.FECHA_ASIG, r.FECHA_FIN, t.NOMBRE FROM Reparacion r"
                        + " JOIN Tecnico t ON t.ID_TEC = r.ID_TEC WHERE r.IMEI = ? ORDER BY r.FECHA_ASIG, r.ID_REP",
                (rs, n) -> new TrabajoTaller(rs.getString("ID_REP"), utcAMadrid(rs.getTimestamp("FECHA_ASIG")),
                        utcAMadrid(rs.getTimestamp("FECHA_FIN")), rs.getString("NOMBRE")), imei);
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
        List<List<T>> trozos = new ArrayList<>();
        for (int i = 0; i < lista.size(); i += LOTE) trozos.add(lista.subList(i, Math.min(lista.size(), i + LOTE)));
        return trozos;
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
