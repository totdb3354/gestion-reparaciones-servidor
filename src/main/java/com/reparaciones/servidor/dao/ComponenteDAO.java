package com.reparaciones.servidor.dao;

import com.reparaciones.servidor.model.Componente;
import com.reparaciones.servidor.util.CatalogoPiezas;
import com.reparaciones.servidor.util.PrevisionPedido;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Repository
public class ComponenteDAO {

    private final JdbcTemplate jdbc;

    public ComponenteDAO(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Componente> getAllGestionados() {
        String sql = """
                SELECT c.ID_COM, c.TIPO, c.FECHA_REGISTRO,
                       COALESCE(master.STOCK, c.STOCK) AS STOCK,
                       COALESCE(master.STOCK_MINIMO, c.STOCK_MINIMO) AS STOCK_MINIMO,
                       COALESCE(master.AUTO_PEDIDO, c.AUTO_PEDIDO) AS AUTO_PEDIDO,
                       c.ACTIVO, c.UPDATED_AT, c.ID_COM_MASTER,
                       COALESCE(SUM(CASE WHEN cc.ESTADO IN ('en_camino','parcial')
                                         THEN cc.CANTIDAD - COALESCE(cc.CANTIDAD_RECIBIDA, 0)
                                         ELSE 0 END), 0) AS EN_CAMINO,
                       COALESCE(SUM(CASE WHEN cc.ESTADO = 'pendiente' THEN cc.CANTIDAD ELSE 0 END), 0) AS PENDIENTE,
                       MAX(cc.FECHA_PEDIDO) AS ULTIMO_PEDIDO
                FROM Componente c
                LEFT JOIN Componente master ON c.ID_COM_MASTER = master.ID_COM
                LEFT JOIN Compra_componente cc ON COALESCE(c.ID_COM_MASTER, c.ID_COM) = cc.ID_COM
                WHERE c.TIPO NOT LIKE 'otro%'
                GROUP BY c.ID_COM, c.TIPO, c.FECHA_REGISTRO, c.STOCK, c.STOCK_MINIMO, c.ACTIVO, c.UPDATED_AT, c.ID_COM_MASTER
                ORDER BY c.TIPO
                """;
        return jdbc.query(sql, (rs, row) -> {
            Componente c = new Componente(
                    rs.getInt("ID_COM"), rs.getString("TIPO"),
                    rs.getTimestamp("FECHA_REGISTRO").toLocalDateTime(),
                    rs.getInt("STOCK"), rs.getInt("STOCK_MINIMO"),
                    rs.getBoolean("ACTIVO"), rs.getTimestamp("UPDATED_AT").toLocalDateTime());
            int masterId = rs.getInt("ID_COM_MASTER");
            if (!rs.wasNull()) c.setIdComMaster(masterId);
            c.setEnCamino(rs.getInt("EN_CAMINO"));
            c.setPendiente(rs.getInt("PENDIENTE"));
            c.setAutoPedido(rs.getBoolean("AUTO_PEDIDO"));
            Timestamp up = rs.getTimestamp("ULTIMO_PEDIDO");
            c.setUltimoPedido(up != null ? up.toLocalDateTime() : null);
            return c;
        });
    }

    /** Unidades consumidas por master y día en [desde, hasta) (spec 0.9.5 §3.2): solo las piezas de las reparaciones
     *  resultantes (R…, G…), que son las que restan stock; las solicitudes cuelgan de la asignación (A…, AG…) y una
     *  solicitud ya servida tiene las mismas marcas que una fila de consumo. Sin reutilizadas ni tipos "otro". */
    public List<PrevisionPedido.ConsumoDia> getConsumoDiario(LocalDate desde, LocalDate hasta) {
        String sql = """
                SELECT COALESCE(c.ID_COM_MASTER, c.ID_COM) AS ID_MASTER, DATE(r.FECHA_FIN) AS DIA,
                       SUM(rc.CANTIDAD) AS UNIDADES
                FROM Reparacion_componente rc
                JOIN Reparacion r ON rc.ID_REP = r.ID_REP
                JOIN Componente c ON rc.ID_COM = c.ID_COM
                WHERE (r.ID_REP LIKE 'R%' OR r.ID_REP LIKE 'G%')
                  AND rc.ES_REUTILIZADO = 0
                  AND c.TIPO NOT LIKE 'otro%'
                  AND r.FECHA_FIN >= ? AND r.FECHA_FIN < ?
                GROUP BY ID_MASTER, DIA
                """;
        return jdbc.query(sql, (rs, row) -> new PrevisionPedido.ConsumoDia(
                        rs.getInt("ID_MASTER"), rs.getDate("DIA").toLocalDate(), rs.getInt("UNIDADES")),
                Timestamp.valueOf(desde.atStartOfDay()), Timestamp.valueOf(hasta.atStartOfDay()));
    }

    public Map<String, List<Componente>> getAgrupadosPorTipo() {
        List<Componente> todos = jdbc.query(
                "SELECT c.ID_COM, c.TIPO, c.FECHA_REGISTRO," +
                " COALESCE(master.STOCK, c.STOCK) AS STOCK," +
                " COALESCE(master.STOCK_MINIMO, c.STOCK_MINIMO) AS STOCK_MINIMO," +
                " c.ACTIVO, c.UPDATED_AT, c.ID_COM_MASTER" +
                " FROM Componente c" +
                " LEFT JOIN Componente master ON c.ID_COM_MASTER = master.ID_COM" +
                " ORDER BY c.TIPO",
                (rs, row) -> {
                    Componente c = new Componente(
                            rs.getInt("ID_COM"), rs.getString("TIPO"),
                            rs.getTimestamp("FECHA_REGISTRO").toLocalDateTime(),
                            rs.getInt("STOCK"), rs.getInt("STOCK_MINIMO"),
                            rs.getBoolean("ACTIVO"), rs.getTimestamp("UPDATED_AT").toLocalDateTime());
                    int masterId = rs.getInt("ID_COM_MASTER");
                    if (!rs.wasNull()) c.setIdComMaster(masterId);
                    return c;
                });
        Map<String, List<Componente>> agrupados = new LinkedHashMap<>();
        for (Componente c : todos) {
            String prefijo = CatalogoPiezas.prefijo(c.getTipo());
            agrupados.computeIfAbsent(prefijo, k -> new ArrayList<>()).add(c);
        }
        Map<String, List<Componente>> ordenado = new LinkedHashMap<>();
        for (String prefijo : CatalogoPiezas.ORDEN_TIPOS)
            if (agrupados.containsKey(prefijo)) ordenado.put(prefijo, agrupados.get(prefijo));
        agrupados.forEach((k, v) -> ordenado.putIfAbsent(k, v));
        return ordenado;
    }

    /** Editar stock. El mínimo no se toca aquí: solo lo cambia el ADMIN con setStockMinimo (spec 0.9.5 §4.2). */
    public void actualizar(int idCom, String tipo, int stock, LocalDateTime updatedAt) {
        int masterIdCom = resolveToMasterId(idCom);
        if (masterIdCom != idCom) {
            // Slave: STOCK en el master; TIPO en el propio slave
            jdbc.update("UPDATE Componente SET STOCK = ? WHERE ID_COM = ?", stock, masterIdCom);
            jdbc.update("UPDATE Componente SET TIPO = ? WHERE ID_COM = ?", tipo, idCom);
        } else {
            int filas = jdbc.update(
                    "UPDATE Componente SET TIPO = ?, STOCK = ? WHERE ID_COM = ? AND UPDATED_AT = ?",
                    tipo, stock, idCom,
                    Timestamp.valueOf(updatedAt.truncatedTo(ChronoUnit.SECONDS)));
            if (filas == 0) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Dato modificado por otro usuario");
            }
        }
    }

    /** El mínimo vive en el master del grupo compartido: es el que enseña Stock y el que usa la previsión. */
    public void setStockMinimo(int idCom, int stockMinimo) {
        jdbc.update("UPDATE Componente SET STOCK_MINIMO = ? WHERE ID_COM = ?", stockMinimo, resolveToMasterId(idCom));
    }

    /** Marca del pedido automático (spec 0.9.6 §4.2), en el master del grupo. No toca UPDATED_AT: "Editar stock" lo usa
     *  para detectar ediciones simultáneas y marcar no es editar. Devuelve el id del master; un id inexistente lanza
     *  EmptyResultDataAccessException (resolveToMasterId). */
    public int setAutoPedido(int idCom, boolean autoPedido) {
        int master = resolveToMasterId(idCom);
        jdbc.update("UPDATE Componente SET AUTO_PEDIDO = ?, UPDATED_AT = UPDATED_AT WHERE ID_COM = ?", autoPedido, master);
        return master;
    }

    public void setActivo(int idCom, boolean activo) {
        // Activar/desactivar afecta a todo el grupo compartido: master + todos sus slaves
        int masterId = resolveToMasterId(idCom);
        jdbc.update("UPDATE Componente SET ACTIVO = ? WHERE ID_COM = ? OR ID_COM_MASTER = ?",
                activo, masterId, masterId);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    int resolveToMasterId(int idCom) {
        Integer master = jdbc.queryForObject(
                "SELECT COALESCE(ID_COM_MASTER, ID_COM) FROM Componente WHERE ID_COM = ?",
                Integer.class, idCom);
        return master != null ? master : idCom;
    }

    /** Lo mínimo para validar una línea de pedido (sub-proyecto 4b): si existe, si está activo y su master.
     *  El ACTIVO de un slave es el de su grupo (setActivo cambia master y slaves a la vez). */
    public record Basico(int idCom, int idMaster, String tipo, boolean activo) {}

    public Optional<Basico> getBasico(int idCom) {
        return jdbc.query(
                "SELECT ID_COM, COALESCE(ID_COM_MASTER, ID_COM) AS ID_MASTER, TIPO, ACTIVO FROM Componente WHERE ID_COM = ?",
                (rs, row) -> new Basico(rs.getInt("ID_COM"), rs.getInt("ID_MASTER"), rs.getString("TIPO"),
                        rs.getBoolean("ACTIVO")),
                idCom).stream().findFirst();
    }

    public String getTipoById(int idCom) {
        return jdbc.queryForObject(
                "SELECT TIPO FROM Componente WHERE ID_COM = ?", String.class, idCom);
    }

    public int getStockById(int idCom) {
        return jdbc.queryForObject(
                "SELECT STOCK FROM Componente WHERE ID_COM = ?", Integer.class, idCom);
    }
}
