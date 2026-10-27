package com.reparaciones.servidor.nsys;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Acceso a Taller_cambio (spec 0.9.21 §4): lo del taller por encima de NSYS. Las horas son de Madrid, como las del espejo:
 *  las pone el servidor, nunca la base de datos. */
@Repository
public class TallerDAO {

    static final String COLUMNAS = "c.ID_CAMBIO, c.IMEI_SN, c.DATO, c.VALOR,"
            + " CASE WHEN a.ID_CAMBIO IS NULL THEN c.VALOR_NSYS ELSE a.VALOR END AS VALOR_ANTES, c.VALOR_NSYS, c.ORIGEN,"
            + " c.ID_REP, c.ID_USU, u.NOMBRE_USUARIO, c.MOTIVO, c.FECHA, c.ESTADO, c.CERRADO_EN, c.VALOR_CIERRE,"
            + " c.ID_CAMBIO_ANTERIOR";
    static final String DESDE = " FROM Taller_cambio c LEFT JOIN Taller_cambio a ON a.ID_CAMBIO = c.ID_CAMBIO_ANTERIOR"
            + " LEFT JOIN Usuario u ON u.ID_USU = c.ID_USU";
    static final String ORDEN = " ORDER BY c.FECHA, c.ID_CAMBIO";
    /** El candado del móvil: con dos correcciones a la vez del mismo IMEI, la segunda espera a la primera y la última gana
     *  sin dejar dos VIGENTE del mismo dato (spec §3). */
    static final String SQL_BLOQUEAR = "SELECT IMEI_SN FROM Nsys_movil WHERE IMEI_SN = ? FOR UPDATE";
    static final String SQL_VIGENTE = "SELECT ID_CAMBIO FROM Taller_cambio WHERE IMEI_SN = ? AND DATO = ? AND ESTADO = 'VIGENTE'";
    static final String SQL_SUSTITUIR = "UPDATE Taller_cambio SET ESTADO = 'SUSTITUIDO', CERRADO_EN = ?"
            + " WHERE ID_CAMBIO = ? AND ESTADO = 'VIGENTE'";
    static final String SQL_INSERTAR = "INSERT INTO Taller_cambio (IMEI_SN, DATO, VALOR, VALOR_NSYS, ORIGEN, ID_REP, ID_USU,"
            + " MOTIVO, FECHA, ESTADO, CERRADO_EN, ID_CAMBIO_ANTERIOR) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
    static final String SQL_CERRAR = "UPDATE Taller_cambio SET ESTADO = ?, CERRADO_EN = ?, VALOR_CIERRE = ?"
            + " WHERE ID_CAMBIO = ? AND ESTADO = 'VIGENTE'";
    static final String SQL_REACTIVAR = "UPDATE Taller_cambio SET ESTADO = 'VIGENTE', CERRADO_EN = NULL"
            + " WHERE ID_CAMBIO = ? AND ESTADO = 'SUSTITUIDO'";
    static final String SQL_AVISAR = "INSERT INTO Taller_aviso (IMEI_SN, ID_REP, TIPO, CAUSA, DATO, DE, A, MOTIVO, ID_USU,"
            + " FECHA) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
    /** Las columnas de un aviso, con el nombre de quien lo guardó y, desde la 0.9.22, el de quien lo dio por revisado. */
    static final String COLUMNAS_AVISO = "v.ID_AVISO, v.IMEI_SN, v.ID_REP, v.TIPO, v.CAUSA, v.DATO, v.DE, v.A, v.MOTIVO,"
            + " v.ID_USU, u.NOMBRE_USUARIO, v.FECHA, v.REVISADO_EN, r.NOMBRE_USUARIO AS REVISOR";
    static final String DESDE_AVISO = " FROM Taller_aviso v LEFT JOIN Usuario u ON u.ID_USU = v.ID_USU"
            + " LEFT JOIN Usuario r ON r.ID_USU = v.REVISADO_POR";
    static final String SQL_AVISOS = "SELECT " + COLUMNAS_AVISO + DESDE_AVISO + " WHERE v.IMEI_SN = ? ORDER BY v.FECHA, v.ID_AVISO";
    /** «Revisar a mano» (spec 0.9.22 §3.5): los SIN_COLOR y SIN_DESHACER (pieza 4: SIN_GRADO ya no) sin «Revisado» y sin una corrección a mano del
     *  mismo IMEI y dato después del aviso («Corregir…» o «Volver a lo de NSYS»: los dos dejan un cambio de origen MANO). */
    static final String SQL_POR_REVISAR = "SELECT " + COLUMNAS_AVISO + DESDE_AVISO
            + " WHERE v.TIPO IN ('SIN_COLOR', 'SIN_DESHACER') AND v.REVISADO_EN IS NULL"
            + " AND NOT EXISTS (SELECT 1 FROM Taller_cambio c WHERE c.IMEI_SN = v.IMEI_SN AND c.DATO = v.DATO"
            + " AND c.ORIGEN = 'MANO' AND c.FECHA > v.FECHA) ORDER BY v.FECHA, v.ID_AVISO";
    static final String SQL_AVISO = "SELECT " + COLUMNAS_AVISO + DESDE_AVISO + " WHERE v.ID_AVISO = ?";
    /** «Revisado»: solo una vez y solo los de «Revisar a mano». */
    static final String SQL_REVISAR = "UPDATE Taller_aviso SET REVISADO_POR = ?, REVISADO_EN = ? WHERE ID_AVISO = ?"
            + " AND REVISADO_EN IS NULL AND TIPO IN ('SIN_COLOR', 'SIN_DESHACER')";
    /** La fila con uso real de una reparación (una R… tiene una). */
    static final String SQL_PIEZA = "SELECT r.IMEI, rc.ID_COM, rc.ES_REUTILIZADO FROM Reparacion r"
            + " JOIN Reparacion_componente rc ON rc.ID_REP = r.ID_REP AND rc.ES_SOLICITUD = 0 WHERE r.ID_REP = ?";
    /** 0.9.22 pieza 4 (§5.1): los avisos «Grado sin calcular» sin revisar, candidatos a batería recordada. */
    static final String SQL_BATERIAS = "SELECT v.ID_AVISO, v.IMEI_SN, v.ID_REP, v.DE, v.MOTIVO, v.ID_USU, v.FECHA FROM Taller_aviso v"
            + " WHERE v.TIPO = 'SIN_GRADO' AND v.REVISADO_EN IS NULL";
    static final String ORDEN_AVISOS = " ORDER BY v.FECHA, v.ID_AVISO";
    /** La pieza con uso real de unas reparaciones (Reparacion_componente y Componente: tablas viejas, la misma intercalación;
     *  Taller_aviso no se junta con ellas). Se completa con las marcas del IN. */
    static final String SQL_PIEZAS = "SELECT rc.ID_REP, rc.ES_REUTILIZADO, c.TIPO FROM Reparacion_componente rc"
            + " JOIN Componente c ON c.ID_COM = rc.ID_COM WHERE rc.ES_SOLICITUD = 0 AND rc.ID_REP IN (";
    /** Dar por usadas unas baterías recordadas (§5.3): revisadas por el ERP (sin quién) con el grado que quedó. Se completa con
     *  las marcas del IN. */
    static final String SQL_USAR = "UPDATE Taller_aviso SET REVISADO_EN = ?, A = ? WHERE TIPO = 'SIN_GRADO' AND REVISADO_EN IS NULL"
            + " AND ID_AVISO IN (";

    /** Deshacer un cambio de grado (D1): las baterias que se usaron con el (mismo instante que su FECHA) vuelven a pendientes.
     *  Solo las que uso el ERP (sin quien, con grado): un «Revisado» a mano no se toca. */
    static final String SQL_REABRIR = "UPDATE Taller_aviso SET REVISADO_EN = NULL, A = NULL WHERE IMEI_SN = ? AND TIPO = 'SIN_GRADO'"
            + " AND REVISADO_POR IS NULL AND A IS NOT NULL AND REVISADO_EN = ?";

    private final JdbcTemplate jdbc;

    public TallerDAO(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Los cambios VIGENTES de todo el taller, del más antiguo al más reciente. Son unas decenas. */
    public List<CambioTaller> vigentes() {
        return jdbc.query("SELECT " + COLUMNAS + DESDE + " WHERE c.ESTADO = 'VIGENTE'" + ORDEN, TallerDAO::cambio);
    }

    /** Los VIGENTES de estos IMEI, en tandas de {@value NsysDAO#TANDA_IN}, del más antiguo al más reciente en cada tanda. */
    public List<CambioTaller> vigentes(Collection<String> imeis) {
        List<CambioTaller> cambios = new ArrayList<>();
        for (List<String> tanda : NsysDAO.trozos(List.copyOf(imeis), NsysDAO.TANDA_IN))
            cambios.addAll(jdbc.query("SELECT " + COLUMNAS + DESDE + " WHERE c.ESTADO = 'VIGENTE' AND c.IMEI_SN IN ("
                    + NsysDAO.marcas(tanda.size()) + ")" + ORDEN, TallerDAO::cambio, tanda.toArray()));
        return cambios;
    }

    /** Todos los cambios de un IMEI, de cualquier estado, para el rastro. */
    public List<CambioTaller> deImei(String imeiSn) {
        return jdbc.query("SELECT " + COLUMNAS + DESDE + " WHERE c.IMEI_SN = ?" + ORDEN, TallerDAO::cambio, imeiSn);
    }

    /** Un cambio que entra: VIGENTE, o QUITADO si vuelve a lo de NSYS. */
    public record Nuevo(DatoTaller dato, String valor, String valorNsys, CambioTaller.Estado estado) {}

    /** Guarda unos cambios a mano de un móvil, todo o nada (ver el de abajo). */
    @Transactional
    public void guardar(String imeiSn, List<Nuevo> nuevos, int idUsu, String motivo, LocalDateTime ahora) {
        guardar(imeiSn, nuevos, null, idUsu, motivo, ahora);
    }

    /** Guarda unos cambios de un móvil, todo o nada: el VIGENTE anterior de cada dato, si lo hay, pasa a SUSTITUIDO y el
     *  nuevo apunta a él (ID_CAMBIO_ANTERIOR). Un QUITADO nace cerrado. {@code idRep}: la reparación que los hace (ORIGEN
     *  REPARACION, pieza 2) o null (a mano). {@code idUsu}: null si quien guardó ya no existe
     *  (pieza 4: la batería recordada). */
    @Transactional
    public void guardar(String imeiSn, List<Nuevo> nuevos, String idRep, Integer idUsu, String motivo, LocalDateTime ahora) {
        jdbc.queryForList(SQL_BLOQUEAR, String.class, imeiSn);
        Timestamp t = Timestamp.valueOf(ahora);
        String origen = idRep == null ? CambioTaller.MANO : CambioTaller.REPARACION;
        for (Nuevo n : nuevos) {
            List<Integer> vigentes = jdbc.queryForList(SQL_VIGENTE, Integer.class, imeiSn, n.dato().name());
            for (Integer id : vigentes) jdbc.update(SQL_SUSTITUIR, t, id);
            Integer anterior = vigentes.stream().max(Integer::compare).orElse(null);
            boolean cerrado = n.estado() != CambioTaller.Estado.VIGENTE;
            jdbc.update(SQL_INSERTAR, imeiSn, n.dato().name(), n.valor(), n.valorNsys(), origen, idRep, idUsu, motivo, t,
                    n.estado().name(), cerrado ? t : null, anterior);
        }
    }

    /** El cierre automático de un VIGENTE (PASADO o MANDA_NSYS). 0 si ya no lo era: lo sustituyó una corrección a la vez. */
    public int cerrar(int idCambio, CambioTaller.Estado estado, String valorCierre, LocalDateTime ahora) {
        return jdbc.update(SQL_CERRAR, estado.name(), Timestamp.valueOf(ahora), valorCierre, idCambio);
    }

    // ---------------------------------------------------------------- 0.9.21, pieza 2: lo automático desde las reparaciones

    /** La pieza que tiene ahora una reparación. */
    public record PiezaDeReparacion(String idRep, String imei, int idCom, boolean reutilizada) {}

    /** El candado del móvil ({@link #SQL_BLOQUEAR}) pedido antes de leer lo que vale: dos reparaciones del mismo móvil a la
     *  vez se aplican una detrás de otra, cada una sobre lo que dejó la anterior (spec pieza 2 §3). false si NSYS no conoce
     *  el IMEI. */
    public boolean bloquear(String imeiSn) {
        return !jdbc.queryForList(SQL_BLOQUEAR, String.class, imeiSn).isEmpty();
    }

    /** Los cambios de una reparación, de cualquier estado, del más antiguo al más reciente. */
    public List<CambioTaller> deReparacion(String idRep) {
        return jdbc.query("SELECT " + COLUMNAS + DESDE + " WHERE c.ID_REP = ?" + ORDEN, TallerDAO::cambio, idRep);
    }

    /** Deshace el VIGENTE de una reparación borrada o editada: pasa a SUSTITUIDO (lo tapa lo que vuelve). 0 si ya no lo era. */
    public int deshacer(int idCambio, LocalDateTime ahora) {
        return jdbc.update(SQL_SUSTITUIR, Timestamp.valueOf(ahora), idCambio);
    }

    /** El cambio al que sustituía uno deshecho vuelve a VIGENTE. 0 si ya no era SUSTITUIDO. */
    public int reactivar(int idCambio) {
        return jdbc.update(SQL_REACTIVAR, idCambio);
    }

    public void avisar(AvisoTaller.Nuevo a, int idUsu, LocalDateTime ahora) {
        jdbc.update(SQL_AVISAR, a.imeiSn(), a.idRep(), a.tipo().name(), a.causa() == null ? null : a.causa().name(),
                a.dato().name(), a.de(), a.a(), a.motivo(), idUsu, Timestamp.valueOf(ahora));
    }

    /** Los avisos del taller de un IMEI, para el rastro, del más antiguo al más reciente. */
    public List<AvisoTaller> avisosDeImei(String imeiSn) {
        return jdbc.query(SQL_AVISOS, TallerDAO::aviso, imeiSn);
    }

    /** «Revisar a mano» (spec 0.9.22 §3.5), del más antiguo al más reciente. Son unos pocos. */
    public List<AvisoTaller> avisosPorRevisar() {
        return jdbc.query(SQL_POR_REVISAR, TallerDAO::aviso);
    }

    public Optional<AvisoTaller> avisoPorId(int idAviso) {
        return jdbc.query(SQL_AVISO, TallerDAO::aviso, idAviso).stream().findFirst();
    }

    /** «Revisado» (spec 0.9.22 §3.5): quién y cuándo. 0 si el aviso no existe, ya estaba revisado o no es de «Revisar a mano». */
    public int revisar(int idAviso, int idUsu, LocalDateTime ahora) {
        return jdbc.update(SQL_REVISAR, idUsu, Timestamp.valueOf(ahora), idAviso);
    }

    /** El SKU (Componente.TIPO) de cada componente, en una consulta; uno que no existe no sale. */
    public Map<Integer, String> tiposDeComponentes(Collection<Integer> idComs) {
        List<Integer> ids = List.copyOf(new LinkedHashSet<>(idComs));
        if (ids.isEmpty()) return Map.of();
        Map<Integer, String> tipos = new HashMap<>();
        for (Map.Entry<Integer, String> e : jdbc.query("SELECT ID_COM, TIPO FROM Componente WHERE ID_COM IN ("
                + NsysDAO.marcas(ids.size()) + ")", (rs, n) -> Map.entry(rs.getInt("ID_COM"), rs.getString("TIPO")), ids.toArray()))
            tipos.put(e.getKey(), e.getValue());
        return tipos;
    }

    public Optional<PiezaDeReparacion> piezaDeReparacion(String idRep) {
        return jdbc.query(SQL_PIEZA, (rs, n) -> new PiezaDeReparacion(idRep, rs.getString("IMEI"), rs.getInt("ID_COM"),
                rs.getBoolean("ES_REUTILIZADO")), idRep).stream().findFirst();
    }

    /** De estas reparaciones, las que siguen existiendo. */
    public Set<String> reparacionesQueExisten(Collection<String> idReps) {
        List<String> ids = List.copyOf(new LinkedHashSet<>(idReps));
        if (ids.isEmpty()) return Set.of();
        return new HashSet<>(jdbc.queryForList("SELECT ID_REP FROM Reparacion WHERE ID_REP IN (" + NsysDAO.marcas(ids.size())
                + ")", String.class, ids.toArray()));
    }

    // ---------------------------------------------------------------- 0.9.22 pieza 4: baterías recordadas (spec §5)

    /** Un aviso «Grado sin calcular» sin revisar, antes de mirar su pieza. */
    record Candidata(int idAviso, String imeiSn, String idRep, String de, String motivo, Integer idUsu, LocalDateTime fecha) {}

    /** La pieza con uso real de una reparación: su SKU (Componente.TIPO) y si es reutilizada. */
    record PiezaLeida(String sku, boolean reutilizada) {}

    /** Las baterías recordadas de todo el taller (tras cada importación), de la más antigua a la más reciente. */
    public List<BateriaRecordada> bateriasRecordadas() {
        return baterias(jdbc.query(SQL_BATERIAS + ORDEN_AVISOS, TallerDAO::candidata));
    }

    /** Las de estos IMEI (como Taller_aviso.IMEI_SN, que es el del espejo), en tandas de {@value NsysDAO#TANDA_IN}. */
    public List<BateriaRecordada> bateriasRecordadas(Collection<String> imeis) {
        List<String> lista = List.copyOf(new LinkedHashSet<>(imeis));
        if (lista.isEmpty()) return List.of();
        List<Candidata> candidatas = new ArrayList<>();
        for (List<String> tanda : NsysDAO.trozos(lista, NsysDAO.TANDA_IN))
            candidatas.addAll(jdbc.query(SQL_BATERIAS + " AND v.IMEI_SN IN (" + NsysDAO.marcas(tanda.size()) + ")" + ORDEN_AVISOS,
                    TallerDAO::candidata, tanda.toArray()));
        return baterias(candidatas);
    }

    /** Lee las piezas de las candidatas «como n/a» (solo si hay alguna) y se queda con las baterías. */
    private List<BateriaRecordada> baterias(List<Candidata> candidatas) {
        List<String> idReps = candidatas.stream().filter(c -> GradoTaller.grupo(c.de()) == GradoTaller.Grupo.COMO_NA)
                .map(Candidata::idRep).distinct().toList();
        if (idReps.isEmpty()) return List.of();
        Map<String, PiezaLeida> piezas = new HashMap<>();
        for (List<String> tanda : NsysDAO.trozos(idReps, NsysDAO.TANDA_IN))
            for (Map.Entry<String, PiezaLeida> e : jdbc.query(SQL_PIEZAS + NsysDAO.marcas(tanda.size()) + ")",
                    (rs, n) -> Map.entry(rs.getString("ID_REP"), new PiezaLeida(rs.getString("TIPO"), rs.getBoolean("ES_REUTILIZADO"))),
                    tanda.toArray()))
                piezas.put(e.getKey(), e.getValue());
        return baterias(candidatas, piezas);
    }

    /** De las candidatas, las que son una batería que sigue existiendo (su R… tiene pieza) puesta en un grado «como n/a», en su
     *  orden. Nueva = la pieza de hoy no es reutilizada. Es puro. */
    static List<BateriaRecordada> baterias(List<Candidata> candidatas, Map<String, PiezaLeida> piezas) {
        List<BateriaRecordada> baterias = new ArrayList<>();
        for (Candidata c : candidatas) {
            PiezaLeida p = piezas.get(c.idRep());
            if (p == null || GradoTaller.grupo(c.de()) != GradoTaller.Grupo.COMO_NA) continue;
            if (PiezaTaller.Tipo.deSku(p.sku()).orElse(null) != PiezaTaller.Tipo.BATERIA) continue;
            baterias.add(new BateriaRecordada(c.idAviso(), c.imeiSn(), c.idRep(), !p.reutilizada(), c.motivo(), c.idUsu(), c.fecha()));
        }
        return baterias;
    }

    /** Da por usadas estas baterías recordadas con el grado que quedó (§5.3). 0 si ya lo estaban o no hay ninguna. */
    public int usarBaterias(Collection<Integer> idAvisos, String gradoQueQueda, LocalDateTime ahora) {
        List<Integer> ids = List.copyOf(new LinkedHashSet<>(idAvisos));
        if (ids.isEmpty()) return 0;
        Object[] args = new Object[ids.size() + 2];
        args[0] = Timestamp.valueOf(ahora);
        args[1] = gradoQueQueda;
        for (int i = 0; i < ids.size(); i++) args[i + 2] = ids.get(i);
        return jdbc.update(SQL_USAR + NsysDAO.marcas(ids.size()) + ")", args);
    }

    /** Vuelve a dejar pendientes las baterias que se dieron por usadas en {@code usadasEn} (la fecha del cambio de grado que se
     *  deshace). 0 si no hay ninguna. */
    public int reabrirBaterias(String imeiSn, LocalDateTime usadasEn) {
        return jdbc.update(SQL_REABRIR, imeiSn, Timestamp.valueOf(usadasEn));
    }

    static Candidata candidata(ResultSet rs, int n) throws SQLException {
        return new Candidata(rs.getInt("ID_AVISO"), rs.getString("IMEI_SN"), rs.getString("ID_REP"), rs.getString("DE"),
                rs.getString("MOTIVO"), entero(rs, "ID_USU"), rs.getTimestamp("FECHA").toLocalDateTime());
    }

    static AvisoTaller aviso(ResultSet rs, int n) throws SQLException {
        String causa = rs.getString("CAUSA");
        return new AvisoTaller(rs.getInt("ID_AVISO"), rs.getString("IMEI_SN"), rs.getString("ID_REP"),
                AvisoTaller.Tipo.valueOf(rs.getString("TIPO")), causa == null ? null : AvisoTaller.Causa.valueOf(causa),
                DatoTaller.valueOf(rs.getString("DATO")), rs.getString("DE"), rs.getString("A"), rs.getString("MOTIVO"),
                entero(rs, "ID_USU"), rs.getString("NOMBRE_USUARIO"), rs.getTimestamp("FECHA").toLocalDateTime(),
                rs.getString("REVISOR"), fechaHora(rs.getTimestamp("REVISADO_EN")));
    }

    static CambioTaller cambio(ResultSet rs, int n) throws SQLException {
        return new CambioTaller(rs.getInt("ID_CAMBIO"), rs.getString("IMEI_SN"), DatoTaller.valueOf(rs.getString("DATO")),
                rs.getString("VALOR"), rs.getString("VALOR_ANTES"), rs.getString("VALOR_NSYS"), rs.getString("ORIGEN"),
                rs.getString("ID_REP"), entero(rs, "ID_USU"), rs.getString("NOMBRE_USUARIO"), rs.getString("MOTIVO"),
                rs.getTimestamp("FECHA").toLocalDateTime(), CambioTaller.Estado.valueOf(rs.getString("ESTADO")),
                fechaHora(rs.getTimestamp("CERRADO_EN")), rs.getString("VALOR_CIERRE"), entero(rs, "ID_CAMBIO_ANTERIOR"));
    }

    private static Integer entero(ResultSet rs, String columna) throws SQLException {
        int valor = rs.getInt(columna);
        return rs.wasNull() ? null : valor;
    }

    private static LocalDateTime fechaHora(Timestamp ts) {
        return ts == null ? null : ts.toLocalDateTime();
    }
}
